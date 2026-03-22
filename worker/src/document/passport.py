"""
Parsování a validace cestovního pasu (TD3).

Cestovní pasy používají MRZ formát TD3 (2 řádky × 44 znaků):
  - typ dokumentu      : začíná na 'P' (pas), za tím volitelná podkategorie
  - kód vydávající země: 3znakový ICAO kód
  - řádek 2            : číslo dokladu, národnost, datum narození, pohlaví,
                         datum platnosti, osobní číslo (volitelné), kontrolní součty

Úrovně spolehlivosti vracené v PassportResult:
  "high"   - projdou kontrolní součty čísla dokladu, data narození, platnosti,
             osobního čísla i kompozitní kontrola TD3
  "medium" - základní kontroly projdou, ale kompozitní nebo osobní číslo selže
  "low"    - selže některá ze základních kontrol (číslo, datum narození, platnost)
"""

from dataclasses import dataclass, field
from typing import Optional

from passporteye.mrz.text import MRZ

from .mrz_reader import read_mrz_enhanced, clean_name, correct_country_code
from ..errors import WorkerError


@dataclass
class PassportResult:
    # Základní identifikační údaje (z MRZ)
    surname: str
    given_names: str
    date_of_birth: str          # YYMMDD
    sex: str                    # M / F / X
    expiration_date: str        # YYMMDD
    document_number: str

    # Geografické údaje
    issuing_country: str        # ICAO kód vydávající země (např. "CZE")
    nationality: str            # ICAO kód národnosti (např. "CZE")

    # Osobní číslo (volitelné pole TD3; rodné číslo u českých pasů, jinak prázdné)
    personal_number: Optional[str]

    # Hodnocení spolehlivosti
    confidence: str                 # "high" / "medium" / "low"
    confidence_notes: list[str] = field(default_factory=list)

    # Původní dict z PassportEye pro pole, která nejsou vystavena výše
    raw: dict = field(default_factory=dict)


def read_passport(image_path: str) -> Optional[PassportResult]:
    """
    Zpracuje obrázek datové stránky cestovního pasu a vrátí PassportResult.

    Parametry:
        image_path: Cesta k datové stránce pasu (obsahuje MRZ).

    Návratová hodnota:
        PassportResult při úspěchu, nebo None, pokud se MRZ nenajde.

    Výjimky:
        WorkerError("IMAGE_READ_ERROR"): pokud obrázek nelze načíst.
        WorkerError("INVALID_MRZ_TYPE"): pokud MRZ není formátu TD3.
    """
    mrz = read_mrz_enhanced(image_path)
    if mrz is None:
        return None

    return parse_passport(mrz)


def parse_passport(mrz: MRZ) -> PassportResult:
    """
    Převede objekt MRZ z PassportEye na PassportResult.

    Vyhodí WorkerError, pokud MRZ neodpovídá formátu TD3.
    """
    d = mrz.to_dict()
    notes: list[str] = []

    # ── Validace typu dokumentu ───────────────────────────────────────────
    mrz_type = d.get("mrz_type", "")
    doc_type = d.get("type", "")

    if mrz_type != "TD3":
        raise WorkerError("INVALID_MRZ_TYPE")
    if not doc_type.startswith("P"):
        notes.append("UNEXPECTED_DOC_TYPE")

    # ── Základní identifikační údaje ─────────────────────────────────────
    surname = clean_name(d.get("surname", ""))
    given_names = clean_name(d.get("names", ""))
    dob = d.get("date_of_birth", "")
    sex = d.get("sex", "")
    expiry = d.get("expiration_date", "")
    doc_number = d.get("number", "").rstrip("<")
    issuing_country = correct_country_code(d.get("country", ""))
    nationality = correct_country_code(d.get("nationality", ""))

    # ── Osobní číslo (volitelné pole TD3) ─────────────────────────────────
    personal_number = _extract_personal_number(d.get("personal_number", ""))

    # ── Spolehlivost ──────────────────────────────────────────────────────
    valid_number = d.get("valid_number", False)
    valid_dob = d.get("valid_date_of_birth", False)
    valid_expiry = d.get("valid_expiration_date", False)
    valid_personal = d.get("valid_personal_number", False)
    valid_composite = d.get("valid_composite", False)

    if not valid_number:
        notes.append("CHECKSUM_NUMBER_FAILED")
    if not valid_dob:
        notes.append("CHECKSUM_DOB_FAILED")
    if not valid_expiry:
        notes.append("CHECKSUM_EXPIRY_FAILED")
    if not valid_personal:
        notes.append("CHECKSUM_PERSONAL_FAILED")
    if not valid_composite:
        notes.append("CHECKSUM_COMPOSITE_FAILED")

    core_ok = valid_number and valid_dob and valid_expiry
    if not core_ok:
        confidence = "low"
    elif valid_composite and valid_personal:
        confidence = "high"
    else:
        confidence = "medium"

    return PassportResult(
        surname=surname,
        given_names=given_names,
        date_of_birth=dob,
        sex=sex,
        expiration_date=expiry,
        document_number=doc_number,
        issuing_country=issuing_country,
        nationality=nationality,
        personal_number=personal_number,
        confidence=confidence,
        confidence_notes=notes,
        raw=d,
    )


def _extract_personal_number(raw: str) -> Optional[str]:
    """
    Vrátí osobní číslo z volitelného pole TD3, nebo None, pokud je prázdné.

    Pole je vyplněno znakem '<' do délky 14 znaků. Pokud po oříznutí '<'
    nic nezbyde, vrací None.
    """
    value = raw.strip().rstrip("<").strip()
    return value if value else None
