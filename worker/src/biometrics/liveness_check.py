"""
Jednoduchá liveness kontrola pomocí odhadu pozice hlavy z MediaPipe FaceLandmarker.

Strategie: analyzuje 4+ obrázků obličeje, u každého odhadne pozici hlavy
(yaw/pitch/roll) a ověří, že mezi snímky je dostatečná změna orientace.
Skutečný živý člověk při pokynu podívat se různými směry vytvoří měřitelný
úhlový rozptyl; statická fotka nebo výtisk typicky ne.

Model (face_landmarker.task, ~1 MB) se při prvním použití automaticky stáhne do složky /models/ a poté se načítá lokálně.

Použití:
    from liveness_check import check_liveness
    result = check_liveness(["left.jpg", "right.jpg", "up.jpg", "center.jpg"])
    print(result.is_alive, result.confidence)
"""
from __future__ import annotations

import logging
import urllib.request
from dataclasses import dataclass, field
from pathlib import Path
from typing import Optional

import cv2
import mediapipe as mp
import numpy as np
from mediapipe.tasks.python import vision as mp_vision
from mediapipe.tasks import python as mp_python

from ..errors import WorkerError

logger = logging.getLogger(__name__)

# ---------------------------------------------------------------------------
# Stažení modelu
# ---------------------------------------------------------------------------
# Model MediaPipe Face Landmarker + použití v Python API:
# https://ai.google.dev/edge/mediapipe/solutions/vision/face_landmarker/python
_MODEL_URL = (
    "https://storage.googleapis.com/mediapipe-models/"
    "face_landmarker/face_landmarker/float16/1/face_landmarker.task"
)
_MODEL_PATH = Path(__file__).parent.parent.parent / "models" / "face_landmarker.task"


def _ensure_model() -> str:
    """Stáhne model FaceLandmarker, pokud ještě není v lokální cache."""
    _MODEL_PATH.parent.mkdir(parents=True, exist_ok=True)
    if not _MODEL_PATH.exists():
        logger.info("Downloading MediaPipe FaceLandmarker model to %s …", _MODEL_PATH)
        print(f"Downloading face_landmarker.task model to {_MODEL_PATH} …")
        # Pomocná funkce pro stažení ze standardní knihovny Pythonu (urlretrieve):
        # https://docs.python.org/3/library/urllib.request.html
        urllib.request.urlretrieve(_MODEL_URL, _MODEL_PATH)
        logger.info("Model downloaded (%d bytes)", _MODEL_PATH.stat().st_size)
    return str(_MODEL_PATH)


# ---------------------------------------------------------------------------
# 6bodový solvePnP záložní postup (když není dostupná transformační matice)
# ---------------------------------------------------------------------------
_LANDMARK_INDICES = [1, 152, 33, 263, 61, 291]

_MODEL_POINTS_3D = np.array([
    (0.0,      0.0,    0.0),    # 1:   špička nosu
    (0.0,   -330.0,  -65.0),    # 152: brada
    (-225.0,  170.0, -135.0),   # 33:  pravý vnější koutek oka
    ( 225.0,  170.0, -135.0),   # 263: levý vnější koutek oka
    (-150.0, -150.0, -125.0),   # 61:  pravý koutek úst
    ( 150.0, -150.0, -125.0),   # 291: levý koutek úst
], dtype=np.float64)


def _pose_from_transform_matrix(mat4x4) -> tuple[float, float, float]:
    """Z 4x4 transformační matice obličeje vyčte (yaw, pitch, roll) ve stupních."""
    m = np.array(mat4x4.data, dtype=np.float64).reshape(4, 4)
    rot = m[:3, :3]
    # Pomocná funkce pro Eulerovu dekompozici:
    # https://docs.opencv.org/4.x/d9/d0c/group__calib3d.html
    angles, *_ = cv2.RQDecomp3x3(rot)
    return float(angles[1]), float(angles[0]), float(angles[2])  # yaw, pitch, roll


def _pose_from_landmarks(
    image_shape: tuple[int, int], landmarks: list
) -> tuple[float, float, float]:
    """Záložní varianta: odhadne pozici hlavy přes solvePnP z 6 landmarků."""
    h, w = image_shape
    focal = float(w)
    cam = np.array([[focal, 0, w / 2.0], [0, focal, h / 2.0], [0, 0, 1]], dtype=np.float64)
    dist = np.zeros((4, 1), dtype=np.float64)
    pts2d = np.array(
        [(landmarks[i].x * w, landmarks[i].y * h) for i in _LANDMARK_INDICES],
        dtype=np.float64,
    )
    # Referenční dokumentace k odhadu pozice přes PnP:
    # https://docs.opencv.org/4.x/d5/d1f/calib3d_solvePnP.html
    ok, rvec, _ = cv2.solvePnP(
        _MODEL_POINTS_3D, pts2d, cam, dist, flags=cv2.SOLVEPNP_ITERATIVE
    )
    if not ok:
        raise WorkerError("POSE_ESTIMATION_FAILED")
    # Rodrigues + RQ dekompozice v modulu calib3d:
    # https://docs.opencv.org/4.x/d9/d0c/group__calib3d.html
    rmat, _ = cv2.Rodrigues(rvec)
    angles, *_ = cv2.RQDecomp3x3(rmat)
    return float(angles[1]), float(angles[0]), float(angles[2])  # yaw, pitch, roll


# ---------------------------------------------------------------------------
# Laditelné prahy
# ---------------------------------------------------------------------------
MIN_IMAGES_WITH_FACE = 3     # počet potřebných detekcí
MIN_YAW_RANGE_DEG    = 25.0  # minimální rozptyl yaw (stupně) napříč všemi obrázky
MIN_DISTINCT_DIRS    = 2     # minimální počet různých zón (left/right/up/down/center) napříč všemi obrázky
DIRECTION_ZONE_DEG   = 20.0  # poloviční šířka zón "left" / "right" / "up" / "down"


# ---------------------------------------------------------------------------
# Datové struktury
# ---------------------------------------------------------------------------

@dataclass
class FacePoseResult:
    """Výsledek odhadu pozice hlavy pro jeden obrázek."""
    image_index: int
    image_path:  str
    face_detected: bool
    yaw:       Optional[float] = None   # kladné = pohled doprava z pohledu osoby
    pitch:     Optional[float] = None   # kladné = pohled nahoru
    roll:      Optional[float] = None   # náklon hlavy
    direction: Optional[str]  = None    # směr hlavy: left / right / up / down / center
    error:     Optional[str]  = None


@dataclass
class LivenessResult:
    """Agregovaný výsledek liveness rozhodnutí."""
    is_alive:           bool
    confidence:         float          # 0.0 - 1.0
    reason:             str
    images_analyzed:    int
    images_with_face:   int
    yaw_range_deg:      float
    pitch_range_deg:    float
    distinct_directions: list[str]
    per_image:          list[FacePoseResult] = field(default_factory=list)


# ---------------------------------------------------------------------------
# Interní pomocné funkce
# ---------------------------------------------------------------------------

def _classify_direction(yaw: float, pitch: float) -> str:
    if abs(yaw) >= abs(pitch):
        if yaw >  DIRECTION_ZONE_DEG: return "right"
        if yaw < -DIRECTION_ZONE_DEG: return "left"
    else:
        if pitch >  DIRECTION_ZONE_DEG: return "up"
        if pitch < -DIRECTION_ZONE_DEG: return "down"
    return "center"


# ---------------------------------------------------------------------------
# Veřejné API
# ---------------------------------------------------------------------------

def check_liveness(image_paths: list[str | Path]) -> LivenessResult:
    """
    Analyzuje pozici hlavy napříč více snímky obličeje a rozhodne, zda jde
    pravděpodobně o živou osobu.

    Parametry
    ----------
    image_paths : seznam cest k obrázkům obličeje (doporučeno 4+)

    Návratová hodnota
    -------
    LivenessResult
        .is_alive    - True, pokud diverzita pozic odpovídá živé osobě
        .confidence  - float 0-1 kombinující rozptyl yaw/pitch a pokrytí směrů
        .per_image   - detaily po jednotlivých snímcích pro ladění / UI
    """
    n = len(image_paths)
    if n < 2:
        return LivenessResult(
            is_alive=False, confidence=0.0,
            reason="TOO_FEW_IMAGES",
            images_analyzed=n, images_with_face=0,
            yaw_range_deg=0.0, pitch_range_deg=0.0, distinct_directions=[],
        )

    model_path = _ensure_model()

    # Konfigurace FaceLandmarkerOptions (num_faces, min_face_detection_confidence,
    # output_facial_transformation_matrixes):
    # https://ai.google.dev/edge/mediapipe/solutions/vision/face_landmarker/python
    options = mp_vision.FaceLandmarkerOptions(
        base_options=mp_python.BaseOptions(model_asset_path=model_path),
        output_face_blendshapes=False,
        output_facial_transformation_matrixes=True,
        num_faces=1,
        min_face_detection_confidence=0.5,
        min_face_presence_confidence=0.5,
    )

    per_image: list[FacePoseResult] = []

    with mp_vision.FaceLandmarker.create_from_options(options) as landmarker:
        for idx, path in enumerate(image_paths):
            path_str = str(path)
            res = FacePoseResult(image_index=idx, image_path=path_str, face_detected=False)
            try:
                img_bgr = cv2.imread(path_str)
                if img_bgr is None:
                    res.error = "IMAGE_READ_ERROR"
                    per_image.append(res)
                    continue

                # OpenCV standardně používá BGR; MediaPipe očekává vstup SRGB/RGB.
                # https://docs.opencv.org/4.x/d8/d01/group__imgproc__color__conversions.html
                img_rgb = cv2.cvtColor(img_bgr, cv2.COLOR_BGR2RGB)
                mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=img_rgb)
                detection = landmarker.detect(mp_image)

                if not detection.face_landmarks:
                    res.error = "FACE_NOT_DETECTED"
                    per_image.append(res)
                    continue

                res.face_detected = True

                # Preferovat transformační matici (nejpřesnější); záložně solvePnP
                if detection.facial_transformation_matrixes:
                    yaw, pitch, roll = _pose_from_transform_matrix(
                        detection.facial_transformation_matrixes[0]
                    )
                else:
                    h, w = img_bgr.shape[:2]
                    yaw, pitch, roll = _pose_from_landmarks(
                        (h, w), detection.face_landmarks[0]
                    )

                res.yaw       = round(yaw,   1)
                res.pitch     = round(pitch, 1)
                res.roll      = round(roll,  1)
                res.direction = _classify_direction(yaw, pitch)

            except Exception as exc:
                res.error = exc.code if isinstance(exc, WorkerError) else "POSE_ESTIMATION_ERROR"
                logger.warning("Pose estimation failed for %s: %s", path_str, exc)

            per_image.append(res)

    # ------------------------------------------------------------------
    # Agregace
    # ------------------------------------------------------------------
    detected   = [r for r in per_image if r.face_detected]
    n_detected = len(detected)

    if n_detected < MIN_IMAGES_WITH_FACE:
        return LivenessResult(
            is_alive=False, confidence=0.0,
            reason="TOO_FEW_FACES_DETECTED",
            images_analyzed=n, images_with_face=n_detected,
            yaw_range_deg=0.0, pitch_range_deg=0.0, distinct_directions=[],
            per_image=per_image,
        )

    yaws        = [r.yaw   for r in detected]
    pitches     = [r.pitch for r in detected]
    yaw_range   = float(max(yaws)    - min(yaws))
    pitch_range = float(max(pitches) - min(pitches))
    directions  = sorted(set(r.direction for r in detected))

    yaw_score   = min(1.0, yaw_range   / 60.0)   # plný rozptyl 60° -> 1.0
    pitch_score = min(1.0, pitch_range / 40.0)   # plný rozptyl 40° -> 1.0
    dir_score   = min(1.0, len(directions) / 3.0) # 3 různé zóny -> 1.0
    confidence  = round(yaw_score * 0.5 + pitch_score * 0.2 + dir_score * 0.3, 3)

    is_alive = (
        yaw_range  >= MIN_YAW_RANGE_DEG
        and len(directions) >= MIN_DISTINCT_DIRS
        and n_detected      >= MIN_IMAGES_WITH_FACE
    )

    reason = "LIVENESS_PASSED" if is_alive else "LIVENESS_FAILED"

    return LivenessResult(
        is_alive=is_alive,
        confidence=confidence,
        reason=reason,
        images_analyzed=n,
        images_with_face=n_detected,
        yaw_range_deg=round(yaw_range, 1),
        pitch_range_deg=round(pitch_range, 1),
        distinct_directions=directions,
        per_image=per_image,
    )
