import { useTranslation } from 'react-i18next'
import { Smartphone } from 'lucide-react'
import StepShell from '@/components/flow/StepShell'
import { Button } from '@/components/ui/button'
import { InputOTP, InputOTPGroup, InputOTPSlot } from '@/components/ui/input-otp'
import { InputPhone } from '@/components/ui/input-phone'
import { Label } from '@/components/ui/label'

function PhoneStep({
  progress,
  phone,
  onPhoneChange,
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
      title={t('stepType.PHONE_VERIFICATION')}
      description={t('step.phone.shortDescription')}
      error={error}
      progressSteps={progressSteps}
      progressIndex={progressIndex}
      currentStepProgress={currentStepProgress}
    >
      {!codeSent ? (
        <form onSubmit={onSend} className="space-y-4">
          <div className="flex justify-center py-2">
            <div className="flex h-16 w-16 items-center justify-center rounded-2xl bg-blue-50">
              <Smartphone className="h-8 w-8 text-blue-500" />
            </div>
          </div>

          <InputPhone
            id="phone"
            value={`${phone.dialCode}${phone.contact}`}
            onChange={onPhoneChange}
            label={t('step.phone.phoneLabel')}
            preferredCountries={['CZ', 'SK', 'DE', 'AT', 'PL', 'US', 'GB']}
            placeholder={t('step.phone.phonePlaceholder')}
            required
          />

          <Button type="submit" disabled={isSending} className="h-12 w-full rounded-2xl text-sm">
            {isSending ? t('flow.sending') : t('step.phone.sendCode')}
          </Button>
        </form>
      ) : (
        <form onSubmit={onVerify} className="space-y-4">
          <div className="flex flex-col items-center gap-2 py-2 text-center">
            <div className="flex h-16 w-16 items-center justify-center rounded-2xl bg-emerald-50">
              <Smartphone className="h-8 w-8 text-emerald-500" />
            </div>
            <p className="text-sm text-slate-500">
              {t('step.phone.codeSentTo')}{' '}
              <span className="font-medium text-slate-800">{phone.dialCode} {phone.contact}</span>
            </p>
          </div>

          <div className="space-y-1.5">
            <Label htmlFor="phone-code" className="block text-center">
              {t('step.phone.codeLabel')}
            </Label>
            <InputOTP
              id="phone-code"
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
            {isVerifying ? t('flow.verifying') : t('step.phone.verifyCode')}
          </Button>

          <button
            type="button"
            disabled={isSending}
            onClick={() => onSend({ preventDefault: () => {} })}
            className="flex w-full items-center justify-center py-1 text-sm text-slate-400 transition-colors hover:text-slate-600 disabled:opacity-40"
          >
            {t('step.phone.resendCode')}
          </button>
        </form>
      )}
    </StepShell>
  )
}

export default PhoneStep
