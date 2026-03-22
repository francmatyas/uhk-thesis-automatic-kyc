"""
OCR dat z přední/zadní strany českého občanského průkazu.

Extrahuje pole, která jsou vytištěná na kartě, ale NEjsou zakódovaná v MRZ:
  - Místo narození                     - přední strana
  - Adresa trvalého pobytu             - zadní strana
  - Rodné číslo (formát YYMMDD/SSSC)   - zadní strana
  - PDF417 čárový kód                  - zadní strana

Používá Tesseract s jazykovými daty čeština + angličtina (ces+eng).

Předzpracování: pouze zvětšení (silné odšumění/CLAHE zhoršuje tisk na kartě).
"""

import re
from dataclasses import dataclass
from typing import Optional, TypedDict

import cv2
import numpy as np
import pytesseract


class BarcodeResult(TypedDict):
    """Jeden dekódovaný čárový kód z obrázku karty."""
    format: str   # např. "PDF417"
    text: str     # dekódovaný textový obsah
    bytes: bytes  # surová dekódovaná data


@dataclass
class CzechIDFaceFields:
    place_of_birth: Optional[str]    # Místo narození
    address: Optional[str]           # Adresa trvalého pobytu (může být víceřádková)
    national_number: Optional[str]   # např. "740812/3478"
    barcodes: list[BarcodeResult]    # všechny kódy nalezené na této straně
    raw_ocr: str                     # plný OCR text (pro ladění)


# ---------------------------------------------------------------------------
# Vzory českých popisků polí
# ---------------------------------------------------------------------------
# Každá položka: (klíč_pole, zkompilovaný regex).
# Hodnoty bývají typicky na řádku ZA názvem pole.
# Vzory se zkouší v pořadí; první shoda pro dané pole vyhrává.

_FIELD_PATTERNS: list[tuple[str, re.Pattern]] = [
    # ── Místo narození ───────────────────────────────────────────────────
    # Český popisek „Místo narození“, hodnota na dalším řádku
    ("place_of_birth", re.compile(
        r"M[ií]sto\s+narozen[íi][^\n]*\n([^\n]{2,})",
        re.IGNORECASE | re.UNICODE,
    )),
    # Dvojjazyčný tvar „... / PLACE OF BIRTH“, hodnota na dalším řádku
    ("place_of_birth", re.compile(
        r"PLACE\s+OF\s+BIRTH[^/\n]*\n([^\n]{2,})",
        re.IGNORECASE,
    )),
    # Hodnota na stejném řádku za popiskem (např. "Místo narození: NÁCHOD")
    ("place_of_birth", re.compile(
        r"(?:M[ií]sto\s+narozen[íi]|Place\s+of\s+birth)[.:\s]+([A-ZÁČĎÉĚÍŇÓŘŠŤÚŮÝŽ][^\n]{2,})",
        re.IGNORECASE | re.UNICODE,
    )),

    # ── Adresa (zadní strana karty) ──────────────────────────────────────
    # "TRVALÝ POBYT" / "PERMANENT STAY" / plný popisek, hodnota na dalších 1–3 řádcích
    ("address", re.compile(
        r"TRVAL[YÝ]\s+POBYT[^\n]*\n((?:[^\n]+\n?){1,3})",
        re.IGNORECASE | re.UNICODE,
    )),
    ("address", re.compile(
        r"PERMANENT\s+STAY[^\n]*\n((?:[^\n]+\n?){1,3})",
        re.IGNORECASE,
    )),
    ("address", re.compile(
        r"Adresa\s+trval[eé]ho\s+pobytu[^\n]*\n((?:[^\n]+\n?){1,3})",
        re.IGNORECASE | re.UNICODE,
    )),

    # ── Rodné číslo (zadní strana karty) ─────────────────────────────────
    # Popisek na jednom řádku, hodnota „YYMMDD/SSSC“ na dalším
    # POZN.: regex odpovídá českému textu vytištěnému na kartě („RODNÉ ČÍSLO“)
    ("national_number", re.compile(
        r"RODN[EÉ]\s+[ČC][ÍI]SLO[^\n]*\n([0-9]{6}\s*/\s*[0-9]{3,4})",
        re.IGNORECASE | re.UNICODE,
    )),
    # Inline formát YYMMDD/SSSC kdekoliv v textu
    ("national_number", re.compile(
        r"\b([0-9]{6}/[0-9]{3,4})\b",
    )),
]


# ---------------------------------------------------------------------------
# Veřejné API
# ---------------------------------------------------------------------------

def read_czech_id_face(image_path: str) -> CzechIDFaceFields:
    """
    Spustí české OCR a čtení čárových kódů nad obrázkem občanského průkazu.

    Vždy vrací instanci CzechIDFaceFields; jednotlivá pole budou
    None / prázdná, pokud je nelze detekovat.

    Args:
        image_path: Cesta k obrázku karty (přední nebo zadní strana).
    """
    img = cv2.imread(image_path)
    if img is None:
        return CzechIDFaceFields(None, None, None, [], "")

    preprocessed = _preprocess_for_face_ocr(img)
    raw_ocr = _ocr_czech(preprocessed)
    barcodes = _read_barcodes(img)

    fields = _extract_fields(raw_ocr)
    fields.barcodes = barcodes
    return fields


# ---------------------------------------------------------------------------
# Čtení čárových kódů
# ---------------------------------------------------------------------------

def _read_barcodes(img: np.ndarray) -> list[BarcodeResult]:
    """
    Dekóduje všechny čárové kódy v *img* pomocí zxing-cpp.

    PDF417 kódy na českých OP pro spolehlivou detekci vyžadují alespoň
    1,5násobné zvětšení, proto se zkouší více měřítek vzestupně.
    Jakmile se najde jakýkoli kód, zpracování končí.

    Pokud není zxing-cpp nainstalované, vrací prázdný seznam.
    """
    try:
        import zxingcpp
    except ImportError:
        return []

    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    seen: set[tuple[str, str]] = set()
    results: list[BarcodeResult] = []

    for scale in (1.0, 1.5, 2.0, 3.0):
        g = (cv2.resize(gray, None, fx=scale, fy=scale,
                        interpolation=cv2.INTER_CUBIC)
             if scale > 1 else gray)
        for found in zxingcpp.read_barcodes(g):
            key = (str(found.format), found.text)
            if not found.valid or key in seen:
                continue
            seen.add(key)
            results.append(BarcodeResult(
                format=str(found.format),
                text=found.text,
                bytes=found.bytes,
            ))
        if results:
            break  # skončit na prvním měřítku, které vrátí výsledek

    return results


# ---------------------------------------------------------------------------
# Předzpracování
# ---------------------------------------------------------------------------

def _preprocess_for_face_ocr(img: np.ndarray) -> np.ndarray:
    """
    Minimální předzpracování pro OCR strany karty: převod do odstínů šedi
    a zvětšení alespoň na šířku 1200 px.

    Silné odšumění a CLAHE jsou záměrně vynechané - zhoršují kvalitu
    vytištěného textu na kartě. Tesseract si s čistými skeny poradí dobře
    i s minimálními zásahy.
    """
    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    h, w = gray.shape
    if w < 1200:
        gray = cv2.resize(gray, None, fx=1200 / w, fy=1200 / w,
                          interpolation=cv2.INTER_CUBIC)
    return gray


# ---------------------------------------------------------------------------
# OCR
# ---------------------------------------------------------------------------

def _ocr_czech(img: np.ndarray) -> str:
    """
    Spustí Tesseract s českými a anglickými jazykovými daty.

    Nejprve zkouší PSM 6 (předpoklad jednotného bloku textu), který se
    nejlépe hodí pro strukturované rozložení popisek/hodnota na OP.
    Pokud PSM 6 vrátí málo textu, použije fallback PSM 3
    (plně automatická detekce layoutu) a nakonec jako poslední možnost
    pouze angličtinu.
    """
    lang = "ces+eng"
    for psm in (6, 3):
        try:
            text = pytesseract.image_to_string(
                img, lang=lang, config=f"--psm {psm} --oem 3"
            )
            if len(text.strip()) > 20:
                return text
        except Exception:
            pass
    try:
        return pytesseract.image_to_string(
            img, lang="eng", config="--psm 6 --oem 3"
        )
    except Exception:
        return ""


# ---------------------------------------------------------------------------
# Extrakce polí
# ---------------------------------------------------------------------------

def _extract_fields(raw_ocr: str) -> CzechIDFaceFields:
    """
    Extrahuje strukturovaná pole ze surového OCR textu.

    Extrakce adresy nejdřív používá speciální heuristiku s kotvou č.p.,
    protože spolehlivě zachytí všechny tři řádky adresy (město / ulice / okres).
    Vzory podle popisků v _FIELD_PATTERNS slouží jako fallback.
    """
    extracted: dict[str, str] = {}

    # Adresa: preferovat přístup přes kotvu č.p. (zachytí 3 řádky)
    addr = _extract_address_by_cp_anchor(raw_ocr)
    if addr:
        extracted["address"] = addr

    for field_key, pattern in _FIELD_PATTERNS:
        if field_key in extracted:
            continue
        match = pattern.search(raw_ocr)
        if match:
            value = _clean_field_value(match.group(1))
            if value:
                extracted[field_key] = value

    # Normalizace rodného čísla: odstranit mezery kolem lomítka
    rc = extracted.get("national_number")
    if rc:
        extracted["national_number"] = re.sub(r"\s*/\s*", "/", rc).strip()

    # Vyčistit a doplnit místo narození
    pob = extracted.get("place_of_birth")
    if pob:
        pob = _enrich_place_of_birth(_clean_place_of_birth(pob))
        extracted["place_of_birth"] = pob

    return CzechIDFaceFields(
        place_of_birth=extracted.get("place_of_birth"),
        address=extracted.get("address"),
        national_number=extracted.get("national_number"),
        barcodes=[],  # doplní read_czech_id_face po OCR
        raw_ocr=raw_ocr,
    )


def _extract_address_by_cp_anchor(raw_ocr: str) -> Optional[str]:
    """
    Najde blok adresy pomocí značky domovního čísla 'č.p.', která je
    v českých adresách běžná a funguje jako spolehlivá strukturální kotva.

    Skládá: předchozí řádek (město) + řádek s č.p. (ulice) + následující
    řádek (část/okres). Vrací None, pokud se značka nenajde nebo je výsledek prázdný.
    """
    lines = raw_ocr.splitlines()
    for i, line in enumerate(lines):
        if "č.p." in line.lower() or "c.p." in line.lower():
            city_line = lines[i - 1].strip() if i > 0 else ""
            street_line = line.strip()
            district_line = lines[i + 1].strip() if i + 1 < len(lines) else ""

            city = _strip_line_noise(_clean_field_value(city_line))
            street = _strip_line_noise(_clean_field_value(street_line))
            district = _strip_line_noise(_clean_field_value(district_line))

            parts = [p for p in [city, street, district] if p and len(p) > 2]
            if parts:
                return ", ".join(parts)

    return None


def _clean_field_value(raw: str) -> str:
    """
    Obecné čištění hodnoty pole:
      - sloučí mezery/taby na jednu mezeru
      - odstraní řádky prosáklé z MRZ (obsahující '<<<')
      - odstraní koncový symbolový šum (=, +, |, krátké malé tokeny)
      - ořízne text na začátku dalšího názvu pole, pokud OCR pole slije dohromady

    Poznámka: seznam stop-slov (Datum, Pohlaví, ...) ořízne i hodnoty, které
    tato slova obsahují. Je to záměr - jde o typické prosakování popisků -
    ale znamená to, že adresa u ulice typu "Datumová" by se zkrátila.
    V praxi se české názvy ulic s těmito popisky nekryjí.
    """
    value = re.sub(r"[ \t]+", " ", raw).strip()

    # Odstranit MRZ řádky, které se kvůli OCR chybám mohou objevit v textu (obsahují '<<<')
    lines = [l for l in value.splitlines() if not re.search(r"[<]{3,}", l)]
    value = " ".join(l.strip() for l in lines if l.strip())

    # Odstranit jen koncový symbolový šum (ne číslice, mohou patřit adrese)
    value = re.sub(r"(\s+[=+|a-z]{1,4}\s*)+$", "", value,
                   flags=re.IGNORECASE).strip()

    # Oříznout při začátku dalšího názvu pole, pokud došlo k slití textu
    value = re.split(
        r"\b(?:Datum|Pohlaví|Státní|Platnost|Číslo|Rodné|Sex|Date|Nationality|RODNÉ|PODPIS)\b",
        value, maxsplit=1,
    )[0].strip()

    return value


def _strip_line_noise(value: str) -> str:
    """
    Odstraní úvodní číslice/symboly, které jsou OCR šum z okrajů,
    z jednoho řádku adresy.

    Příklady:
        "179 ČESKÁ SKALICE"         → "ČESKÁ SKALICE"
        "= PIVOVARSKÁ č.p. 667"     → "PIVOVARSKÁ č.p. 667"

    Poznámka: odstraňování koncového šumu je záměrně omezené na známé
    OCR artefakty (krátké tokeny malými písmeny jako 'alll', 'ANE'),
    ne na libovolná krátká velká slova, aby nedocházelo k omylům u názvů
    měst jako BRNO, CHEB nebo AŠ.
    """
    # Odstranit úvodní číslice a interpunkci
    value = re.sub(r"^[\d\s=+|\-]+", "", value).strip()
    # Odstranit koncové tokeny, které jsou zjevný OCR šum: malá písmena, 1–4 znaky
    value = re.sub(r"\s+[a-z]{1,4}$", "", value).strip()
    return value


def _clean_place_of_birth(raw: str) -> str:
    """
    Dodatečné čištění místa narození: odstraní úvodní šumové tokeny jako
    'al)', které se kvůli OCR chybám na okrajích karty objevují před
    'okr.' nebo před názvem města.
    """
    # Odstranit úvodní tokeny, které jsou jen ne-písmenné nebo krátký šum
    # např. "al) okr. NACHOD" → "okr. NACHOD"
    value = re.sub(
        r"^(?:[^a-zA-ZÁČĎÉĚÍŇÓŘŠŤÚŮÝŽ]*\b\w{1,2}\b\s*[\)\]>]*\s+)+",
        "", raw,
    ).strip()
    # Odstranit úvodní samostatný symbol
    value = re.sub(r"^[=+|\-]{1,3}\s+", "", value).strip()
    return value


def _enrich_place_of_birth(value: str) -> str:
    """
    Když OCR vrátí jen 'okr. MĚSTO' (pouze okres), doplní tvar na
    'MĚSTO, okr. MĚSTO' - u mnoha menších českých měst má město i okres
    stejný název (např. 'NÁCHOD, okr. NÁCHOD').
    """
    m = re.match(
        r"okr\.\s*([A-ZÁČĎÉĚÍŇÓŘŠŤÚŮÝŽ][A-ZÁČĎÉĚÍŇÓŘŠŤÚŮÝŽ ]+)",
        value, re.UNICODE,
    )
    if m:
        city = m.group(1).strip()
        return f"{city}, okr. {city}"
    return value
