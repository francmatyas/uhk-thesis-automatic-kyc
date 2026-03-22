"""
Parsování a validace českého občanského průkazu (TD1).

České občanské průkazy používají MRZ formát TD1 (3 řádky × 30 znaků):
  - země / národnost      : CZE
  - prefix typu dokladu   : I nebo IC
  - volitelné pole v řádku 1 (pozice 15-29): rodné číslo jako 9-10 číslic
    doplněných znakem '<'. U karet vydaných po roce ~2012 bývá toto pole
    často prázdné a rodné číslo se pak čte z vytištěné zadní strany.

Úrovně spolehlivosti vracené v CzechIDResult:
  "high"   - projdou kontrolní součty čísla dokladu, data narození i platnosti
             A zároveň projde kompozitní kontrola TD1
  "medium" - základní kontroly projdou, ale kompozitní selže; u moderních OP
             je to běžné, protože volitelné pole je prázdná výplň a kompozitní
             kontrola je proto méně spolehlivá
  "low"    - selže některá ze základních kontrol (číslo, datum narození, platnost)
"""

import re
from dataclasses import dataclass, field
from typing import Optional

from passporteye.mrz.text import MRZ

from .mrz_reader import read_mrz_enhanced, clean_name, correct_country_code
from .czech_id_face import read_czech_id_face, CzechIDFaceFields, BarcodeResult
from ..errors import WorkerError


@dataclass
class CzechIDResult:
    # Základní identifikační údaje (z MRZ)
    surname: str
    given_names: str
    date_of_birth: str          # YYMMDD
    sex: str                    # M / F
    expiration_date: str        # YYMMDD
    document_number: str

    # Specifické pro ČR
    national_number: Optional[str]  # např. "740812/2345" nebo None, pokud je nečitelné

    # OCR pole z přední/zadní strany (None, když OCR neběželo nebo pole nebylo nalezeno)
    place_of_birth: Optional[str]   # Místo narození
    address: Optional[str]          # Adresa trvalého pobytu

    # Hodnocení spolehlivosti
    confidence: str                 # "high" / "medium" / "low"
    confidence_notes: list[str] = field(default_factory=list)

    # Čárové kódy nalezené na libovolné straně karty [{format, text, bytes}]
    barcodes: list[BarcodeResult] = field(default_factory=list)

    # Původní dict z PassportEye pro pole, která nejsou vystavena výše
    raw: dict = field(default_factory=dict)


def read_czech_id(
    image_path: str,
    front_image_path: Optional[str] = None,
) -> Optional["CzechIDResult"]:
    """
    Zpracuje obrázek českého občanského průkazu a vrátí CzechIDResult.

    Parametry:
        image_path: Cesta k zadní straně karty (obsahuje MRZ).
        front_image_path: Volitelná cesta k přední straně karty.
                          Pokud je zadaná, place_of_birth se čte z ní.
                          Pokud chybí, place_of_birth bude None.

    Návratová hodnota:
        CzechIDResult při úspěchu, nebo None, pokud se MRZ nenajde.

    Výjimky:
        ValueError: pokud se MRZ najde, ale nejde o český dokument TD1.
    """
    mrz = read_mrz_enhanced(image_path)
    if mrz is None:
        return None

    back_face = read_czech_id_face(image_path)
    front_face = read_czech_id_face(front_image_path) if front_image_path else None
    face = _merge_face_fields(back_face, front_face)

    return parse_czech_id(mrz, face)


def parse_czech_id(mrz: MRZ, face: Optional[CzechIDFaceFields] = None) -> CzechIDResult:
    """
    Převede objekt MRZ z PassportEye na CzechIDResult.

    Vyhodí ValueError, pokud MRZ neodpovídá českému dokumentu TD1.
    Volá se přímo po read_mrz_enhanced(), který zajišťuje, že MRZ je validní a obsahuje potřebná pole.
    """
    d = mrz.to_dict()
    notes: list[str] = []

    # ── Validace typu dokumentu ───────────────────────────────────────────
    country = correct_country_code(d.get("country", ""))
    mrz_type = d.get("mrz_type", "")
    doc_type = d.get("type", "")

    if mrz_type != "TD1":
        raise WorkerError("INVALID_MRZ_TYPE")
    if country != "CZE":
        raise WorkerError("INVALID_DOCUMENT_COUNTRY")
    if not doc_type.startswith("I"):
        notes.append("UNEXPECTED_DOC_TYPE")

    # ── Základní identifikační údaje ─────────────────────────────────────
    surname = clean_name(d.get("surname", ""))
    given_names = clean_name(d.get("names", ""))
    dob = d.get("date_of_birth", "")
    sex = d.get("sex", "")
    expiry = d.get("expiration_date", "")
    doc_number = d.get("number", "")

    # ── Rodné číslo ───────────────────────────────────────────────────────
    # Preferujeme hodnotu vytištěnou na zadní straně (OCR), náhradou je
    # volitelné pole MRZ, které bývá u českých OP po roce 2012 často prázdné.
    national_number = (
        (face.national_number if face else None)
        or _extract_national_number(d.get("raw_text", ""))
    )

    # ── Spolehlivost ──────────────────────────────────────────────────────
    valid_number = d.get("valid_number", False)
    valid_dob = d.get("valid_date_of_birth", False)
    valid_expiry = d.get("valid_expiration_date", False)
    valid_composite = d.get("valid_composite", False)

    if not valid_number:
        notes.append("CHECKSUM_NUMBER_FAILED")
    if not valid_dob:
        notes.append("CHECKSUM_DOB_FAILED")
    if not valid_expiry:
        notes.append("CHECKSUM_EXPIRY_FAILED")
    if not valid_composite:
        # Kompozitní kontrola TD1 pokrývá oba řádky 1+2 včetně volitelného pole.
        # U moderních českých OP je volitelné pole prázdná výplň, takže OCR šum
        # v tomto poli tuto kontrolu spolehlivě rozbije - bereme to jako očekávané.
        notes.append("CHECKSUM_COMPOSITE_FAILED")

    core_ok = valid_number and valid_dob and valid_expiry
    if not core_ok:
        confidence = "low"
    elif valid_composite:
        confidence = "high"
    else:
        confidence = "medium"

    return CzechIDResult(
        surname=surname,
        given_names=given_names,
        date_of_birth=dob,
        sex=sex,
        expiration_date=expiry,
        document_number=doc_number,
        national_number=national_number,
        place_of_birth=face.place_of_birth if face else None,
        address=face.address if face else None,
        confidence=confidence,
        confidence_notes=notes,
        barcodes=face.barcodes if face else [],
        raw=d,
    )


# ---------------------------------------------------------------------------
# Sloučení polí z obou stran karty
# ---------------------------------------------------------------------------

def _merge_face_fields(
    back: Optional[CzechIDFaceFields],
    front: Optional[CzechIDFaceFields],
) -> Optional[CzechIDFaceFields]:
    """
    Sloučí OCR výsledky ze zadní a přední strany karty.

    Priorita polí:
      - address         : zadní strana
      - place_of_birth  : přední strana
      - national_number : zadní strana
      - barcodes        : sjednocení obou stran, deduplikace podle (format, text)
    """
    if back is None and front is None:
        return None
    if back is None:
        return front
    if front is None:
        return back

    seen: set[tuple[str, str]] = {(b["format"], b["text"]) for b in back.barcodes}
    combined_barcodes = back.barcodes + [
        b for b in front.barcodes if (b["format"], b["text"]) not in seen
    ]

    return CzechIDFaceFields(
        place_of_birth=front.place_of_birth or back.place_of_birth,
        address=back.address or front.address,
        national_number=back.national_number or front.national_number,
        barcodes=combined_barcodes,
        raw_ocr=f"[BACK]\n{back.raw_ocr}\n[FRONT]\n{front.raw_ocr}",
    )


# ---------------------------------------------------------------------------
# Extrakce rodného čísla z volitelného pole MRZ
# ---------------------------------------------------------------------------

# Rodné číslo má 9 nebo 10 číslic. Ve volitelném poli TD1 (pozice 15–29
# v řádku 1) je jako čisté číslice doplněné znakem '<', např. "7408122345<<<<<".
_RC_PATTERN = re.compile(r"([0-9]{6}[0-9]{3,4})")


def _extract_national_number(raw_text: str) -> Optional[str]:
    """
    Vytáhne rodné číslo ze surového textu MRZ.

    Rozložení řádku 1 formátu TD1 (pozice indexované od 0):
      0-1   typ dokumentu  (např. "IC")
      2-4   kód země       (např. "CZE")
      5-13  číslo dokladu
      14    kontrolní číslice čísla dokladu
      15-29 volitelné pole -> rodné číslo (9-10 číslic) + výplň '<'

    Vrací None, pokud pole chybí, je příliš krátké nebo neobsahuje číslice.
    """
    if not raw_text:
        return None

    lines = [l.strip() for l in raw_text.strip().splitlines() if l.strip()]
    if not lines or len(lines[0]) < 25:
        return None

    optional_field = lines[0][15:30].replace("<", "").replace(" ", "")
    match = _RC_PATTERN.search(optional_field)
    return match.group(1) if match else None
