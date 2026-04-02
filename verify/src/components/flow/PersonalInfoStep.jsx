import { useTranslation } from 'react-i18next'
import { User } from 'lucide-react'
import StepShell from '@/components/flow/StepShell'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'

function PersonalInfoStep({
  progress,
  firstName,
  onFirstNameChange,
  lastName,
  onLastNameChange,
  dateOfBirth,
  onDateOfBirthChange,
  isSubmitting,
  onSubmit,
  error,
  progressSteps,
  progressIndex,
  currentStepProgress,
}) {
  const { t } = useTranslation()

  return (
    <StepShell
      progress={progress}
      title={t('stepType.PERSONAL_INFO')}
      description={t('step.personalInfo.shortDescription')}
      error={error}
      progressSteps={progressSteps}
      progressIndex={progressIndex}
      currentStepProgress={currentStepProgress}
    >
      <form onSubmit={onSubmit} className="space-y-4">
        <div className="flex justify-center py-2">
          <div className="flex h-16 w-16 items-center justify-center rounded-2xl bg-blue-50">
            <User className="h-8 w-8 text-blue-500" />
          </div>
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="firstName">{t('step.personalInfo.firstNameLabel')}</Label>
          <Input
            id="firstName"
            type="text"
            value={firstName}
            onChange={(e) => onFirstNameChange(e.target.value)}
            placeholder={t('step.personalInfo.firstNamePlaceholder')}
            className="h-12 rounded-2xl px-4 text-base"
            required
          />
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="lastName">{t('step.personalInfo.lastNameLabel')}</Label>
          <Input
            id="lastName"
            type="text"
            value={lastName}
            onChange={(e) => onLastNameChange(e.target.value)}
            placeholder={t('step.personalInfo.lastNamePlaceholder')}
            className="h-12 rounded-2xl px-4 text-base"
            required
          />
        </div>

        <div className="space-y-1.5">
          <Label htmlFor="dateOfBirth">{t('step.personalInfo.dobLabel')}</Label>
          <Input
            id="dateOfBirth"
            type="date"
            value={dateOfBirth}
            onChange={(e) => onDateOfBirthChange(e.target.value)}
            className="h-12 rounded-2xl px-4 text-base"
            max={new Date().toISOString().split('T')[0]}
            required
          />
        </div>

        <Button type="submit" disabled={isSubmitting} className="h-12 w-full rounded-2xl text-sm">
          {isSubmitting ? t('flow.uploading') : t('step.personalInfo.submit')}
        </Button>
      </form>
    </StepShell>
  )
}

export default PersonalInfoStep
