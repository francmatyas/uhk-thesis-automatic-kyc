import { useTranslation } from 'react-i18next'
import { CircleHelp } from 'lucide-react'
import StepShell from '@/components/flow/StepShell'
import { Button } from '@/components/ui/button'

function FallbackStep({
  progress,
  stepType,
  onComplete,
  isPending,
  progressSteps,
  progressIndex,
  currentStepProgress,
}) {
  const { t } = useTranslation()

  return (
    <StepShell
      progress={progress}
      title={t(`stepType.${stepType}`, { defaultValue: stepType })}
      description={t('flow.fallbackDescription')}
      progressSteps={progressSteps}
      progressIndex={progressIndex}
      currentStepProgress={currentStepProgress}
    >
      <div className="flex justify-center py-2">
        <div className="flex h-16 w-16 items-center justify-center rounded-2xl bg-slate-100">
          <CircleHelp className="h-8 w-8 text-slate-400" />
        </div>
      </div>

      <Button
        type="button"
        onClick={onComplete}
        disabled={isPending}
        className="h-12 w-full rounded-2xl text-sm"
      >
        {t('flow.completeStep')}
      </Button>
    </StepShell>
  )
}

export default FallbackStep
