"""
Jednorázový build skript: zpracuje peps.csv + sanctions.csv -> data/aml.db (SQLite/FTS5).

Spusťte jednou (nebo po aktualizaci CSV):
    python -m src/aml/build_db

Vlastní cesty:
    python -m src/aml/build_db --peps /p/peps.csv --sanctions /p/sanctions.csv --db /p/aml.db
"""

from __future__ import annotations

import argparse
import csv
import logging
import re
import sqlite3
import sys
import time
import unicodedata
from pathlib import Path
from typing import Iterator

logger = logging.getLogger(__name__)

# ---------------------------------------------------------------------------
# Výchozí hodnoty
# ---------------------------------------------------------------------------

_ROOT = Path(__file__).parent.parent.parent
DEFAULT_PEPS = _ROOT / "data" / "peps.csv"
DEFAULT_SANCTIONS = _ROOT / "data" / "sanctions.csv"
DEFAULT_DB = _ROOT / "data" / "aml.db"

BATCH_SIZE = 2_000
LOG_EVERY = 100_000

# ---------------------------------------------------------------------------
# Normalizace jmen (stejná logika jako při dotazu v aml_screener.py)
# ---------------------------------------------------------------------------

_PUNCT_RE = re.compile(r"[^\w\s]", re.UNICODE)


def _normalize(text: str) -> str:
    nfkd = unicodedata.normalize("NFKD", text)
    ascii_approx = "".join(c for c in nfkd if not unicodedata.combining(c))
    no_punct = _PUNCT_RE.sub(" ", ascii_approx.lower())
    return " ".join(no_punct.split())


def _parse_aliases(raw: str) -> list[str]:
    if not raw.strip():
        return []
    return [
        p.strip().strip('"').strip()
        for p in raw.split(";")
        if p.strip().strip('"').strip()
    ]


# ---------------------------------------------------------------------------
# Řádek -> tuple
# ---------------------------------------------------------------------------

_NAMES_SEP = "\x1f"   # Oddělovač ASCII unit-separator; bezpečný - ve jménech se nevyskytuje


def _row_to_tuple(row: dict, list_type: str) -> tuple | None:
    name = row.get("name", "").strip()
    if not name:
        return None

    aliases = _parse_aliases(row.get("aliases", ""))
    all_display = [name] + aliases          # původní zápis
    # text spojený mezerami po normalizaci jde do FTS5 indexovaného sloupce
    search_text = " ".join(_normalize(n) for n in all_display if n)
    if not search_text.strip():
        return None

    return (
        row.get("id", ""),                  # entity_id
        list_type,                          # list_type
        row.get("schema", ""),              # schema
        row.get("birth_date", ""),          # birth_date
        row.get("countries", ""),           # countries
        row.get("dataset", ""),             # dataset
        row.get("program_ids", ""),         # program_ids
        name,                               # primary_name  (původní)
        _NAMES_SEP.join(all_display),       # all_names     (původní, spojeno oddělovačem)
        search_text,                        # search_text   (indexované přes FTS5)
    )


# ---------------------------------------------------------------------------
# Streamování řádků z jednoho CSV
# ---------------------------------------------------------------------------

def _iter_csv(path: Path, list_type: str) -> Iterator[tuple]:
    with path.open(newline="", encoding="utf-8") as fh:
        reader = csv.DictReader(fh)
        for row in reader:
            t = _row_to_tuple(row, list_type)
            if t is not None:
                yield t


# ---------------------------------------------------------------------------
# Hlavní build funkce (volatelná i z testů)
# ---------------------------------------------------------------------------

def build_db(
    peps_path: Path,
    sanctions_path: Path,
    db_path: Path,
) -> int:
    """
    Vytvoří (nebo znovu vytvoří) *db_path* ze dvou CSV souborů.
    Vrací celkový počet vložených záznamů.
    """
    db_path.unlink(missing_ok=True)   # vždy začít znovu
    con = sqlite3.connect(str(db_path))
    try:
        con.executescript("""
            PRAGMA journal_mode = WAL;
            PRAGMA synchronous  = NORMAL;
            PRAGMA cache_size   = -65536;   -- 64 MB page cache během buildu

            CREATE VIRTUAL TABLE aml_fts USING fts5(
                entity_id    UNINDEXED,
                list_type    UNINDEXED,
                schema       UNINDEXED,
                birth_date   UNINDEXED,
                countries    UNINDEXED,
                dataset      UNINDEXED,
                program_ids  UNINDEXED,
                primary_name UNINDEXED,
                all_names    UNINDEXED,
                search_text,
                tokenize = 'unicode61 remove_diacritics 2'
            );
        """)

        INSERT = (
            "INSERT INTO aml_fts("
            "entity_id, list_type, schema, birth_date, countries, "
            "dataset, program_ids, primary_name, all_names, search_text"
            ") VALUES (?,?,?,?,?,?,?,?,?,?)"
        )

        total = 0
        batch: list[tuple] = []

        def _flush() -> None:
            nonlocal total
            con.executemany(INSERT, batch)
            total += len(batch)
            batch.clear()

        sources = [
            (peps_path, "PEP"),
            (sanctions_path, "SANCTION"),
        ]

        for path, list_type in sources:
            logger.info("Ingesting %s from %s …", list_type, path)
            t0 = time.monotonic()
            count = 0
            for tup in _iter_csv(path, list_type):
                batch.append(tup)
                count += 1
                if len(batch) >= BATCH_SIZE:
                    _flush()
                if count % LOG_EVERY == 0:
                    logger.info("  … %d %s rows processed", count, list_type)
            if batch:
                _flush()
            elapsed = time.monotonic() - t0
            logger.info("  %s done: %d rows in %.1f s", list_type, count, elapsed)

        con.commit()
        logger.info("FTS5 index optimising …")
        con.execute("INSERT INTO aml_fts(aml_fts) VALUES('optimize')")
        con.commit()
        logger.info("Build complete: %d total records → %s", total, db_path)
        return total

    finally:
        con.close()


# ---------------------------------------------------------------------------
# CLI
# ---------------------------------------------------------------------------

def _cli() -> None:
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s [%(levelname)s] %(message)s",
    )
    parser = argparse.ArgumentParser(description="Build AML SQLite database from CSV files.")
    parser.add_argument("--peps",       default=str(DEFAULT_PEPS),      help="Path to peps.csv")
    parser.add_argument("--sanctions",  default=str(DEFAULT_SANCTIONS),  help="Path to sanctions.csv")
    parser.add_argument("--db",         default=str(DEFAULT_DB),         help="Output database path")
    args = parser.parse_args()

    peps = Path(args.peps)
    sanctions = Path(args.sanctions)
    db = Path(args.db)

    for p in (peps, sanctions):
        if not p.exists():
            print(f"ERROR: file not found: {p}", file=sys.stderr)
            sys.exit(1)

    t0 = time.monotonic()
    n = build_db(peps, sanctions, db)
    print(f"Done. {n:,} records in {time.monotonic() - t0:.1f} s → {db}")


if __name__ == "__main__":
    _cli()
