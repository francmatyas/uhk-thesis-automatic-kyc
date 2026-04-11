import { useCallback, useEffect, useRef, useState } from 'react'
import * as faceapi from 'face-api.js'

// Modely se standardně načítají z /public/models/face-api/.
// V případě potřeby přepište pomocí env proměnné VITE_FACE_API_MODELS_URL.
const MODELS_URL =
  import.meta.env.VITE_FACE_API_MODELS_URL ?? '/models/face-api'

let loadPromise = null

function ensureModelsLoaded() {
  if (loadPromise) return loadPromise
  loadPromise = Promise.all([
    faceapi.nets.tinyFaceDetector.loadFromUri(MODELS_URL),
    faceapi.nets.faceLandmark68TinyNet.loadFromUri(MODELS_URL),
  ])
  return loadPromise
}

/**
 * Odhadne natočení hlavy z 68bodových obličejových landmarků (surový/nepřevrácený prostor kamery).
 *
 * Yaw > 0  → uživatel otočil hlavu na SVOU levou stranu  (nos se posunul doprava v kameře)
 * Yaw < 0  → uživatel otočil hlavu na SVOU pravou stranu (nos se posunul doleva v kameře)
 * Pitch    → 0..1, nižší hodnota znamená pohled nahoru
 */
function computeHeadPose(landmarks) {
  const p = landmarks.positions

  const avgPts = (indices) => {
    const pts = indices.map((i) => p[i])
    return {
      x: pts.reduce((s, q) => s + q.x, 0) / pts.length,
      y: pts.reduce((s, q) => s + q.y, 0) / pts.length,
    }
  }

  const noseTip = p[30]
  const leftEye = avgPts([36, 37, 38, 39, 40, 41])
  const rightEye = avgPts([42, 43, 44, 45, 46, 47])
  const chin = p[8]

  const eyeMid = {
    x: (leftEye.x + rightEye.x) / 2,
    y: (leftEye.y + rightEye.y) / 2,
  }

  // vzdálenost očí v prostoru kamery (pravé oko má menší x, protože obraz je zrcadlený)
  const eyeDist = Math.abs(rightEye.x - leftEye.x)
  if (eyeDist < 15) return null // obličej je moc malý nebo téměř z profilu

  const yaw = (noseTip.x - eyeMid.x) / eyeDist
  const faceH = chin.y - eyeMid.y
  const pitch = faceH > 0 ? (noseTip.y - eyeMid.y) / faceH : 0.5

  return { yaw, pitch }
}

/**
 * Vrátí true, pokud je detekovaný bounding box obličeje vystředěný a dostatečně velký
 * v rámci video snímku. Odpovídá přerušované oválné pomůcce v UI
 * (rx≈34 %, ry≈42 % snímku od středu).
 */
function isFaceCentered(box, videoWidth, videoHeight) {
  if (!videoWidth || !videoHeight) return false

  const faceCenterX = box.x + box.width / 2
  const faceCenterY = box.y + box.height / 2

  const offsetX = Math.abs(faceCenterX - videoWidth / 2) / videoWidth
  const offsetY = Math.abs(faceCenterY - videoHeight / 2) / videoHeight

  // Střed obličeje musí být v každé ose do 20 % od středu snímku
  if (offsetX > 0.20 || offsetY > 0.20) return false

  // Obličej musí zabírat alespoň 25 % šířky snímku (nesmí být moc daleko)
  if (box.width / videoWidth < 0.25) return false

  return true
}

function poseMatchesPosition(pose, positionKey) {
  if (!pose) return false
  const { yaw, pitch } = pose

  switch (positionKey) {
    case 'center':
      // Pitch se záměrně nekontroluje: kamery notebooků bývají nad úrovní očí,
      // takže přirozený pohled rovně už dává nízkou hodnotu pitch.
      return Math.abs(yaw) < 0.20
    case 'left': // uživatel otočí hlavu doleva → yaw > 0 v prostoru kamery
      return yaw > 0.25
    case 'right': // uživatel otočí hlavu doprava → yaw < 0 v prostoru kamery
      return yaw < -0.25
    case 'up':
      // Vyžaduj výraznější náklon (< 0.15), aby přirozený úhel kamery notebooku
      // (který už sám dává nízký pitch) nespouštěl podmínku falešně.
      return pitch < 0.15 && Math.abs(yaw) < 0.30
    default:
      return false
  }
}

// Kolik po sobě jdoucích odpovídajících snímků je potřeba před automatickým pořízením.
// Inference face-api trvá na CPU ~400–500 ms na snímek, takže 3 snímky ≈ držení 1–1.5 s.
const HOLD_FRAMES = 3

/**
 * @param {object} params
 * @param {React.RefObject<HTMLVideoElement>} params.videoRef
 * @param {string}  params.requiredPosition  - 'center' | 'left' | 'right' | 'up'
 * @param {() => void} params.onCapture      - zavolá se jednou po udržení pozice
 * @param {boolean} params.isActive          - spouští/zastavuje detekční smyčku
 *
 * @returns {{ status: string, holdProgress: number }}
 *   status: 'loading' | 'error' | 'no-face' | 'wrong-pose' | 'correct-pose' | 'captured'
 *   holdProgress: 0..1 (průběh držení)
 */
export function useFaceDetection({ videoRef, requiredPosition, onCapture, isActive }) {
  const [status, setStatus] = useState('loading')
  const [holdProgress, setHoldProgress] = useState(0)

  const rafRef = useRef(null)
  const consecutiveRef = useRef(0)
  const capturedRef = useRef(false)
  const activeRef = useRef(isActive)
  const requiredRef = useRef(requiredPosition)

  // Udržuj stabilní reference synchronizované
  useEffect(() => {
    activeRef.current = isActive
  }, [isActive])

  // Callback onCapture je uložený v ref, aby nikdy nezpůsobil restart detekční
  // smyčky při re-renderu rodiče nebo posunu indexu požadované pozice.
  const onCaptureRef = useRef(onCapture)
  useEffect(() => {
    onCaptureRef.current = onCapture
  }, [onCapture])

  // Resetuje počítadla při každé změně požadované pozice
  useEffect(() => {
    requiredRef.current = requiredPosition
    consecutiveRef.current = 0
    capturedRef.current = false
    setHoldProgress(0)
    // Při posunu pozice vždy hned vyčisti stavy captured/wrong-pose
    setStatus((prev) => (prev === 'loading' || prev === 'error' ? prev : 'no-face'))
  }, [requiredPosition])

  const detect = useCallback(async () => {
    const video = videoRef.current
    if (!video || !activeRef.current) return

    // Když už je zachyceno, drž smyčku naživu (idle), aby se automaticky
    // obnovila po posunu requiredPosition a resetu capturedRef v rodiči.
    if (capturedRef.current) {
      rafRef.current = requestAnimationFrame(detect)
      return
    }

    if (video.readyState < 2) {
      rafRef.current = requestAnimationFrame(detect)
      return
    }

    try {
      const result = await faceapi
        .detectSingleFace(
          video,
          new faceapi.TinyFaceDetectorOptions({ inputSize: 320, scoreThreshold: 0.3 }),
        )
        .withFaceLandmarks(true)

      if (!activeRef.current) return

      // Pozice se během detekce resetovala — tento výsledek zahodit
      if (capturedRef.current) {
        rafRef.current = requestAnimationFrame(detect)
        return
      }

      if (!result) {
        consecutiveRef.current = 0
        setHoldProgress(0)
        setStatus('no-face')
      } else {
        const pose = computeHeadPose(result.landmarks)
        const centered = isFaceCentered(result.detection.box, video.videoWidth, video.videoHeight)
        const matches = centered && poseMatchesPosition(pose, requiredRef.current)

        if (matches) {
          consecutiveRef.current++
          const progress = Math.min(consecutiveRef.current / HOLD_FRAMES, 1)
          setHoldProgress(progress)
          setStatus(progress >= 1 ? 'captured' : 'correct-pose')

          if (consecutiveRef.current >= HOLD_FRAMES) {
            capturedRef.current = true
            onCaptureRef.current()
            // Smyčka výše pokračuje v idle režimu do posunu pozice
          }
        } else {
          consecutiveRef.current = 0
          setHoldProgress(0)
          setStatus('wrong-pose')
        }
      }
    } catch {
      // Ignoruj jednotlivé chyby snímků - detekce pokračuje
    }

    rafRef.current = requestAnimationFrame(detect)
  }, [videoRef]) // stabilní: onCapture přes ref, requiredPosition přes ref

  // Modely načti jednou a smyčku spouštěj podle isActive
  useEffect(() => {
    if (!isActive) {
      if (rafRef.current) {
        cancelAnimationFrame(rafRef.current)
        rafRef.current = null
      }
      return
    }

    setStatus('loading')
    consecutiveRef.current = 0
    capturedRef.current = false

    ensureModelsLoaded()
      .then(() => {
        if (!activeRef.current) return
        setStatus('no-face')
        rafRef.current = requestAnimationFrame(detect)
      })
      .catch(() => {
        setStatus('error')
      })

    return () => {
      if (rafRef.current) {
        cancelAnimationFrame(rafRef.current)
        rafRef.current = null
      }
    }
  }, [isActive, detect])

  return { status, holdProgress }
}
