import { useState } from 'react'
import { Mail, Smartphone, IdCard, ScanFace, ClipboardList, Circle, Copy, Share2, Check, User } from 'lucide-react'
import { QRCodeSVG } from 'qrcode.react'
import { useTranslation } from 'react-i18next'
import { Button } from '@/components/ui/button'
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog'

function ContinueOnPhoneButton() {
  const { t } = useTranslation()
  const [copied, setCopied] = useState(false)
  const url = window.location.href

  const handleCopy = async () => {
    await navigator.clipboard.writeText(url)
    setCopied(true)
    setTimeout(() => setCopied(false), 2000)
  }

  const handleShare = async () => {
    try {
      await navigator.share({ url, title: t('flow.continueOnPhone') })
    } catch {}
  }

  return (
    <Dialog>
      <DialogTrigger asChild>
        <button
          type="button"
          className="flex items-center gap-1.5 text-xs text-slate-400 transition hover:text-slate-600"
        >
          <Smartphone className="h-3.5 w-3.5" />
          {t('flow.continueOnPhone')}
        </button>
      </DialogTrigger>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{t('flow.continueOnPhone')}</DialogTitle>
          <DialogDescription>{t('flow.qrDescription')}</DialogDescription>
        </DialogHeader>
        <div className="flex justify-center rounded-xl bg-slate-50 p-4">
          <QRCodeSVG value={url} size={180} />
        </div>
        <div className="flex gap-2">
          {typeof navigator.share === 'function' && (
            <Button onClick={handleShare} className="flex-1">
              <Share2 />
              {t('flow.share')}
            </Button>
          )}
          <Button variant="outline" onClick={handleCopy} className="flex-1">
            {copied ? <Check className="text-emerald-600" /> : <Copy />}
            {copied ? t('flow.linkCopied') : t('flow.copyLink')}
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  )
}

function StepShell({
  progress,
  title,
  description,
  children,
  error,
  progressSteps = [],
  progressIndex = 0,
  currentStepProgress = 0,
}) {
  const hasStepper = progressSteps.length > 1

  const iconByType = {
    EMAIL_VERIFICATION: Mail,
    PHONE_VERIFICATION: Smartphone,
    DOCUMENT_IDENTITY: IdCard,
    LIVENESS_CHECK: ScanFace,
    AML_QUESTIONNAIRE: ClipboardList,
    PERSONAL_INFO: User,
  }

  return (
    <section className="flex min-h-[calc(100vh-6.5rem)] flex-col">
      <div className="mb-4">
        {hasStepper ? (
          <div className="space-y-2">
            <div className="grid gap-2" style={{ gridTemplateColumns: `repeat(${progressSteps.length}, minmax(0, 1fr))` }}>
              {progressSteps.map((step, index) => {
                const Icon = iconByType[step.type] ?? Circle
                return (
                <div key={`${step.type}-${index}`} className="text-center">
                  <div className="flex justify-center">
                    <Icon className={`h-4 w-4 ${index <= progressIndex ? 'text-slate-900' : 'text-slate-400'}`} />
                  </div>
                  <div className={`mx-auto mt-1 h-1.5 w-1.5 rounded-full ${index <= progressIndex ? 'bg-blue-600' : 'bg-slate-300'}`} />
                </div>
                )
              })}
            </div>
            <div className="flex items-center gap-2">
              {progressSteps.map((_, index) => (
                <div key={`segment-${index}`} className="h-2 flex-1 rounded-full bg-blue-100">
                  <div
                    className="h-2 rounded-full bg-blue-600 transition-all"
                    style={{
                      width:
                        progressIndex > index
                          ? '100%'
                          : progressIndex === index
                            ? `${Math.max(0, Math.min(100, currentStepProgress))}%`
                            : '0%',
                    }}
                  />
                </div>
              ))}
            </div>
          </div>
        ) : (
          <div className="mb-2 h-1.5 w-full rounded-full bg-slate-200">
            <div className="h-1.5 rounded-full bg-slate-900 transition-all" style={{ width: `${progress}%` }} />
          </div>
        )}
      </div>

      <div className="flex flex-1 flex-col justify-center rounded-3xl bg-white px-5 py-6 shadow-sm ring-1 ring-slate-200">
        <h1 className="text-center text-2xl font-semibold text-slate-900">{title}</h1>
        {description ? <p className="mt-2 text-center text-sm leading-6 text-slate-600">{description}</p> : null}

        <div className="mt-6 space-y-4">{children}</div>

        {error ? <p className="mt-4 text-sm text-rose-700">{error}</p> : null}
      </div>

      <div className="mt-3 hidden justify-center sm:flex">
        <ContinueOnPhoneButton />
      </div>
    </section>
  )
}

export default StepShell
