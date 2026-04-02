import { useState } from 'react'
import { useTranslation } from 'react-i18next'
import { AML_FORM_SCHEMA } from '../../assets/data/amlFormSchema'
import countries from '../../assets/data/countries.json'
import StepShell from './StepShell'

const inputClass =
  'w-full rounded-xl border border-slate-200 bg-white px-3.5 py-2.5 text-sm text-slate-900 placeholder:text-slate-400 focus:border-blue-500 focus:outline-none focus:ring-2 focus:ring-blue-100'

// ── pomocné funkce ────────────────────────────────────────────────────────────

function isVisible(fieldDef, formData, optionalFields) {
  if (fieldDef.optional && !optionalFields.includes(fieldDef.key)) return false
  if (!fieldDef.showWhen) return true

  const results = fieldDef.showWhen.map(({ field, eq, in: inValues }) =>
    inValues !== undefined ? inValues.includes(formData[field]) : formData[field] === eq,
  )
  return fieldDef.showWhenMode === 'any' ? results.some(Boolean) : results.every(Boolean)
}

function isEmpty(value) {
  return value === undefined || value === null || value === ''
}

// ── podkomponenty ─────────────────────────────────────────────────────────────

function SectionHeader({ children }) {
  return (
    <h2 className="mb-3 text-xs font-semibold uppercase tracking-wider text-slate-500">
      {children}
    </h2>
  )
}

function FieldWrapper({ label, required, hint, error, children }) {
  const { t } = useTranslation()
  return (
    <div>
      <div className="mb-1.5 flex items-baseline justify-between gap-2">
        <label className="text-sm font-medium text-slate-700">{label}</label>
        {!required && (
          <span className="text-xs text-slate-400">{t('step.aml.optional')}</span>
        )}
      </div>
      {hint && <p className="mb-1.5 text-xs text-slate-500">{hint}</p>}
      {children}
      {error && <p className="mt-1 text-xs text-rose-600">{error}</p>}
    </div>
  )
}

// ── renderery polí ────────────────────────────────────────────────────────────

function SelectField({ fieldDef, value, onChange, label, hint, error, configOptions }) {
  const { t } = useTranslation()
  return (
    <FieldWrapper label={label} required={fieldDef.required} hint={hint} error={error}>
      <select value={value ?? ''} onChange={(e) => onChange(e.target.value)} className={inputClass}>
        <option value="">{t(`step.aml.field.${fieldDef.tKey ?? fieldDef.key}.placeholder`)}</option>
        {fieldDef.optionsSource === 'countries'
          ? countries.map((c) => (
              <option key={c.code} value={c.code}>
                {c.name}
              </option>
            ))
          : configOptions !== undefined
            ? configOptions.map((opt) => (
                <option key={opt} value={opt}>
                  {opt}
                </option>
              ))
            : fieldDef.options.map((opt) => (
                <option key={opt} value={opt}>
                  {t(`step.aml.options.${fieldDef.key}.${opt}`)}
                </option>
              ))}
      </select>
    </FieldWrapper>
  )
}

function TextField({ fieldDef, value, onChange, label, hint, error }) {
  const { t } = useTranslation()
  return (
    <FieldWrapper label={label} required={fieldDef.required} hint={hint} error={error}>
      <input
        type="text"
        value={value ?? ''}
        onChange={(e) => onChange(e.target.value)}
        placeholder={t(`step.aml.field.${fieldDef.tKey ?? fieldDef.key}.placeholder`)}
        className={inputClass}
      />
    </FieldWrapper>
  )
}

function TextareaField({ fieldDef, value, onChange, label, hint, error }) {
  const { t } = useTranslation()
  return (
    <FieldWrapper label={label} required={fieldDef.required} hint={hint} error={error}>
      <textarea
        rows={3}
        value={value ?? ''}
        onChange={(e) => onChange(e.target.value)}
        placeholder={t(`step.aml.field.${fieldDef.tKey ?? fieldDef.key}.placeholder`)}
        className={inputClass}
      />
    </FieldWrapper>
  )
}

function BooleanField({ fieldDef, value, onChange, label, hint, error }) {
  const { t } = useTranslation()
  return (
    <FieldWrapper label={label} required={fieldDef.required} hint={hint} error={error}>
      <div className="flex gap-2">
        {[true, false].map((bool) => (
          <button
            key={String(bool)}
            type="button"
            onClick={() => onChange(bool)}
            className={`flex-1 rounded-xl border px-4 py-2.5 text-sm font-medium transition-colors ${
              value === bool
                ? 'border-blue-600 bg-blue-600 text-white'
                : 'border-slate-200 bg-white text-slate-700'
            }`}
          >
            {t(bool ? 'step.aml.yes' : 'step.aml.no')}
          </button>
        ))}
      </div>
    </FieldWrapper>
  )
}

const FIELD_RENDERERS = {
  select: SelectField,
  text: TextField,
  textarea: TextareaField,
  boolean: BooleanField,
}

// ── hlavní komponenta ─────────────────────────────────────────────────────────

function AmlStep({
  progress,
  config,
  error,
  isUploading,
  onSubmit,
  progressSteps,
  progressIndex,
  currentStepProgress,
}) {
  const { t } = useTranslation()

  const optionalFields = config?.optionalFields ?? []

  const [formData, setFormData] = useState({})
  const [submitAttempted, setSubmitAttempted] = useState(false)

  const setValue = (key, value) => setFormData((prev) => ({ ...prev, [key]: value }))

  const getLabel = (fieldDef) =>
    t(`step.aml.field.${fieldDef.tKey ?? fieldDef.key}.label`)

  const getHint = (fieldDef) => {
    const key = `step.aml.field.${fieldDef.tKey ?? fieldDef.key}.hint`
    const val = t(key)
    return val === key ? null : val
  }

  const getError = (fieldDef) => {
    if (!submitAttempted) return null
    if (!fieldDef.required) return null
    const value = formData[fieldDef.key]
    const invalid = fieldDef.type === 'boolean' ? value === undefined || value === null : isEmpty(value)
    return invalid ? t('step.aml.error.required') : null
  }

  const handleSubmit = () => {
    setSubmitAttempted(true)

    // Validuj všechna pole schématu, která jsou viditelná a povinná
    for (const { fields } of AML_FORM_SCHEMA) {
      for (const fieldDef of fields) {
        if (!isVisible(fieldDef, formData, optionalFields)) continue
        if (!fieldDef.required) continue
        const value = formData[fieldDef.key]
        const invalid =
          fieldDef.type === 'boolean'
            ? value === undefined || value === null
            : isEmpty(value)
        if (invalid) return
      }
    }

    onSubmit(formData)
  }

  const renderSchemaField = (fieldDef) => {
    if (!isVisible(fieldDef, formData, optionalFields)) return null

    const Renderer = FIELD_RENDERERS[fieldDef.type]
    if (!Renderer) return null

    const configOptions =
      fieldDef.optionsSource === 'config'
        ? (config?.fieldOptions?.[fieldDef.key] ?? [])
        : undefined

    return (
      <Renderer
        key={fieldDef.key}
        fieldDef={fieldDef}
        value={formData[fieldDef.key]}
        onChange={(val) => setValue(fieldDef.key, val)}
        label={getLabel(fieldDef)}
        hint={getHint(fieldDef)}
        error={getError(fieldDef)}
        configOptions={configOptions}
      />
    )
  }

  return (
    <StepShell
      progress={progress}
      title={t('step.aml.title')}
      description={t('step.aml.description')}
      error={error}
      progressSteps={progressSteps}
      progressIndex={progressIndex}
      currentStepProgress={currentStepProgress}
    >
      {AML_FORM_SCHEMA.map(({ section, fields }) => (
        <div key={section} className="space-y-4">
          <SectionHeader>{t(`step.aml.section.${section}`)}</SectionHeader>
          {fields.map(renderSchemaField)}
        </div>
      ))}

      <button
        type="button"
        disabled={isUploading}
        onClick={handleSubmit}
        className="h-12 w-full rounded-2xl bg-slate-900 text-sm font-medium text-white disabled:opacity-50"
      >
        {isUploading ? t('flow.uploading') : t('step.aml.submit')}
      </button>
    </StepShell>
  )
}

export default AmlStep
