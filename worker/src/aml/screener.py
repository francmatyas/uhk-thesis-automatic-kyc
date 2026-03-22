"""
AML vyhledávač - párování proti PEP a sankčním seznamům přes SQLite FTS5.

Vyžaduje `data/aml.db`, vytvořenou příkazem:
    python -m src/aml/build_db
"""

from __future__ import annotations

import logging
import re
import sqlite3
import unicodedata
from dataclasses import dataclass
from pathlib import Path
from typing import Optional

from rapidfuzz import fuzz

from ..errors import WorkerError
# Dokumentace RapidFuzz (token_sort_ratio):
# https://rapidfuzz.github.io/RapidFuzz/Usage/fuzz.html

logger = logging.getLogger(__name__)

# ---------------------------------------------------------------------------
# Nastavitelné konstanty
# ---------------------------------------------------------------------------

DEFAULT_THRESHOLD = 80      # minimální fuzzy skóre (0-100) pro nahlášení shody
DOB_BONUS = 5               # navýšení skóre při shodě číslic data narození
MIN_TOKEN_LEN = 2           # ignorovat velmi krátké tokeny (členy apod.)

_NAMES_SEP = "\x1f"         # musí odpovídat oddělovači použitému v src.aml.build_db.py

# ---------------------------------------------------------------------------
# Pomocné funkce (stejná normalizace jako v src.aml.build_db.py)
# ---------------------------------------------------------------------------

_PUNCT_RE = re.compile(r"[^\w\s]", re.UNICODE)


def _normalize(text: str) -> str:
    """NFKD -> odstranit kombinující znaky -> lowercase -> odstranit interpunkci -> sloučit mezery."""
    nfkd = unicodedata.normalize("NFKD", text)
    ascii_approx = "".join(c for c in nfkd if not unicodedata.combining(c))
    no_punct = _PUNCT_RE.sub(" ", ascii_approx.lower())
    return " ".join(no_punct.split())


def _tokens(normalized: str) -> list[str]:
    return [t for t in normalized.split() if len(t) >= MIN_TOKEN_LEN]


def _fts_escape(token: str) -> str:
    """Zabalí token do FTS5 dvojitých uvozovek a escapuje vnitřní uvozovky."""
    return '"' + token.replace('"', '""') + '"'


def _match_expr(tokens: list[str]) -> str:
    """Sestaví FTS5 MATCH výraz, který odpovídá řádkům obsahujícím KTERÝKOLI token."""
    return " OR ".join(_fts_escape(t) for t in tokens)


# ---------------------------------------------------------------------------
# Typ výsledku
# ---------------------------------------------------------------------------

@dataclass
class AmlHit:
    list_type: str          # "PEP" nebo "SANCTION"
    entity_id: str
    schema: str             # "Person" | "Company" | …
    name: str               # primární jméno (původní zápis)
    matched_alias: str      # alias s nejvyšším skóre
    score: int              # 0-100 (může obsahovat bonus za datum narození)
    birth_date: str
    countries: str
    dataset: str
    program_ids: str


# ---------------------------------------------------------------------------
# Screener
# ---------------------------------------------------------------------------

_SELECT = """
    SELECT entity_id, list_type, schema, birth_date, countries,
           dataset, program_ids, primary_name, all_names
    FROM   aml_fts
    WHERE  search_text MATCH ?
"""
# Dotazovací jazyk SQLite FTS5 MATCH:
# https://www.sqlite.org/fts5.html


class AmlScreener:
    """
    Obálka jen pro čtení nad FTS5 databází vytvořenou skriptem src/aml/build_db.py.

    SQLite připojení se otevírá líně při prvním volání connect() (nebo scan()).
    Používá check_same_thread=False, protože asyncio workery běží jednovláknově;
    pokud by scan() běžel z více OS vláken, musí být vlání obaleno v threading.Lock.
    """

    def __init__(self, db_path: str | Path) -> None:
        self._db_path = Path(db_path)
        self._con: Optional[sqlite3.Connection] = None

    # ------------------------------------------------------------------
    # Lifecycle
    # ------------------------------------------------------------------

    def connect(self) -> None:
        """Otevře SQLite připojení. Je bezpečné volat opakovaně."""
        if self._con is not None:
            return
        if not self._db_path.exists():
            raise WorkerError("AML_DB_NOT_FOUND")
        self._con = sqlite3.connect(
            str(self._db_path),
            check_same_thread=False,
            uri=False,
        )
        # Nastavení pro read-only
        self._con.execute("PRAGMA query_only = ON")
        self._con.execute("PRAGMA cache_size = -32768")   # 32 MB page cache
        self._con.row_factory = sqlite3.Row
        logger.info("AmlScreener connected to %s", self._db_path)

    def close(self) -> None:
        if self._con:
            self._con.close()
            self._con = None

    def __enter__(self) -> "AmlScreener":
        self.connect()
        return self

    def __exit__(self, *_: object) -> None:
        self.close()

    # ------------------------------------------------------------------
    # Skenování
    # ------------------------------------------------------------------

    def scan(
        self,
        full_name: str,
        dob: Optional[str] = None,
        *,
        threshold: int = DEFAULT_THRESHOLD,
        max_hits: int = 20,
    ) -> list[AmlHit]:
        """
        Prověří *full_name* proti PEP a sankčním seznamům.

        Parametry
        ----------
        full_name   : Jméno extrahované z identifikačního dokladu.
        dob         : Volitelné datum narození (libovolný formát) pro zpřesnění.
        threshold   : Minimální fuzzy skóre (0-100) před bonusem za DOB.
        max_hits    : Maximální počet vrácených zásahů, řazeno sestupně podle skóre.

        Návratová hodnota
        -------
        List[AmlHit] - prázdný seznam znamená, že nebyla nalezena shoda.
        """
        if self._con is None:
            self.connect()

        norm_query = _normalize(full_name)
        if not norm_query:
            return []

        query_tokens = _tokens(norm_query)
        if not query_tokens:
            return []

        # 1. Načtení kandidátů přes FTS5 — O(log n) na token přes FTS index
        match = _match_expr(query_tokens)
        try:
            rows = self._con.execute(_SELECT, (match,)).fetchall()  # type: ignore[union-attr]
        except sqlite3.OperationalError as exc:
            logger.error("FTS5 query failed (expr=%r): %s", match, exc)
            return []

        if not rows:
            return []

        # 2. Skórování Rapidfuzz proti aliasům každého kandidáta
        q_digits = re.sub(r"\D", "", dob) if dob else ""
        hits: list[AmlHit] = []

        for row in rows:
            all_names: list[str] = row["all_names"].split(_NAMES_SEP)
            best_score = 0
            best_display = row["primary_name"]

            for display_name in all_names:
                norm_alias = _normalize(display_name)
                # Fuzzy párování aliasů (nezávislé na pořadí tokenů):
                # https://rapidfuzz.github.io/RapidFuzz/Usage/fuzz.html
                score = fuzz.token_sort_ratio(norm_query, norm_alias)
                if score > best_score:
                    best_score = score
                    best_display = display_name

            if best_score < threshold:
                continue

            # 3. Bonus za datum narození
            if q_digits and row["birth_date"]:
                r_digits = re.sub(r"\D", "", row["birth_date"])
                if r_digits and q_digits == r_digits:
                    best_score = min(100, best_score + DOB_BONUS)

            hits.append(
                AmlHit(
                    list_type=row["list_type"],
                    entity_id=row["entity_id"],
                    schema=row["schema"],
                    name=row["primary_name"],
                    matched_alias=best_display,
                    score=best_score,
                    birth_date=row["birth_date"],
                    countries=row["countries"],
                    dataset=row["dataset"],
                    program_ids=row["program_ids"],
                )
            )

        hits.sort(key=lambda h: h.score, reverse=True)
        return hits[:max_hits]

    # ------------------------------------------------------------------
    # Pomocné rozhraní
    # ------------------------------------------------------------------

    @property
    def record_count(self) -> int:
        if self._con is None:
            self.connect()
        row = self._con.execute("SELECT COUNT(*) FROM aml_fts").fetchone()  # type: ignore[union-attr]
        return row[0]
