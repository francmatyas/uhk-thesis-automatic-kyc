import { useEffect, useMemo, useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { useTranslation } from 'react-i18next'
import { useParams } from 'react-router'
import {
  fetchVerificationFlow,
  finalizeVerification,
  sendEmailVerificationCode,
  sendPhoneVerificationCode,
  submitAmlQuestionnaire,
  submitPersonalInfo,
  uploadIdentityDocument,
  uploadLivenessFrames,
  verifyEmailCode,
  verifyPhoneCode,
} from '@/api/verification'
import AmlStep from '@/components/flow/AmlStep'
import DocumentStep from '@/components/flow/DocumentStep'
import EmailStep from '@/components/flow/EmailStep'
import FallbackStep from '@/components/flow/FallbackStep'
import LivenessStep from '@/components/flow/LivenessStep'
import PersonalInfoStep from '@/components/flow/PersonalInfoStep'
import PhoneStep from '@/components/flow/PhoneStep'
import StepShell from '@/components/flow/StepShell'

function errorToMessage(error, fallback) {
  return error?.response?.data?.message ?? error?.message ?? fallback
}

function requiredDocumentSides(documentType) {
  return documentType === 'id_card' ? ['front', 'back'] : ['front']
}

function VerificationFlowPage() {
  const { token = '' } = useParams()
  const { t } = useTranslation()

  const [localStatuses, setLocalStatuses] = useState({})
  const [email, setEmail] = useState('')
  const [emailCode, setEmailCode] = useState('')
  const [emailCodeSent, setEmailCodeSent] = useState(false)
  const [phoneDialCode, setPhoneDialCode] = useState('+420')
  const [phoneContact, setPhoneContact] = useState('')
  const [phoneCode, setPhoneCode] = useState('')
  const [phoneCodeSent, setPhoneCodeSent] = useState(false)
  const [firstName, setFirstName] = useState('')
  const [lastName, setLastName] = useState('')
  const [dateOfBirth, setDateOfBirth] = useState('')
  const [documentType, setDocumentType] = useState('id_card')
  const [documentFiles, setDocumentFiles] = useState({ front: null, back: null })
  const [documentStage, setDocumentStage] = useState('select')
  const [documentCurrentSideIndex, setDocumentCurrentSideIndex] = useState(0)

  const flowQuery = useQuery({
    queryKey: ['verification-flow', token],
    queryFn: () => fetchVerificationFlow(token),
    enabled: Boolean(token),
    retry: (_, error) => {
      const apiError = error?.response?.data?.error
      const permanent = ['verification_already_submitted', 'verification_expired', 'not_found']
      return !permanent.includes(apiError)
    },
  })

  const flow = flowQuery.data
  const steps = useMemo(
    () =>
      (flow?.steps ?? []).map((step, index) => ({
        ...step,
        index,
        status: localStatuses[step.id] ?? step.status,
      })),
    [flow?.steps, localStatuses],
  )

  const totalRequired = steps.filter((step) => step.required).length
  const completedRequired = steps.filter((step) => step.required && step.status === 'COMPLETED').length
  const progress = totalRequired ? Math.round((completedRequired / totalRequired) * 100) : 0
  const requiredSteps = steps.filter((step) => step.required)

  const activeStep = steps.find((step) => step.status !== 'COMPLETED')
  const activeStepIndex = activeStep
    ? requiredSteps.findIndex((step) => step.id === activeStep.id)
    : requiredSteps.length
  const progressSteps = requiredSteps.map((step) => ({
    type: step.type,
    label: t(`stepType.${step.type}`, { defaultValue: step.type }),
  }))
  let currentStepProgress = 0

  if (activeStep?.type === 'EMAIL_VERIFICATION') {
    currentStepProgress = emailCodeSent ? 50 : 0
  } else if (activeStep?.type === 'PHONE_VERIFICATION') {
    currentStepProgress = phoneCodeSent ? 50 : 0
  } else if (activeStep?.type === 'DOCUMENT_IDENTITY') {
    const sides = requiredDocumentSides(documentType)
    const totalPages = 1 + sides.length
    let completedPages = documentStage === 'select' ? 0 : 1
    completedPages += sides.filter((side) => Boolean(documentFiles[side])).length
    currentStepProgress = Math.round((completedPages / totalPages) * 100)
  }

  const setStepStatus = (stepId, status) => {
    setLocalStatuses((prev) => ({ ...prev, [stepId]: status }))
  }

  const markCompleted = (stepId) => {
    setStepStatus(stepId, 'COMPLETED')
  }

  const sendEmailMutation = useMutation({
    mutationFn: ({ value }) => sendEmailVerificationCode(token, value),
    onSuccess: (_, variables) => {
      setEmailCodeSent(true)
      setStepStatus(variables.stepId, 'IN_PROGRESS')
    },
  })

  const verifyEmailMutation = useMutation({
    mutationFn: async ({ value }) => {
      const result = await verifyEmailCode(token, value)
      if (!result?.verified) {
        throw new Error(t('step.email.invalidCode'))
      }
      return result
    },
    onSuccess: () => {
      if (activeStep) markCompleted(activeStep.id)
    },
  })

  const sendPhoneMutation = useMutation({
    mutationFn: ({ dialCode, contact }) => sendPhoneVerificationCode(token, dialCode, contact),
    onSuccess: (_, variables) => {
      setPhoneCodeSent(true)
      setStepStatus(variables.stepId, 'IN_PROGRESS')
    },
  })

  const verifyPhoneMutation = useMutation({
    mutationFn: async ({ value }) => {
      const result = await verifyPhoneCode(token, value)
      if (!result?.verified) {
        throw new Error(t('step.phone.invalidCode'))
      }
      return result
    },
    onSuccess: () => {
      if (activeStep) markCompleted(activeStep.id)
    },
  })

  const uploadDocumentMutation = useMutation({
    mutationFn: ({ files, type }) => uploadIdentityDocument(token, type, files),
    onSuccess: () => {
      if (activeStep) markCompleted(activeStep.id)
    },
  })

  const uploadLivenessMutation = useMutation({
    mutationFn: ({ frames }) => uploadLivenessFrames(token, frames),
    onSuccess: () => {
      if (activeStep) markCompleted(activeStep.id)
    },
  })

  const submitPersonalInfoMutation = useMutation({
    mutationFn: ({ data }) => submitPersonalInfo(token, data),
    onSuccess: () => {
      if (activeStep) markCompleted(activeStep.id)
    },
  })

  const submitAmlMutation = useMutation({
    mutationFn: ({ answers }) => submitAmlQuestionnaire(token, answers),
    onSuccess: () => {
      if (activeStep) markCompleted(activeStep.id)
    },
  })

  const finalizeMutation = useMutation({
    mutationFn: () => finalizeVerification(token),
  })

  const allRequiredComplete =
    steps.length > 0 && steps.filter((s) => s.required).every((s) => s.status === 'COMPLETED')

  useEffect(() => {
    if (
      allRequiredComplete &&
      !finalizeMutation.isPending &&
      !finalizeMutation.isSuccess &&
      !finalizeMutation.isError
    ) {
      finalizeMutation.mutate()
    }
  }, [allRequiredComplete]) // eslint-disable-line react-hooks/exhaustive-deps

  const handleSendEmailCode = (event) => {
    event.preventDefault()
    if (!activeStep || !email.trim()) return
    sendEmailMutation.mutate({ stepId: activeStep.id, value: email.trim() })
  }

  const handleVerifyEmailCode = (event) => {
    event.preventDefault()
    if (!emailCode.trim()) return
    verifyEmailMutation.mutate({ value: emailCode.trim() })
  }

  const handleSendPhoneCode = (event) => {
    event.preventDefault()
    if (!activeStep || !phoneContact.trim()) return
    sendPhoneMutation.mutate({ stepId: activeStep.id, dialCode: phoneDialCode, contact: phoneContact.trim() })
  }

  const handleVerifyPhoneCode = (event) => {
    event.preventDefault()
    if (!phoneCode.trim()) return
    verifyPhoneMutation.mutate({ value: phoneCode.trim() })
  }

  const handleSubmitPersonalInfo = (event) => {
    event.preventDefault()
    if (!firstName.trim() || !lastName.trim() || !dateOfBirth) return
    submitPersonalInfoMutation.mutate({
      data: { firstName: firstName.trim(), lastName: lastName.trim(), dateOfBirth },
    })
  }

  const handleUploadDocument = () => {
    const sides = requiredDocumentSides(documentType)
    const missingSide = sides.find((side) => !documentFiles[side])
    if (missingSide) return
    uploadDocumentMutation.mutate({ type: documentType, files: documentFiles })
  }

  if (flowQuery.isLoading) {
    return (
      <section className="flex min-h-[calc(100vh-6.5rem)] flex-col">
        <div className="mb-4 h-2 w-full animate-pulse rounded-full bg-slate-200" />
        <div className="flex flex-1 flex-col justify-center rounded-3xl bg-white px-5 py-6 shadow-sm ring-1 ring-slate-200">
          <div className="h-7 w-44 animate-pulse rounded-lg bg-slate-100" />
          <div className="mt-3 h-4 w-full animate-pulse rounded-lg bg-slate-100" />
          <div className="mt-2 h-4 w-2/3 animate-pulse rounded-lg bg-slate-100" />
          <div className="mt-8 space-y-3">
            <div className="h-12 w-full animate-pulse rounded-2xl bg-slate-100" />
            <div className="h-12 w-full animate-pulse rounded-2xl bg-slate-100" />
          </div>
        </div>
      </section>
    )
  }

  if (flowQuery.isError) {
    const apiError = flowQuery.error?.response?.data?.error

    if (apiError === 'verification_already_submitted') {
      return (
        <StepShell progress={100} title={t('flow.alreadySubmitted')} description={t('flow.alreadySubmittedDescription')}>
          <img src="/verification_already_submitted.svg" alt="" className="mx-auto w-48" />
        </StepShell>
      )
    }

    if (apiError === 'verification_expired') {
      return (
        <StepShell progress={0} title={t('flow.expired')} description={t('flow.expiredDescription')}>
          <img src="/verification_expired.svg" alt="" className="mx-auto w-48" />
        </StepShell>
      )
    }

    return (
      <StepShell progress={0} title={t('flow.loadError')} description={t('flow.loadErrorDescription')}>
        <img src="/verification_not_found.svg" alt="" className="mx-auto w-48" />
        <button
          type="button"
          onClick={() => flowQuery.refetch()}
          className="w-full rounded-2xl bg-slate-900 px-4 py-3 text-sm font-medium text-white"
        >
          {t('flow.retry')}
        </button>
      </StepShell>
    )
  }

  if (!activeStep) {
    return (
      <StepShell
        progress={100}
        title={t('flow.completedTitle')}
        description={t('flow.completedDescription')}
      >
        <img src="/verification_ready_for_autocheck.svg" alt="" className="mx-auto w-48" />
      </StepShell>
    )
  }

  if (activeStep.type === 'EMAIL_VERIFICATION') {
    const errorMessage = sendEmailMutation.isError
      ? errorToMessage(sendEmailMutation.error, t('step.email.sendError'))
      : verifyEmailMutation.isError
        ? errorToMessage(verifyEmailMutation.error, t('step.email.verifyError'))
        : null

    return (
      <EmailStep
        progress={progress}
        email={email}
        onEmailChange={setEmail}
        code={emailCode}
        onCodeChange={setEmailCode}
        codeSent={emailCodeSent}
        isSending={sendEmailMutation.isPending}
        isVerifying={verifyEmailMutation.isPending}
        onSend={handleSendEmailCode}
        onVerify={handleVerifyEmailCode}
        error={errorMessage}
        progressSteps={progressSteps}
        progressIndex={activeStepIndex}
        currentStepProgress={currentStepProgress}
      />
    )
  }

  if (activeStep.type === 'PHONE_VERIFICATION') {
    const errorMessage = sendPhoneMutation.isError
      ? errorToMessage(sendPhoneMutation.error, t('step.phone.sendError'))
      : verifyPhoneMutation.isError
        ? errorToMessage(verifyPhoneMutation.error, t('step.phone.verifyError'))
        : null

    return (
      <PhoneStep
        progress={progress}
        phone={{ dialCode: phoneDialCode, contact: phoneContact }}
        onPhoneChange={({ dialCode, contact }) => { setPhoneDialCode(dialCode); setPhoneContact(contact) }}
        code={phoneCode}
        onCodeChange={setPhoneCode}
        codeSent={phoneCodeSent}
        isSending={sendPhoneMutation.isPending}
        isVerifying={verifyPhoneMutation.isPending}
        onSend={handleSendPhoneCode}
        onVerify={handleVerifyPhoneCode}
        error={errorMessage}
        progressSteps={progressSteps}
        progressIndex={activeStepIndex}
        currentStepProgress={currentStepProgress}
      />
    )
  }

  if (activeStep.type === 'DOCUMENT_IDENTITY') {
    const allowedDocumentTypes = activeStep.config?.allowedDocumentTypes ?? ['id_card', 'passport']
    const effectiveDocumentType = allowedDocumentTypes.includes(documentType)
      ? documentType
      : allowedDocumentTypes[0]

    return (
      <DocumentStep
        progress={progress}
        allowedDocumentTypes={allowedDocumentTypes}
        documentType={effectiveDocumentType}
        onDocumentTypeChange={(type) => {
          setDocumentType(type)
          setDocumentFiles({ front: null, back: null })
          setDocumentStage('select')
          setDocumentCurrentSideIndex(0)
        }}
        onFileChange={(side, file) =>
          setDocumentFiles((prev) => ({
            ...prev,
            [side]: file,
          }))
        }
        selectedFiles={documentFiles}
        stage={documentStage}
        setStage={setDocumentStage}
        currentSideIndex={documentCurrentSideIndex}
        setCurrentSideIndex={setDocumentCurrentSideIndex}
        isUploading={uploadDocumentMutation.isPending}
        onSubmit={handleUploadDocument}
        error={
          uploadDocumentMutation.isError
            ? errorToMessage(uploadDocumentMutation.error, t('step.document.uploadError'))
            : null
        }
        progressSteps={progressSteps}
        progressIndex={activeStepIndex}
        currentStepProgress={currentStepProgress}
      />
    )
  }

  if (activeStep.type === 'LIVENESS_CHECK') {
    return (
      <LivenessStep
        progress={progress}
        onSubmit={(frames) => uploadLivenessMutation.mutate({ frames })}
        error={
          uploadLivenessMutation.isError
            ? errorToMessage(uploadLivenessMutation.error, t('step.liveness.uploadError'))
            : null
        }
        progressSteps={progressSteps}
        progressIndex={activeStepIndex}
        currentStepProgress={currentStepProgress}
      />
    )
  }

  if (activeStep.type === 'AML_QUESTIONNAIRE') {
    return (
      <AmlStep
        progress={progress}
        config={activeStep.config}
        onSubmit={(answers) => submitAmlMutation.mutate({ answers })}
        isUploading={submitAmlMutation.isPending}
        error={
          submitAmlMutation.isError
            ? errorToMessage(submitAmlMutation.error, t('step.aml.uploadError'))
            : null
        }
        progressSteps={progressSteps}
        progressIndex={activeStepIndex}
        currentStepProgress={currentStepProgress}
      />
    )
  }

  if (activeStep.type === 'PERSONAL_INFO') {
    return (
      <PersonalInfoStep
        progress={progress}
        firstName={firstName}
        onFirstNameChange={setFirstName}
        lastName={lastName}
        onLastNameChange={setLastName}
        dateOfBirth={dateOfBirth}
        onDateOfBirthChange={setDateOfBirth}
        isSubmitting={submitPersonalInfoMutation.isPending}
        onSubmit={handleSubmitPersonalInfo}
        error={
          submitPersonalInfoMutation.isError
            ? errorToMessage(submitPersonalInfoMutation.error, t('step.personalInfo.submitError'))
            : null
        }
        progressSteps={progressSteps}
        progressIndex={activeStepIndex}
        currentStepProgress={currentStepProgress}
      />
    )
  }

  return (
    <FallbackStep
      progress={progress}
      stepType={activeStep.type}
      onComplete={() => markCompleted(activeStep.id)}
      isPending={false}
      progressSteps={progressSteps}
      progressIndex={activeStepIndex}
      currentStepProgress={currentStepProgress}
    />
  )
}

export default VerificationFlowPage
