import { useCallback, useEffect, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { ScanFace, ArrowLeft, ArrowRight, ArrowUp } from 'lucide-react'
import StepShell from '@/components/flow/StepShell'
import { Button } from '@/components/ui/button'
import { useFaceDetection } from '@/hooks/useFaceDetection'

const POSITIONS = [
  { key: 'center', Icon: ScanFace },
  { key: 'left', Icon: ArrowLeft },
  { key: 'right', Icon: ArrowRight },
  { key: 'up', Icon: ArrowUp },
]

// Obvod kružnice pro r=114 v SVG 236×236: 2π×114 ≈ 716
const RING_CIRCUMFERENCE = 716

const STATUS_RING_COLOR = {
  loading: '#94a3b8',
  error: '#f87171',
  'no-face': '#94a3b8',
  'wrong-pose': '#fbbf24',
  'correct-pose': '#4ade80',
  captured: '#4ade80',
}


function LivenessStep({
  progress,
  onSubmit,
  error,
  progressSteps,
  progressIndex,
  currentStepProgress,
}) {
  const { t } = useTranslation()
  const videoRef = useRef(null)
  const canvasRef = useRef(null)
  const streamRef = useRef(null)

  const [stage, setStage] = useState('intro')
  const [currentIndex, setCurrentIndex] = useState(0)
  const [frames, setFrames] = useState(Array(POSITIONS.length).fill(null))
  const [cameraError, setCameraError] = useState(null)
  const [isFlashing, setIsFlashing] = useState(false)

  // ── správa kamery ───────────────────────────────────────────────────────────
  const stopCamera = useCallback(() => {
    if (streamRef.current) {
      streamRef.current.getTracks().forEach((t) => t.stop())
      streamRef.current = null
    }
  }, [])

  const startCamera = useCallback(async () => {
    setCameraError(null)
    if (!navigator.mediaDevices?.getUserMedia) {
      setCameraError(t('step.liveness.cameraError'))
      return
    }
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: 'user', width: { ideal: 640 }, height: { ideal: 640 } },
      })
      streamRef.current = stream
      if (videoRef.current) videoRef.current.srcObject = stream
    } catch {
      setCameraError(t('step.liveness.cameraError'))
    }
  }, [t])

  useEffect(() => {
    if (stage === 'capture') startCamera()
    else stopCamera()
  }, [stage, startCamera, stopCamera])

  useEffect(() => () => stopCamera(), [stopCamera])

  useEffect(() => {
    if (stage !== 'submitting') return
    onSubmit(frames.filter(Boolean))
  }, [stage]) // eslint-disable-line react-hooks/exhaustive-deps

  // ── snímání snímků ──────────────────────────────────────────────────────────
  // Volá detekční hook, jakmile je požadovaná pozice dostatečně dlouho držena.
  const captureFrame = useCallback(() => {
    const video = videoRef.current
    const canvas = canvasRef.current
    if (!video || !canvas || !video.videoWidth) return

    canvas.width = video.videoWidth
    canvas.height = video.videoHeight
    canvas.getContext('2d').drawImage(video, 0, 0)

    // Krátký záblesk pro potvrzení pořízení snímku
    setIsFlashing(true)
    setTimeout(() => setIsFlashing(false), 300)

    canvas.toBlob(
      (blob) => {
        if (!blob) return
        const file = new File([blob], `liveness_${POSITIONS[currentIndex].key}.jpg`, {
          type: 'image/jpeg',
        })

        setFrames((prev) => {
          const next = [...prev]
          next[currentIndex] = file
          return next
        })

        // Krátké zpoždění, aby uživatel viděl záblesk před přechodem na další pozici
        setTimeout(() => {
          if (currentIndex >= POSITIONS.length - 1) {
            setStage('submitting')
          } else {
            setCurrentIndex((prev) => prev + 1)
          }
        }, 400)
      },
      'image/jpeg',
      0.9,
    )
  }, [currentIndex])

  // ── Detekce obličeje ────────────────────────────────────────────────────────
  const { status: detectionStatus, holdProgress } = useFaceDetection({
    videoRef,
    requiredPosition: POSITIONS[currentIndex].key,
    onCapture: captureFrame,
    isActive: stage === 'capture',
  })

  const ringColor = STATUS_RING_COLOR[detectionStatus] ?? '#94a3b8'

  // ── Úvod ────────────────────────────────────────────────────────────────────
  if (stage === 'intro') {
    return (
      <StepShell
        progress={progress}
        title={t('stepType.LIVENESS_CHECK')}
        description={t('step.liveness.introDescription')}
        progressSteps={progressSteps}
        progressIndex={progressIndex}
        currentStepProgress={currentStepProgress}
      >
        <div className="flex justify-center py-2">
          <div className="flex h-16 w-16 items-center justify-center rounded-2xl bg-blue-50">
            <ScanFace className="h-8 w-8 text-blue-500" />
          </div>
        </div>

        <div className="space-y-2 rounded-2xl bg-slate-50 p-4">
          {POSITIONS.map((pos) => {
            const Icon = pos.Icon
            return (
              <div key={pos.key} className="flex items-center gap-3">
                <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-full bg-white shadow-sm">
                  <Icon className="h-3.5 w-3.5 text-slate-500" />
                </div>
                <p className="text-sm text-slate-600">
                  {t(`step.liveness.direction.${pos.key}`)}
                </p>
              </div>
            )
          })}
        </div>

        <Button
          type="button"
          className="h-12 w-full rounded-2xl text-sm"
          onClick={() => setStage('capture')}
        >
          {t('step.liveness.startCheck')}
        </Button>
      </StepShell>
    )
  }

  // ── Snímání ─────────────────────────────────────────────────────────────────
  if (stage === 'capture') {
    const position = POSITIONS[currentIndex]

    const arrowColor =
      detectionStatus === 'correct-pose' || detectionStatus === 'captured'
        ? 'text-green-500'
        : detectionStatus === 'wrong-pose'
          ? 'text-amber-400'
          : 'text-slate-300'

    const ovalStroke =
      detectionStatus === 'correct-pose' || detectionStatus === 'captured'
        ? 'rgba(74,222,128,0.8)'
        : detectionStatus === 'wrong-pose'
          ? 'rgba(251,191,36,0.5)'
          : 'rgba(255,255,255,0.35)'

    return (
      <StepShell
        progress={progress}
        title={t(`step.liveness.direction.${position.key}`)}
        error={cameraError || error}
        progressSteps={progressSteps}
        progressIndex={progressIndex}
        currentStepProgress={currentStepProgress}
      >
        {/* Tečky kroků */}
        <div className="flex items-center justify-center gap-2">
          {POSITIONS.map((pos, i) => (
            <div
              key={pos.key}
              className={`h-2 rounded-full transition-all duration-300 ${
                frames[i]
                  ? 'w-2 bg-green-500'
                  : i === currentIndex
                    ? 'w-5 bg-blue-500'
                    : 'w-2 bg-slate-200'
              }`}
            />
          ))}
        </div>

        {/* Kamera + směrové šipky v třísloupcové mřížce */}
        <div
          className="mx-auto grid items-center"
          style={{ gridTemplateColumns: '44px 1fr 44px', maxWidth: '308px' }}
        >
          {/* Levá pozice */}
          <div className="flex justify-center">
            {position.key === 'left' && (
              <ArrowLeft className={`h-8 w-8 transition-colors duration-200 ${arrowColor}`} />
            )}
          </div>

          {/* Střed: volitelná šipka nahoru + kruh */}
          <div className="flex flex-col items-center gap-2">
            {position.key === 'up' && (
              <ArrowUp className={`h-8 w-8 transition-colors duration-200 ${arrowColor}`} />
            )}

            {/* Kruh s průběhovým prstencem */}
            <div className="relative" style={{ width: '220px', height: '220px' }}>
              {/* Průběhový oblouk - mimo overflow:hidden */}
              <svg
                viewBox="0 0 236 236"
                style={{
                  position: 'absolute',
                  top: '-8px',
                  left: '-8px',
                  width: '236px',
                  height: '236px',
                  pointerEvents: 'none',
                }}
              >
                <circle cx={118} cy={118} r={114} fill="none" stroke="rgba(0,0,0,0.07)" strokeWidth="4" />
                <circle
                  cx={118}
                  cy={118}
                  r={114}
                  fill="none"
                  stroke={ringColor}
                  strokeWidth="4"
                  strokeDasharray={`${holdProgress * RING_CIRCUMFERENCE} ${RING_CIRCUMFERENCE}`}
                  strokeLinecap="round"
                  transform="rotate(-90 118 118)"
                  style={{ transition: 'stroke-dasharray 0.08s linear, stroke 0.2s ease' }}
                />
              </svg>

              {/* Překryv záblesku */}
              <div
                className={`pointer-events-none absolute inset-0 z-20 rounded-full bg-white transition-opacity duration-200 ${
                  isFlashing ? 'opacity-70' : 'opacity-0'
                }`}
              />

              {/* Video */}
              <div className="absolute inset-0 overflow-hidden rounded-full bg-slate-200">
                <video
                  ref={videoRef}
                  autoPlay
                  playsInline
                  muted
                  className="h-full w-full object-cover"
                  style={{ transform: 'scaleX(-1)' }}
                />

                {/* Pomocný přerušovaný ovál obličeje */}
                <svg
                  viewBox="0 0 100 100"
                  className="pointer-events-none absolute inset-0 h-full w-full"
                >
                  <ellipse
                    cx="50"
                    cy="50"
                    rx="34"
                    ry="42"
                    fill="none"
                    stroke={ovalStroke}
                    strokeWidth="1.5"
                    strokeDasharray="6 3"
                    style={{ transition: 'stroke 0.2s ease' }}
                  />
                </svg>
              </div>
            </div>
          </div>

          {/* Pravá pozice */}
          <div className="flex justify-center">
            {position.key === 'right' && (
              <ArrowRight className={`h-8 w-8 transition-colors duration-200 ${arrowColor}`} />
            )}
          </div>
        </div>

        {/* Text stavu + průběhový pruh držení */}
        <div className="flex flex-col items-center gap-2">
          <p
            className={`text-center text-sm font-medium transition-colors duration-200 ${
              detectionStatus === 'correct-pose' || detectionStatus === 'captured'
                ? 'text-green-600'
                : detectionStatus === 'wrong-pose'
                  ? 'text-amber-500'
                  : 'text-slate-400'
            }`}
          >
            {t(`step.liveness.detectionStatus.${detectionStatus}`)}
          </p>

          {/* Progress bar - viditelný jen při držení */}
          <div
            className={`h-1 w-28 overflow-hidden rounded-full bg-green-100 transition-opacity duration-200 ${
              detectionStatus === 'correct-pose' ? 'opacity-100' : 'opacity-0'
            }`}
          >
            <div
              className="h-full rounded-full bg-green-500 transition-all duration-75"
              style={{ width: `${holdProgress * 100}%` }}
            />
          </div>
        </div>

        {/* Záložní ruční tlačítko - jen pokud se nepodařilo načíst modely */}
        {detectionStatus === 'error' && (
          <Button
            type="button"
            className="h-12 w-full rounded-2xl text-sm"
            onClick={captureFrame}
          >
            {t('step.liveness.captureManual')}
          </Button>
        )}

        <canvas ref={canvasRef} className="hidden" />
      </StepShell>
    )
  }

  return null
}

export default LivenessStep
