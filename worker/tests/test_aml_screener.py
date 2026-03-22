"""Testy pro AmlScreener - používají malá syntetická CSV data, ne reálné soubory."""

import csv
import io
from pathlib import Path

import pytest

# ---------------------------------------------------------------------------
# Pomocné funkce - vytvoří malé CSV soubory v paměti pro testy
# ---------------------------------------------------------------------------

FIELDS = [
    "id", "schema", "name", "aliases", "birth_date", "countries",
    "addresses", "identifiers", "sanctions", "phones", "emails",
    "program_ids", "dataset", "first_seen", "last_seen", "last_change",
]


def _make_csv(*rows: dict) -> str:
    buf = io.StringIO()
    writer = csv.DictWriter(buf, fieldnames=FIELDS)
    writer.writeheader()
    for row in rows:
        full = {f: "" for f in FIELDS}
        full.update(row)
        writer.writerow(full)
    return buf.getvalue()


def _write_tmp(tmp_path: Path, filename: str, content: str) -> Path:
    p = tmp_path / filename
    p.write_text(content, encoding="utf-8")
    return p


# ---------------------------------------------------------------------------
# Fixtura
# ---------------------------------------------------------------------------

@pytest.fixture()
def screener(tmp_path):
    from src.aml.build_db import build_db
    from src.aml.screener import AmlScreener

    peps_content = _make_csv(
        {"id": "P1", "schema": "Person", "name": "Emmanuel Macron",
         "birth_date": "1977-12-21", "countries": "fr", "dataset": "test-peps"},
        {"id": "P2", "schema": "Person", "name": "Kim Jong Un",
         "aliases": "Kim Jong-un;Kim Čong-un",
         "birth_date": "1984-01-08", "countries": "kp", "dataset": "test-peps"},
    )
    sanctions_content = _make_csv(
        {"id": "S1", "schema": "Company", "name": "Acme Weapons LLC",
         "aliases": "Acme Arms;ACME WEAPONS",
         "countries": "ir", "sanctions": "US-OFAC", "dataset": "test-sanctions"},
        {"id": "S2", "schema": "Person", "name": "Hans Müller",
         "birth_date": "1975-03-15", "countries": "de", "dataset": "test-sanctions"},
    )

    peps_path = _write_tmp(tmp_path, "peps.csv", peps_content)
    sanctions_path = _write_tmp(tmp_path, "sanctions.csv", sanctions_content)
    db_path = tmp_path / "aml.db"

    build_db(peps_path, sanctions_path, db_path)

    s = AmlScreener(db_path)
    s.connect()
    yield s
    s.close()


# ---------------------------------------------------------------------------
# Testy
# ---------------------------------------------------------------------------

class TestExactMatch:
    def test_exact_pep(self, screener):
        hits = screener.scan("Emmanuel Macron")
        assert any(h.entity_id == "P1" for h in hits)

    def test_exact_sanction(self, screener):
        hits = screener.scan("Acme Weapons LLC")
        assert any(h.entity_id == "S1" for h in hits)

    def test_list_type_correct(self, screener):
        pep_hits = screener.scan("Emmanuel Macron")
        assert all(h.list_type == "PEP" for h in pep_hits if h.entity_id == "P1")
        sanc_hits = screener.scan("Acme Weapons LLC")
        assert all(h.list_type == "SANCTION" for h in sanc_hits if h.entity_id == "S1")


class TestFuzzyMatch:
    def test_typo_in_name(self, screener):
        # Běžná OCR/pravopisná chyba - jeden token zůstává pro FTS5 vyhledání
        hits = screener.scan("Emanuel Macron")
        ids = [h.entity_id for h in hits]
        assert "P1" in ids

    def test_word_order(self, screener):
        hits = screener.scan("Macron Emmanuel")
        ids = [h.entity_id for h in hits]
        assert "P1" in ids

    def test_alias_match(self, screener):
        hits = screener.scan("Kim Jong-un")
        ids = [h.entity_id for h in hits]
        assert "P2" in ids

    def test_diacritic_folding(self, screener):
        # "Muller" by mělo najít "Hans Müller"
        hits = screener.scan("Hans Muller")
        ids = [h.entity_id for h in hits]
        assert "S2" in ids


class TestDobBoost:
    def test_dob_boosts_score(self, screener):
        hits_no_dob = screener.scan("Emmanuel Macron")
        hits_with_dob = screener.scan("Emmanuel Macron", dob="1977-12-21")
        p1_no_dob = next(h for h in hits_no_dob if h.entity_id == "P1")
        p1_with_dob = next(h for h in hits_with_dob if h.entity_id == "P1")
        assert p1_with_dob.score >= p1_no_dob.score

    def test_wrong_dob_no_boost(self, screener):
        hits_wrong = screener.scan("Emmanuel Macron", dob="1960-01-01")
        hits_right = screener.scan("Emmanuel Macron", dob="1977-12-21")
        p1_wrong = next(h for h in hits_wrong if h.entity_id == "P1")
        p1_right = next(h for h in hits_right if h.entity_id == "P1")
        assert p1_right.score >= p1_wrong.score


class TestThreshold:
    def test_high_threshold_filters_weak_hits(self, screener):
        hits = screener.scan("John Smith", threshold=95)
        ids = [h.entity_id for h in hits]
        assert "P1" not in ids
        assert "S1" not in ids

    def test_clean_result_returns_empty(self, screener):
        hits = screener.scan("Completely Unknown Person XYZ", threshold=95)
        assert hits == []


class TestEdgeCases:
    def test_empty_name(self, screener):
        hits = screener.scan("")
        assert hits == []

    def test_context_manager(self, tmp_path):
        from src.aml.build_db import build_db
        from src.aml.screener import AmlScreener

        peps_path = _write_tmp(tmp_path, "peps2.csv", _make_csv())
        sanctions_path = _write_tmp(tmp_path, "sanctions2.csv", _make_csv())
        db_path = tmp_path / "aml2.db"
        build_db(peps_path, sanctions_path, db_path)

        with AmlScreener(db_path) as s:
            assert s.record_count == 0

    def test_missing_db_raises(self, tmp_path):
        from src.aml.screener import AmlScreener
        from src.errors import WorkerError
        s = AmlScreener(tmp_path / "nonexistent.db")
        with pytest.raises(WorkerError, match="AML_DB_NOT_FOUND"):
            s.connect()

    def test_record_count(self, screener):
        assert screener.record_count == 4
