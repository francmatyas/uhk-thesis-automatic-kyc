"""
Testovací prostředí PassportEye.

Testy pokrývají:
  1. Parsování MRZ textu (není potřeba obrázek)
  2. Čtení MRZ z obrázku (vyžaduje tesseract)
  3. Pomocné funkce pro extrakci polí
"""

import os
from pathlib import Path

import pytest
from passporteye import read_mrz
from passporteye.mrz.text import MRZ

SAMPLES_DIR = Path(__file__).parent / "samples"
SAMPLE_PASSPORT = SAMPLES_DIR / "sample_passport.png"

# ── Standardní testovací data ICAO TD3 ──────────────────────────────────────
TD3_LINE1 = "P<UTOERIKSSON<<ANNA<MARIA<<<<<<<<<<<<<<<<<<<"
TD3_LINE2 = "L898902C36UTO7408122F1204159ZE184226B<<<<<<1"

# ── Testovací data TD1 (občanský průkaz, 3 řádky) ───────────────────────────
TD1_LINE1 = "I<UTOD231458907<<<<<<<<<<<<<<<<<"
TD1_LINE2 = "7408122F1204159UTO<<<<<<<<<<<6"
TD1_LINE3 = "ERIKSSON<<ANNA<MARIA<<<<<<<<<<"


# ─────────────────────────────────────────────────────────────────────────────
# 1. Parsování MRZ textu
# ─────────────────────────────────────────────────────────────────────────────

class TestMRZTextParsing:
    def test_td3_type_detected(self):
        mrz = MRZ([TD3_LINE1, TD3_LINE2])
        assert mrz.mrz_type == "TD3"

    def test_td3_surname(self):
        mrz = MRZ([TD3_LINE1, TD3_LINE2])
        d = mrz.to_dict()
        assert d["surname"] == "ERIKSSON"

    def test_td3_given_names(self):
        mrz = MRZ([TD3_LINE1, TD3_LINE2])
        d = mrz.to_dict()
        assert "ANNA" in d["names"]
        assert "MARIA" in d["names"]

    def test_td3_country(self):
        mrz = MRZ([TD3_LINE1, TD3_LINE2])
        assert mrz.to_dict()["country"] == "UTO"

    def test_td3_nationality(self):
        mrz = MRZ([TD3_LINE1, TD3_LINE2])
        assert mrz.to_dict()["nationality"] == "UTO"

    def test_td3_dob(self):
        mrz = MRZ([TD3_LINE1, TD3_LINE2])
        assert mrz.to_dict()["date_of_birth"] == "740812"

    def test_td3_expiry(self):
        mrz = MRZ([TD3_LINE1, TD3_LINE2])
        assert mrz.to_dict()["expiration_date"] == "120415"

    def test_td3_sex(self):
        mrz = MRZ([TD3_LINE1, TD3_LINE2])
        assert mrz.to_dict()["sex"] == "F"

    def test_td3_document_number(self):
        mrz = MRZ([TD3_LINE1, TD3_LINE2])
        assert mrz.to_dict()["number"] == "L898902C3"

    def test_td3_valid_score_positive(self):
        mrz = MRZ([TD3_LINE1, TD3_LINE2])
        assert mrz.valid_score > 0

    def test_td3_checksum_number(self):
        mrz = MRZ([TD3_LINE1, TD3_LINE2])
        assert mrz.to_dict()["valid_number"] is True

    def test_td3_checksum_dob(self):
        mrz = MRZ([TD3_LINE1, TD3_LINE2])
        assert mrz.to_dict()["valid_date_of_birth"] is True

    def test_td3_checksum_expiry(self):
        mrz = MRZ([TD3_LINE1, TD3_LINE2])
        assert mrz.to_dict()["valid_expiration_date"] is True

    def test_td3_to_dict_keys(self):
        mrz = MRZ([TD3_LINE1, TD3_LINE2])
        d = mrz.to_dict()
        expected_keys = {
            "mrz_type", "valid_score", "type", "country", "number",
            "date_of_birth", "expiration_date", "nationality", "sex",
            "names", "surname",
        }
        assert expected_keys.issubset(d.keys())

    def test_td1_type_detected(self):
        mrz = MRZ([TD1_LINE1, TD1_LINE2, TD1_LINE3])
        assert mrz.mrz_type == "TD1"

    def test_td1_surname(self):
        mrz = MRZ([TD1_LINE1, TD1_LINE2, TD1_LINE3])
        assert mrz.to_dict()["surname"] == "ERIKSSON"

    def test_invalid_lines_low_score(self):
        mrz = MRZ(["XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX",
                   "XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"])
        assert mrz.valid_score < 50


# ─────────────────────────────────────────────────────────────────────────────
# 2. Čtení MRZ z obrázku
# ─────────────────────────────────────────────────────────────────────────────

class TestMRZImageReading:
    @pytest.fixture(autouse=True)
    def require_tesseract(self):
        import shutil
        if not shutil.which("tesseract"):
            pytest.skip("tesseract not installed")

    def test_sample_image_exists(self):
        assert SAMPLE_PASSPORT.exists(), f"Missing sample: {SAMPLE_PASSPORT}"

    def test_read_mrz_returns_result(self):
        result = read_mrz(str(SAMPLE_PASSPORT))
        # Při selhání OCR na syntetickém obrázku může být None - hlavně nesmí spadnout.
        assert result is None or hasattr(result, "to_dict")

    def test_read_mrz_from_bytes(self):
        """Funkce read_mrz přijímá i objekt podobný souboru."""
        with open(SAMPLE_PASSPORT, "rb") as f:
            result = read_mrz(f)
        assert result is None or hasattr(result, "to_dict")


# ─────────────────────────────────────────────────────────────────────────────
# 3. Pomocné funkce pro extrakci polí
# ─────────────────────────────────────────────────────────────────────────────

class TestFieldExtraction:
    """Pomocné funkce, které se typicky staví nad PassportEye."""

    @staticmethod
    def parse_dob(yymmdd: str) -> str:
        """Převede YYMMDD na ISO datum s hranicí století 30 (1900/2000)."""
        yy, mm, dd = int(yymmdd[:2]), yymmdd[2:4], yymmdd[4:6]
        century = "19" if yy >= 30 else "20"
        return f"{century}{yy:02d}-{mm}-{dd}"

    def test_dob_1974(self):
        mrz = MRZ([TD3_LINE1, TD3_LINE2])
        dob_raw = mrz.to_dict()["date_of_birth"]
        assert self.parse_dob(dob_raw) == "1974-08-12"

    def test_full_name_concat(self):
        mrz = MRZ([TD3_LINE1, TD3_LINE2])
        d = mrz.to_dict()
        full = f"{d['surname']} {d['names']}"
        assert full == "ERIKSSON ANNA MARIA"
