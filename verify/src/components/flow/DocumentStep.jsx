import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { CheckCircle2, Circle, CreditCard, BookOpen, Camera, ArrowLeft, Upload } from 'lucide-react'
import StepShell from '@/components/flow/StepShell'
import { Button } from '@/components/ui/button'

function requiredSides(documentType) {
  return documentType === 'id_card' ? ['front', 'back'] : ['front']
}

function DocumentStep({
  progress,
  allowedDocumentTypes = ['id_card', 'passport'],
  documentType,
  onDocumentTypeChange,
  onFileChange,
  selectedFiles,
  stage,
  setStage,
  currentSideIndex,
  setCurrentSideIndex,
  isUploading,
  onSubmit,
  error,
  progressSteps,
  progressIndex,
  currentStepProgress,
}) {
  const { t } = useTranslation()
  const uploadInputRef = useRef(null)
  const videoRef = useRef(null)
  const canvasRef = useRef(null)
  const streamRef = useRef(null)

  const sides = useMemo(() => requiredSides(documentType), [documentType])
  const currentSide = sides[currentSideIndex]
  const hasAllRequiredFiles = sides.every((side) => Boolean(selectedFiles?.[side]))
  const currentFile = selectedFiles?.[currentSide]
  const isLastSide = currentSideIndex === sides.length - 1

  const [previewUrl, setPreviewUrl] = useState(null)
  const [cameraError, setCameraError] = useState(null)
  const [isFlashing, setIsFlashing] = useState(false)

  useEffect(() => {
    if (!currentFile) {
      setPreviewUrl(null)
      return
    }
    const url = URL.createObjectURL(currentFile)
    setPreviewUrl(url)
    return () => URL.revokeObjectURL(url)
  }, [currentFile])

  const stopCamera = useCallback(() => {
    if (streamRef.current) {
      streamRef.current.getTracks().forEach((t) => t.stop())
      streamRef.current = null
    }
  }, [])

  const startCamera = useCallback(async () => {
    setCameraError(null)
    if (!navigator.mediaDevices?.getUserMedia) {
      setCameraError(t('step.document.cameraUnavailable'))
      return
    }
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: { ideal: 'environment' }, width: { ideal: 1280 }, height: { ideal: 720 } },
      })
      streamRef.current = stream
      if (videoRef.current) videoRef.current.srcObject = stream
    } catch {
      setCameraError(t('step.document.cameraUnavailable'))
    }
  }, [t])

  useEffect(() => {
    if (stage === 'camera') startCamera()
    else stopCamera()
  }, [stage, startCamera, stopCamera])

  useEffect(() => () => stopCamera(), [stopCamera])

  const handleUploadSelected = (event) => {
    const file = event.target.files?.[0] ?? null
    if (!currentSide || !file) return
    onFileChange(currentSide, file)
    event.target.value = ''
  }

  const handleCapturePhoto = useCallback(() => {
    const video = videoRef.current
    const canvas = canvasRef.current
    if (!video || !canvas || !video.videoWidth) return

    canvas.width = video.videoWidth
    canvas.height = video.videoHeight
    canvas.getContext('2d').drawImage(video, 0, 0)

    setIsFlashing(true)
    setTimeout(() => setIsFlashing(false), 300)

    canvas.toBlob(
      (blob) => {
        if (!blob) return
        const file = new File([blob], `document_${currentSide}.jpg`, { type: 'image/jpeg' })
        onFileChange(currentSide, file)
        stopCamera()
        setTimeout(() => setStage('capture'), 350)
      },
      'image/jpeg',
      0.92,
    )
  }, [currentSide, onFileChange, setStage, stopCamera])

  const handleNextSide = () => {
    if (currentSideIndex < sides.length - 1) {
      setCurrentSideIndex(currentSideIndex + 1)
    }
  }

  const handleBack = () => {
    if (currentSideIndex > 0) {
      setCurrentSideIndex(currentSideIndex - 1)
    } else {
      setStage('select')
    }
  }

  // ── Select stage ─────────────────────────────────────────────────────────────
  if (stage === 'select') {
    return (
      <StepShell
        progress={progress}
        title={t('step.document.selectTitle')}
        description={t('step.document.selectDescription')}
        error={error}
        progressSteps={progressSteps}
        progressIndex={progressIndex}
        currentStepProgress={currentStepProgress}
      >
        <div className="space-y-3">
          {allowedDocumentTypes.includes('id_card') && (
            <button
              type="button"
              onClick={() => onDocumentTypeChange('id_card')}
              className={`flex w-full items-center gap-4 rounded-2xl border-2 px-4 py-4 text-left transition-all ${
                documentType === 'id_card'
                  ? 'border-blue-500 bg-blue-50'
                  : 'border-slate-200 bg-white hover:border-slate-300'
              }`}
            >
              <div
                className={`flex h-11 w-11 shrink-0 items-center justify-center rounded-xl ${
                  documentType === 'id_card' ? 'bg-blue-100' : 'bg-slate-100'
                }`}
              >
                <CreditCard
                  className={`h-5 w-5 ${documentType === 'id_card' ? 'text-blue-600' : 'text-slate-400'}`}
                />
              </div>
              <div className="flex-1 min-w-0">
                <p
                  className={`text-sm font-semibold ${
                    documentType === 'id_card' ? 'text-blue-900' : 'text-slate-800'
                  }`}
                >
                  {t('step.document.idCard')}
                </p>
                <p
                  className={`mt-0.5 text-xs ${
                    documentType === 'id_card' ? 'text-blue-500' : 'text-slate-400'
                  }`}
                >
                  {t('step.document.idCardHint')}
                </p>
              </div>
              {documentType === 'id_card' ? (
                <CheckCircle2 className="h-5 w-5 shrink-0 text-blue-500" />
              ) : (
                <Circle className="h-5 w-5 shrink-0 text-slate-300" />
              )}
            </button>
          )}

          {allowedDocumentTypes.includes('passport') && (
            <button
              type="button"
              onClick={() => onDocumentTypeChange('passport')}
              className={`flex w-full items-center gap-4 rounded-2xl border-2 px-4 py-4 text-left transition-all ${
                documentType === 'passport'
                  ? 'border-blue-500 bg-blue-50'
                  : 'border-slate-200 bg-white hover:border-slate-300'
              }`}
            >
              <div
                className={`flex h-11 w-11 shrink-0 items-center justify-center rounded-xl ${
                  documentType === 'passport' ? 'bg-blue-100' : 'bg-slate-100'
                }`}
              >
                <BookOpen
                  className={`h-5 w-5 ${documentType === 'passport' ? 'text-blue-600' : 'text-slate-400'}`}
                />
              </div>
              <div className="flex-1 min-w-0">
                <p
                  className={`text-sm font-semibold ${
                    documentType === 'passport' ? 'text-blue-900' : 'text-slate-800'
                  }`}
                >
                  {t('step.document.passport')}
                </p>
                <p
                  className={`mt-0.5 text-xs ${
                    documentType === 'passport' ? 'text-blue-500' : 'text-slate-400'
                  }`}
                >
                  {t('step.document.passportHint')}
                </p>
              </div>
              {documentType === 'passport' ? (
                <CheckCircle2 className="h-5 w-5 shrink-0 text-blue-500" />
              ) : (
                <Circle className="h-5 w-5 shrink-0 text-slate-300" />
              )}
            </button>
          )}
        </div>

        <Button
          type="button"
          className="h-12 w-full rounded-2xl text-sm"
          onClick={() => setStage('capture')}
        >
          {t('step.document.continue')}
        </Button>
      </StepShell>
    )
  }

  // ── Camera stage ─────────────────────────────────────────────────────────────
  if (stage === 'camera') {
    return (
      <StepShell
        progress={progress}
        title={t(`step.document.side.${currentSide}`)}
        description={t('step.document.cameraDescription')}
        error={cameraError || error}
        progressSteps={progressSteps}
        progressIndex={progressIndex}
        currentStepProgress={currentStepProgress}
      >
        {sides.length > 1 && (
          <div className="flex items-center justify-center gap-2">
            {sides.map((side, i) => (
              <div
                key={side}
                className={`h-2 rounded-full transition-all duration-300 ${
                  i < currentSideIndex
                    ? 'w-2 bg-green-500'
                    : i === currentSideIndex
                      ? 'w-5 bg-blue-500'
                      : 'w-2 bg-slate-200'
                }`}
              />
            ))}
          </div>
        )}

        <div className="relative overflow-hidden rounded-2xl bg-black" style={{ aspectRatio: '4/3' }}>
          {/* Flash overlay */}
          <div
            className={`pointer-events-none absolute inset-0 z-20 bg-white transition-opacity duration-200 ${
              isFlashing ? 'opacity-70' : 'opacity-0'
            }`}
          />
          <video
            ref={videoRef}
            autoPlay
            playsInline
            muted
            className="h-full w-full object-cover"
          />
          {/* Document frame guide */}
          <div className="pointer-events-none absolute inset-0 flex items-center justify-center">
            <div
              className="rounded-lg border-2 border-dashed border-white/70"
              style={{ width: '85%', aspectRatio: '3/2' }}
            />
          </div>
        </div>

        <canvas ref={canvasRef} className="hidden" />

        <Button
          type="button"
          className="h-12 w-full rounded-2xl text-sm"
          onClick={handleCapturePhoto}
          disabled={!!cameraError}
        >
          <Camera className="mr-2 h-4 w-4" />
          {t('step.document.capturePhoto')}
        </Button>

        <button
          type="button"
          onClick={() => setStage('capture')}
          className="flex w-full items-center justify-center gap-1.5 py-1 text-sm text-slate-400 transition-colors hover:text-slate-600"
        >
          <ArrowLeft className="h-4 w-4" />
          {t('step.document.backToUpload')}
        </button>
      </StepShell>
    )
  }

  // ── Capture/upload stage ──────────────────────────────────────────────────────
  return (
    <StepShell
      progress={progress}
      title={t(`step.document.side.${currentSide}`)}
      description={
        sides.length > 1
          ? t('step.document.sideProgress', { current: currentSideIndex + 1, total: sides.length })
          : t('step.document.captureDescription')
      }
      error={error}
      progressSteps={progressSteps}
      progressIndex={progressIndex}
      currentStepProgress={currentStepProgress}
    >
      {sides.length > 1 && (
        <div className="flex items-center justify-center gap-2">
          {sides.map((side, i) => (
            <div
              key={side}
              className={`h-2 rounded-full transition-all duration-300 ${
                i < currentSideIndex
                  ? 'w-2 bg-green-500'
                  : i === currentSideIndex
                    ? 'w-5 bg-blue-500'
                    : 'w-2 bg-slate-200'
              }`}
            />
          ))}
        </div>
      )}

      <button
        type="button"
        onClick={() => uploadInputRef.current?.click()}
        className={`relative flex w-full flex-col items-center justify-center overflow-hidden rounded-2xl border-2 border-dashed transition-all ${
          previewUrl
            ? 'border-green-300 bg-green-50 hover:border-green-400'
            : 'border-slate-200 bg-slate-50 hover:border-blue-300 hover:bg-blue-50'
        }`}
        style={{ aspectRatio: '3/2' }}
      >
        {previewUrl ? (
          <>
            <img
              src={previewUrl}
              alt={t(`step.document.side.${currentSide}`)}
              className="h-full w-full object-contain p-2"
            />
            <div className="absolute inset-0 bg-black/0 transition-all hover:bg-black/5" />
            <span className="absolute bottom-2 right-2 rounded-full bg-white/90 px-2.5 py-1 text-xs font-medium text-slate-600 shadow-sm">
              {t('step.document.changePhoto')}
            </span>
          </>
        ) : (
          <div className="flex flex-col items-center gap-3 p-6">
            <div className="flex h-14 w-14 items-center justify-center rounded-2xl bg-white shadow-sm">
              <Upload className="h-7 w-7 text-slate-400" />
            </div>
            <div className="text-center">
              <p className="text-sm font-medium text-slate-700">{t('step.document.tapToUpload')}</p>
              <p className="mt-1 text-xs text-slate-400">{t('step.document.fileHint')}</p>
            </div>
          </div>
        )}
      </button>

      <input
        ref={uploadInputRef}
        type="file"
        accept="image/*"
        onChange={handleUploadSelected}
        className="hidden"
      />

      {!previewUrl && (
        <button
          type="button"
          onClick={() => setStage('camera')}
          className="flex w-full items-center justify-center gap-2 rounded-2xl border-2 border-slate-200 bg-white px-4 py-3 text-sm font-medium text-slate-700 transition-all hover:border-blue-300 hover:text-blue-700"
        >
          <Camera className="h-4 w-4" />
          {t('step.document.useCamera')}
        </button>
      )}

      {currentFile && !isLastSide ? (
        <Button
          type="button"
          className="h-12 w-full rounded-2xl text-sm"
          onClick={handleNextSide}
        >
          {t('step.document.continueToBack')}
        </Button>
      ) : (
        <Button
          type="button"
          disabled={isUploading || !hasAllRequiredFiles}
          className="h-12 w-full rounded-2xl text-sm"
          onClick={onSubmit}
        >
          {isUploading ? t('flow.uploading') : t('step.document.upload')}
        </Button>
      )}

      <button
        type="button"
        onClick={handleBack}
        className="flex w-full items-center justify-center gap-1.5 py-1 text-sm text-slate-400 transition-colors hover:text-slate-600"
      >
        <ArrowLeft className="h-4 w-4" />
        {currentSideIndex > 0 ? t('step.document.backToFront') : t('step.document.backToType')}
      </button>
    </StepShell>
  )
}

export default DocumentStep
