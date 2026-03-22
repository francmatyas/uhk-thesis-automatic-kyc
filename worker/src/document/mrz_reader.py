"""
Rozšířená čtečka MRZ obalující PassportEye více detekčními strategiemi.

Strategie (zkoušejí se v pořadí, vyhrává nejvyšší valid_score):
  1. výchozí PassportEye
  2. PassportEye s legacy Tesseract enginem (--oem 0), přeskočí se, pokud
     není nainstalované legacy traineddata
  3. PassportEye nad obrazem předzpracovaným přes OpenCV
     (odšumění + CLAHE + narovnání)
  4. fallback s ořezem spodní části: přímý Tesseract nad spodními 28 % obrázku
  5. opakované OCR ROI oblasti nalezené PassportEye, po jednotlivých
     MRZ řádcích s PSM 7 pro lepší přesnost jednoho řádku
"""

import re
import tempfile
import warnings
from contextlib import contextmanager
from pathlib import Path
from typing import Iterator, Optional

from ..errors import WorkerError

import cv2
import numpy as np
import pytesseract
# Použití PassportEye API (read_mrz / MRZ typy):
# https://passporteye.readthedocs.io/en/latest/python_usage.html
from passporteye import read_mrz
from passporteye.mrz.image import MRZPipeline
from passporteye.mrz.text import MRZ


# ---------------------------------------------------------------------------
# Veřejný vstupní bod
# ---------------------------------------------------------------------------

def read_mrz_enhanced(image_path: str) -> Optional[MRZ]:
    """
    Vyzkouší více detekčních strategií a vrátí MRZ s nejvyšším valid_score.
    None vrací pouze tehdy, když žádná strategie MRZ nenajde.

    Pokud obrázek nelze načíst, vyhodí FileNotFoundError.
    """
    img_bgr = cv2.imread(image_path)
    if img_bgr is None:
        raise WorkerError("IMAGE_READ_ERROR")

    best: Optional[MRZ] = None

    def _keep_best(candidate: Optional[MRZ], label: str) -> None:
        nonlocal best
        if candidate is None:
            return
        score = candidate.valid_score
        prev = best.valid_score if best else -1
        print(f"  [{label}] valid_score={score}")
        if score > prev:
            best = candidate

    # Strategie 1: výchozí PassportEye
    _keep_best(_passporteye(image_path), "passporteye:default")
    if best and best.valid and best.valid_score >= 90:
        return best

    # Strategie 2: PassportEye + legacy OCR engine (oem 0)
    # Přeskočí se, pokud není nainstalované legacy traineddata.
    # Dokumentace k Tesseract OEM:
    # https://tesseract-ocr.github.io/tessdoc/Command-Line-Usage.html
    _keep_best(_passporteye(image_path, extra="--oem 0"), "passporteye:oem0")
    if best and best.valid and best.valid_score >= 90:
        return best

    # Strategie 3: PassportEye nad předzpracovanou kopií (odšuměná + CLAHE + narovnaná)
    with _preprocessed_tempfile(img_bgr) as preprocessed_path:
        _keep_best(_passporteye(preprocessed_path), "passporteye:preprocessed")
        _keep_best(_passporteye(preprocessed_path, extra="--oem 0"), "passporteye:preprocessed+oem0")
    if best and best.valid and best.valid_score >= 90:
        return best

    # Strategie 4: oříznout spodních 28 % a spustit OCR přímo
    _keep_best(_bottom_crop_ocr(img_bgr), "bottom-crop:direct")
    if best and best.valid and best.valid_score >= 90:
        return best

    # Strategie 5: znovu provést OCR ROI oblasti z PassportEye, po řádcích
    _keep_best(_reocr_roi(image_path), "reocr-roi")

    return best


# ---------------------------------------------------------------------------
# Pomocné funkce strategií
# ---------------------------------------------------------------------------

def _passporteye(path: str, extra: str = "") -> Optional[MRZ]:
    """
    Spustí PassportEye nad *path* s volitelnými extra argumenty pro Tesseract.
    Při jakékoliv chybě vrací None, včetně chybějících legacy traineddata.
    """
    with warnings.catch_warnings():
        warnings.simplefilter("ignore")
        try:
            return read_mrz(path, extra_cmdline_params=extra)
        except Exception:
            return None


@contextmanager
def _preprocessed_tempfile(img_bgr: np.ndarray) -> Iterator[str]:
    """
    Správce kontextu zapíše předzpracovanou šedotónovou kopii *img_bgr*
    do dočasného PNG souboru, vrátí jeho cestu a při ukončení ho smaže.
    """
    processed = _preprocess(img_bgr)
    tmp = tempfile.NamedTemporaryFile(suffix=".png", delete=False)
    try:
        cv2.imwrite(tmp.name, processed)
        tmp.close()
        yield tmp.name
    finally:
        Path(tmp.name).unlink(missing_ok=True)


def _preprocess(img_bgr: np.ndarray) -> np.ndarray:
    """
    Pipeline předzpracování pro detekci MRZ:
      1. převod do odstínů šedi
      2. zvětšení alespoň na šířku 1000 px (PassportEye interně škáluje na 250 px,
         takže větší vstup lépe zachová detaily znaků MRZ)
      3. odšumění metodou non-local means
      4. zvýšení kontrastu pomocí CLAHE (pomáhá u nízkokontrastních skenů)
      5. narovnání pomocí odhadu úhlu z Houghových přímek

    Vrací šedotónový obrázek typu uint8.
    """
    gray = cv2.cvtColor(img_bgr, cv2.COLOR_BGR2GRAY)

    h, w = gray.shape
    if w < 1000:
        gray = cv2.resize(gray, None, fx=1000 / w, fy=1000 / w,
                          interpolation=cv2.INTER_CUBIC)

    gray = cv2.fastNlMeansDenoising(gray, h=10, templateWindowSize=7,
                                    searchWindowSize=21)

    clahe = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8))
    gray = clahe.apply(gray)

    gray = _deskew(gray)
    return gray


def _deskew(gray: np.ndarray) -> np.ndarray:
    """
    Detekuje a opraví sklon obrazu pomocí Houghových přímek na Canny mapě hran.
    Opravuje pouze úhly menší než ±10°; odchylky pod 0,3° ignoruje jako šum.
    """
    edges = cv2.Canny(gray, 50, 150, apertureSize=3)
    lines = cv2.HoughLines(edges, 1, np.pi / 180,
                           threshold=max(100, gray.shape[1] // 8))
    if lines is None:
        return gray

    angles = [
        np.degrees(theta) - 90
        for _, theta in lines[:, 0]
        if abs(np.degrees(theta) - 90) < 10
    ]
    if not angles:
        return gray

    skew = float(np.median(angles))
    if abs(skew) < 0.3:
        return gray

    h, w = gray.shape
    M = cv2.getRotationMatrix2D((w / 2, h / 2), skew, 1.0)
    return cv2.warpAffine(gray, M, (w, h), flags=cv2.INTER_CUBIC,
                          borderMode=cv2.BORDER_REPLICATE)


def _bottom_crop_ocr(img_bgr: np.ndarray) -> Optional[MRZ]:
    """
    MRZ zóna je vždy přibližně ve spodních 28 % dokumentu.
    Tuto oblast ořízne, zbinarizuje a spustí nad ní Tesseract přímo
    s whitelistem znaků vhodným pro obsah MRZ.
    """
    h, w = img_bgr.shape[:2]
    crop = img_bgr[int(h * 0.72):, :]

    gray = cv2.cvtColor(crop, cv2.COLOR_BGR2GRAY)

    scale = max(1.0, 1500 / w)
    if scale > 1:
        gray = cv2.resize(gray, None, fx=scale, fy=scale,
                          interpolation=cv2.INTER_CUBIC)

    gray = cv2.fastNlMeansDenoising(gray, h=10)
    _, binary = cv2.threshold(gray, 0, 255,
                              cv2.THRESH_BINARY + cv2.THRESH_OTSU)

    cfg = ("--psm 6 --oem 3 "
           "-c tessedit_char_whitelist=ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789>< "
           "-c load_system_dawg=F -c load_freq_dawg=F")
    # Parametry příkazové řádky Tesseractu (--psm / --oem):
    # https://tesseract-ocr.github.io/tessdoc/Command-Line-Usage.html
    try:
        raw = pytesseract.image_to_string(binary, config=cfg).strip()
    except Exception:
        return None

    return _parse_raw_ocr(raw)


def _reocr_roi(image_path: str) -> Optional[MRZ]:
    """
    Nechá PassportEye najít oblast MRZ bez úplného parsování a pak
    tuto oblast znovu zpracuje OCR ve vysokém rozlišení po řádcích (PSM 7).

    ROI z PassportEye je šedotónové pole float64 v intervalu [0, 1].
    Počet MRZ řádků (2 pro pas TD3, 3 pro občanku TD1) se odhaduje
    z poměru stran ROI: široká a nízká oblast -> 2 řádky, vyšší -> 3 řádky.
    """
    with warnings.catch_warnings():
        warnings.simplefilter("ignore")
        try:
            p = MRZPipeline(image_path)
            _ = p.result
            roi = p.data.get("roi")
        except Exception:
            return None

    if roi is None:
        return None

    roi_u8 = (roi * 255).astype(np.uint8)

    # Zajistit dostatečnou šířku ROI pro spolehlivé OCR po znacích
    target_width = 1050
    if roi_u8.shape[1] < target_width:
        scale = max(1, round(target_width / roi_u8.shape[1]))
        roi_u8 = cv2.resize(roi_u8, None, fx=scale, fy=scale,
                             interpolation=cv2.INTER_CUBIC)

    binary = cv2.adaptiveThreshold(roi_u8, 255,
                                   cv2.ADAPTIVE_THRESH_GAUSSIAN_C,
                                   cv2.THRESH_BINARY, 31, 10)

    # TD3 (pas) = 2 řádky MRZ; TD1 (občanka) = 3 řádky MRZ.
    # ICAO Doc 9303 (část 4 TD3, část 5 TD1):
    # https://www.icao.int/publications/doc-series/doc-9303
    # Odhad podle poměru stran: řádky TD3 mají ~44 znaků a 2 řádky na výšku,
    # tedy široký poměr; TD1 má 30 znaků × 3 řádky, tedy spíše užší poměr.
    h, w = binary.shape
    n_lines = 2 if (w / max(h, 1)) > 6 else 3
    line_height = h // n_lines

    cfg = ("--psm 7 --oem 3 "
           "-c tessedit_char_whitelist=ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789>< "
           "-c load_system_dawg=F -c load_freq_dawg=F")

    lines_text = []
    for i in range(n_lines):
        band = binary[i * line_height:(i + 1) * line_height, :]
        if band.shape[0] < 5:
            continue
        text = pytesseract.image_to_string(band, config=cfg).strip()
        text = _normalize_mrz_line(text)
        if len(text) >= 30:
            lines_text.append(text)

    if not lines_text:
        return None

    return _parse_raw_ocr("\n".join(lines_text))


# ---------------------------------------------------------------------------
# OCR text -> parsování MRZ
# ---------------------------------------------------------------------------

# Znaky, které Tesseract často plete za MRZ výplňový znak '<'
_FILLER_MISREADS = re.compile(r"[ \t|!.]")


def _normalize_mrz_line(text: str) -> str:
    """Převede na velká písmena, nahradí záměny výplně a odstraní ne-MRZ znaky."""
    text = text.upper().strip()
    text = _FILLER_MISREADS.sub("<", text)
    text = re.sub(r"[^A-Z0-9<>]", "<", text)
    return text


def _parse_raw_ocr(raw: str) -> Optional[MRZ]:
    """
    Normalizuje surový OCR výstup a pokusí se jej naparsovat jako MRZ.

    Zkouší všechny rozumné kombinace řádků (TD3 = 2 x 44 znaků,
    TD1 = 3 x 30 znaků) a vrací variantu s nejvyšším valid_score.
    """
    lines = [_normalize_mrz_line(l) for l in raw.splitlines()
             if len(l.strip()) >= 20]
    if not lines:
        return None

    td3_lines = [l for l in lines if len(l) >= 40]
    td1_lines = [l for l in lines if len(l) >= 28]

    best: Optional[MRZ] = None

    def _try(line_group: list[str]) -> None:
        nonlocal best
        try:
            mrz = MRZ(line_group)
            if best is None or mrz.valid_score > best.valid_score:
                best = mrz
        except Exception:
            pass

    if len(td3_lines) >= 2:
        _try(td3_lines[:2])
    if len(td1_lines) >= 3:
        _try(td1_lines[:3])
    for i in range(len(lines) - 1):
        _try([lines[i], lines[i + 1]])
    for i in range(len(lines) - 2):
        _try([lines[i], lines[i + 1], lines[i + 2]])

    return best


# ---------------------------------------------------------------------------
# ICAO třípísmenné kódy zemí (ISO 3166-1 alpha-3 + speciální ICAO kódy)
# ---------------------------------------------------------------------------

_ICAO_CODES: frozenset[str] = frozenset({
    "AFG","ALB","DZA","AND","AGO","ATG","ARG","ARM","AUS","AUT","AZE",
    "BHS","BHR","BGD","BRB","BLR","BEL","BLZ","BEN","BTN","BOL","BIH",
    "BWA","BRA","BRN","BGR","BFA","BDI","CPV","KHM","CMR","CAN","CAF",
    "TCD","CHL","CHN","COL","COM","COD","COG","CRI","HRV","CUB","CYP",
    "CZE","DNK","DJI","DMA","DOM","ECU","EGY","SLV","GNQ","ERI","EST",
    "SWZ","ETH","FJI","FIN","FRA","GAB","GMB","GEO","DEU","GHA","GRC",
    "GRD","GTM","GIN","GNB","GUY","HTI","HND","HUN","ISL","IND","IDN",
    "IRN","IRQ","IRL","ISR","ITA","JAM","JPN","JOR","KAZ","KEN","KIR",
    "PRK","KOR","KWT","KGZ","LAO","LVA","LBN","LSO","LBR","LBY","LIE",
    "LTU","LUX","MDG","MWI","MYS","MDV","MLI","MLT","MHL","MRT","MUS",
    "MEX","FSM","MDA","MCO","MNG","MNE","MAR","MOZ","MMR","NAM","NRU",
    "NPL","NLD","NZL","NIC","NER","NGA","MKD","NOR","OMN","PAK","PLW",
    "PAN","PNG","PRY","PER","PHL","POL","PRT","QAT","ROU","RUS","RWA",
    "KNA","LCA","VCT","WSM","SMR","STP","SAU","SEN","SRB","SYC","SLE",
    "SGP","SVK","SVN","SLB","SOM","ZAF","SSD","ESP","LKA","SDN","SUR",
    "SWE","CHE","SYR","TJK","TZA","THA","TLS","TGO","TON","TTO","TUN",
    "TUR","TKM","TUV","UGA","UKR","ARE","GBR","USA","URY","UZB","VUT",
    "VEN","VNM","YEM","ZMB","ZWE",
    # Speciální / fiktivní ICAO kódy
    "UNO","UNA","UNK","XXA","XXB","XXC","XXX","UTO","D",
    # Cestovní doklad Tchaj-wanu
    "TWN",
})

# Dvojice OCR záměn: (chybně přečtený znak, správný znak).
# Je zahrnuto jen O->C (ne C->O), protože OCR v MRZ fontu zaměňuje 'O' za 'C',
# ale opačně ne; ponechání opačného směru by vytvářelo falešné kandidáty
# pro kódy, které legitimně začínají na C.
_OCR_ALPHA_CONFUSIONS: list[tuple[str, str]] = [
    ("0", "O"), ("1", "I"), ("2", "Z"), ("5", "S"),
    ("6", "G"), ("8", "B"), ("L", "Z"), ("I", "L"),
    ("O", "C"),
]


def correct_country_code(code: str) -> str:
    """
    Ověří třípísmenný MRZ kód země/národnosti proti seznamu ICAO.
    Pokud kód není nalezen, zkusí na jednotlivých pozicích jednopísmenné
    náhrady podle typických OCR záměn, dokud nevznikne známý kód.

    Vrací opravený kód, nebo původní řetězec, pokud se shoda nenajde.

    Příklady:
        "CLE" -> "CZE"   (L chybně přečteno jako Z)
        "OZE" -> "CZE"   (O chybně přečteno jako C)
        "CZE" -> "CZE"   (už je validní, vrací se beze změny)
    """
    code = code.upper().strip()
    if code in _ICAO_CODES:
        return code

    for pos in range(len(code)):
        for wrong, right in _OCR_ALPHA_CONFUSIONS:
            if code[pos] == wrong:
                candidate = code[:pos] + right + code[pos + 1:]
                if candidate in _ICAO_CODES:
                    return candidate

    return code


def clean_name(raw: str) -> str:
    """
    Odstraní OCR artefakty výplně z pole jména/příjmení.

    MRZ výplňový znak '<' bývá po konci skutečného jména často chybně přečten
    jako mezery nebo jednotlivá písmena (K, S, X, Z, I). Funkce ponechá
    pouze tokeny s alespoň 2 písmeny a skončí na prvním krátkém/šumovém tokenu,
    který následuje po validním jmenném tokenu.

    Příklady:
        "MATYAS        K  KKKKKKKKKKSKKKS"   → "MATYAS"
        "ANNA MARIA C"                       → "ANNA MARIA"
        "ERIKSSON"                           → "ERIKSSON"
    """
    tokens = raw.split()
    clean_tokens: list[str] = []
    for t in tokens:
        if len(t) >= 2 and t.isalpha():
            clean_tokens.append(t)
        elif clean_tokens:
            break  # první šumový token po reálném obsahu = konec jména
    return " ".join(clean_tokens)
