"""
Porovnání obličejů: ověří, že selfie odpovídá portrétu na dokladu.

Používá detektor obličejů YuNet a rozpoznávač SFace z OpenCV - oba jsou
nativní pro OpenCV, bez potřeby TensorFlow/PyTorch.

Soubory modelů (stahují se automaticky při prvním použití, ukládají se do src/models/):
  face_detection_yunet_2023mar.onnx   ~227 KB
  face_recognition_sface_2021dec.onnx ~37 MB

Rozhodovací metrika: kosinová podobnost embeddingů SFace.
  skóre >= threshold -> stejná osoba (OpenCV doporučený threshold: 0.363)

Použití:
    from face_comparison import compare_faces
    result = compare_faces("id_card.jpg", "selfie.jpg")
    print(result.match, result.confidence, result.reason)
"""
from __future__ import annotations

import logging
import ssl
import urllib.request
from dataclasses import dataclass
from pathlib import Path
from typing import Optional

import cv2
import numpy as np

logger = logging.getLogger(__name__)

# ---------------------------------------------------------------------------
# Cesty k modelům a automatické stažení
# ---------------------------------------------------------------------------
_MODELS_DIR = Path(__file__).parent.parent.parent / "models"

_YUNET_PATH = _MODELS_DIR / "face_detection_yunet_2023mar.onnx"
_SFACE_PATH = _MODELS_DIR / "face_recognition_sface_2021dec.onnx"

_YUNET_URL = (
    "https://github.com/opencv/opencv_zoo/raw/main/models/"
    "face_detection_yunet/face_detection_yunet_2023mar.onnx"
)
_SFACE_URL = (
    "https://github.com/opencv/opencv_zoo/raw/main/models/"
    "face_recognition_sface/face_recognition_sface_2021dec.onnx"
)
# Odkazy na modely:
# YuNet:  https://raw.githubusercontent.com/opencv/opencv_zoo/main/models/face_detection_yunet/README.md
# SFace:  https://github.com/opencv/opencv_zoo/tree/main/models/face_recognition_sface


def _download(url: str, dest: Path) -> None:
    """Stáhne soubor; v případě potřeby obejde SSL zvláštnosti na macOS."""
    logger.info("Downloading %s → %s", url, dest)
    print(f"Downloading {dest.name} …")
    try:
        # Pomocná funkce pro stažení ze standardní knihovny Pythonu (urlretrieve):
        # https://docs.python.org/3/library/urllib.request.html
        urllib.request.urlretrieve(url, dest)
    except urllib.error.URLError:
        # Python na macOS může postrádat systémový CA bundle; lokálně vypnout verifikaci
        ctx = ssl.create_default_context()
        ctx.check_hostname = False
        ctx.verify_mode = ssl.CERT_NONE
        with urllib.request.urlopen(url, context=ctx) as resp:  # noqa: S310
            dest.write_bytes(resp.read())


def _ensure_models() -> tuple[str, str]:
    _MODELS_DIR.mkdir(parents=True, exist_ok=True)
    if not _YUNET_PATH.exists():
        _download(_YUNET_URL, _YUNET_PATH)
    if not _SFACE_PATH.exists():
        _download(_SFACE_URL, _SFACE_PATH)
    return str(_YUNET_PATH), str(_SFACE_PATH)


# ---------------------------------------------------------------------------
# Prah kosinové podobnosti (z OpenCV SFace paper / zoo README)
# ---------------------------------------------------------------------------
# Prahy odpovídají tabulce v OpenCV DNN face tutorialu:
# https://docs.opencv.org/4.x/d0/dd4/tutorial_dnn_face.html
COSINE_THRESHOLD = 0.363   # skóre >= tato hodnota -> stejná osoba
L2_THRESHOLD     = 1.128   # skóre <= tato hodnota -> stejná osoba (alternativní metrika)


# ---------------------------------------------------------------------------
# Datové struktury
# ---------------------------------------------------------------------------

@dataclass
class FaceComparisonResult:
    """Výsledek jednoho porovnání portrétu z dokladu proti selfie."""
    match:                  bool
    confidence:             float   # normalizováno na 0–1 podle cosine skóre
    cosine_similarity:      float   # surové skóre; rozsah přibližně −1 až 1
    threshold:              float   # použitá rozhodovací hranice
    reason:                 str
    document_face_detected: bool = False
    selfie_face_detected:   bool = False


# ---------------------------------------------------------------------------
# Interní pomocné funkce
# ---------------------------------------------------------------------------

def _load_bgr(path: str | Path) -> Optional[np.ndarray]:
    img = cv2.imread(str(path))
    return img  # None, pokud soubor neexistuje / nejde přečíst


def _detect_best_face(
    detector: cv2.FaceDetectorYN,
    image: np.ndarray,
) -> Optional[np.ndarray]:
    """
    Spustí YuNet nad *image* a vrátí detekci s nejvyšší důvěrou,
    nebo None, pokud se obličej nenajde.

    Každý řádek detekce má 15 hodnot:
      [x, y, w, h,  re_x, re_y,  le_x, le_y,  nt_x, nt_y,
       rcm_x, rcm_y,  lcm_x, lcm_y,  score]

    Viz OpenCV DNN face tutorial (rozložení výstupu FaceDetectorYN).
    """
    h, w = image.shape[:2]
    detector.setInputSize((w, h))
    _, faces = detector.detect(image)
    if faces is None or len(faces) == 0:
        return None
    return faces[int(np.argmax(faces[:, -1]))]  # řádek s nejvyšším skóre


def _align_crop(
    recognizer: cv2.FaceRecognizerSF,
    image: np.ndarray,
    face_row: np.ndarray,
) -> np.ndarray:
    """Pomocí SFace zarovná a ořízne výřez obličeje 112x112.

    Použití FaceRecognizerSF pro align/match:
    https://docs.opencv.org/4.x/d0/dd4/tutorial_dnn_face.html
    """
    face_row_2d = face_row.reshape(1, -1)
    return recognizer.alignCrop(image, face_row_2d)


def _cosine_to_confidence(cosine: float, threshold: float) -> float:
    """
    Převede kosinovou podobnost na confidence skóre v rozsahu 0-1.

    Hodnoty >= threshold mapuje do [0.5, 1.0], nižší hodnoty do [0.0, 0.5).
    """
    if cosine >= threshold:
        # interpolace od threshold -> 1.0 mapovaná na 0.5 -> 1.0
        return 0.5 + 0.5 * (cosine - threshold) / (1.0 - threshold + 1e-9)
    else:
        # interpolace od 0 -> threshold mapovaná na 0.0 -> 0.5
        return 0.5 * max(0.0, cosine) / (threshold + 1e-9)


# ---------------------------------------------------------------------------
# Veřejné API
# ---------------------------------------------------------------------------

def compare_faces(
    document_path: str | Path,
    selfie_path: str | Path,
    threshold: float = COSINE_THRESHOLD,
) -> FaceComparisonResult:
    """
    Porovná portrét na dokladu proti selfie fotografii.

    Parametry
    ----------
    document_path : cesta k obrázku občanky nebo pasu
    selfie_path   : cesta k živému selfie / fotografii z liveness kontroly
    threshold     : rozhodovací hranice kosinové podobnosti (výchozí 0.363)

    Návratová hodnota
    -------
    FaceComparisonResult
        .match       - True, pokud jsou obličeje vyhodnoceny jako stejná osoba
        .confidence  - float 0-1 (vyšší = jistější shoda)
        .cosine_similarity - surové kosinové skóre SFace
        .reason      - textové vysvětlení výsledku
    """
    yunet_path, sface_path = _ensure_models()

    # Načtení obrázků
    doc_img  = _load_bgr(document_path)
    self_img = _load_bgr(selfie_path)

    if doc_img is None:
        return FaceComparisonResult(
            match=False, confidence=0.0, cosine_similarity=0.0,
            threshold=threshold,
            reason="DOCUMENT_IMAGE_READ_ERROR",
        )
    if self_img is None:
        return FaceComparisonResult(
            match=False, confidence=0.0, cosine_similarity=0.0,
            threshold=threshold,
            reason="SELFIE_IMAGE_READ_ERROR",
        )

    # Inicializace detektoru se zástupnou vstupní velikostí (pak se mění pro každý obrázek)
    detector   = cv2.FaceDetectorYN.create(yunet_path, "", (320, 320),
                                           score_threshold=0.6, nms_threshold=0.3)
    recognizer = cv2.FaceRecognizerSF.create(sface_path, "")

    # Detekce obličejů
    doc_face  = _detect_best_face(detector, doc_img)
    self_face = _detect_best_face(detector, self_img)

    doc_detected  = doc_face  is not None
    self_detected = self_face is not None

    if not doc_detected:
        return FaceComparisonResult(
            match=False, confidence=0.0, cosine_similarity=0.0,
            threshold=threshold,
            reason="DOCUMENT_FACE_NOT_DETECTED",
            document_face_detected=False,
            selfie_face_detected=self_detected,
        )
    if not self_detected:
        return FaceComparisonResult(
            match=False, confidence=0.0, cosine_similarity=0.0,
            threshold=threshold,
            reason="SELFIE_FACE_NOT_DETECTED",
            document_face_detected=True,
            selfie_face_detected=False,
        )

    # Zarovnání, ořez a embedding
    doc_aligned  = _align_crop(recognizer, doc_img,  doc_face)
    self_aligned = _align_crop(recognizer, self_img, self_face)

    doc_feat  = recognizer.feature(doc_aligned)
    self_feat = recognizer.feature(self_aligned)

    cosine = float(recognizer.match(doc_feat, self_feat,
                                    cv2.FaceRecognizerSF_FR_COSINE))
    match  = cosine >= threshold
    conf   = round(_cosine_to_confidence(cosine, threshold), 3)

    reason = "FACES_MATCH" if match else "FACES_NO_MATCH"

    return FaceComparisonResult(
        match=match,
        confidence=conf,
        cosine_similarity=round(cosine, 4),
        threshold=threshold,
        reason=reason,
        document_face_detected=True,
        selfie_face_detected=True,
    )
