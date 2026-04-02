import { useTranslation } from 'react-i18next'
import { Mail } from 'lucide-react'
import StepShell from '@/components/flow/StepShell'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { InputOTP, InputOTPGroup, InputOTPSlot } from '@/components/ui/input-otp'
import { Label } from '@/components/ui/label'

function EmailStep({
  progress,
  email,
  onEmailChange,
  code,
  onCodeChange,
  codeSent,
  isSending,
  isVerifying,
  onSend,
  onVerify,
  error,
  progressSteps,
  progressIndex,
  currentStepProgress,
}) {
  const { t } = useTranslation()

  return (
    <StepShell
      progress={progress}
      title={t('stepType.EMAIL_VERIFICATION')}
      description={t('step.email.shortDescription')}
      error={error}
      progressSteps={progressSteps}
      progressIndex={progressIndex}
      currentStepProgress={currentStepProgress}
    >
      {!codeSent ? (
        <form onSubmit={onSend} className="space-y-4">
          <div className="flex justify-center py-2">
            <div className="flex h-16 w-16 items-center justify-center rounded-2xl bg-blue-50">
              <Mail className="h-8 w-8 text-blue-500" />
            </div>
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="email">{t('step.email.emailLabel')}</Label>
            <Input
              id="email"
              type="email"
              value={email}
              onChange={(event) => onEmailChange(event.target.value)}
              placeholder={t('step.email.emailPlaceholder')}
              className="h-12 rounded-2xl px-4 text-base"
              required
            />
          </div>

          <Button type="submit" disabled={isSending} className="h-12 w-full rounded-2xl text-sm">
            {isSending ? t('flow.sending') : t('step.email.sendCode')}
          </Button>
        </form>
      ) : (
        <form onSubmit={onVerify} className="space-y-4">
          <div className="flex flex-col items-center gap-2 py-2 text-center">
            <div className="flex h-16 w-16 items-center justify-center rounded-2xl bg-emerald-50">
              <Mail className="h-8 w-8 text-emerald-500" />
            </div>
            <p className="text-sm text-slate-500">
              {t('step.email.codeSentTo')}{' '}
              <span className="font-medium text-slate-800">{email}</span>
            </p>
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="email-code" className="block text-center">
              {t('step.email.codeLabel')}
            </Label>
            <InputOTP
              id="email-code"
              value={code}
              onChange={onCodeChange}
              maxLength={6}
              containerClassName="justify-center"
              autoFocus
              required
            >
              <InputOTPGroup>
                <InputOTPSlot index={0} className="size-12 text-base" />
                <InputOTPSlot index={1} className="size-12 text-base" />
                <InputOTPSlot index={2} className="size-12 text-base" />
                <InputOTPSlot index={3} className="size-12 text-base" />
                <InputOTPSlot index={4} className="size-12 text-base" />
                <InputOTPSlot index={5} className="size-12 text-base" />
              </InputOTPGroup>
            </InputOTP>
          </div>

          <Button type="submit" disabled={isVerifying} className="h-12 w-full rounded-2xl text-sm">
            {isVerifying ? t('flow.verifying') : t('step.email.verifyCode')}
          </Button>

          <button
            type="button"
            disabled={isSending}
            onClick={() => onSend({ preventDefault: () => {} })}
            className="flex w-full items-center justify-center py-1 text-sm text-slate-400 transition-colors hover:text-slate-600 disabled:opacity-40"
          >
            {t('step.email.resendCode')}
          </button>
        </form>
      )}
    </StepShell>
  )
}

export default EmailStep
