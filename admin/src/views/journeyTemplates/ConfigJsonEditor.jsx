import { Controller } from "react-hook-form";
import { useTranslation } from "react-i18next";
import { cn } from "@/lib/utils";
import { Label } from "@/components/ui/label";
import { Tag, TagContainer } from "@/components/ui/tag";

const DOCUMENT_TYPES = ["CZECH_ID", "PASSPORT"];

const DOCUMENT_TYPE_META = {
  CZECH_ID: {
    labelKey: "moduleDefinitions.journeyTemplates.configJsonEditor.documentTypes.czechId",
    color: "blue",
  },
  PASSPORT: {
    labelKey: "moduleDefinitions.journeyTemplates.configJsonEditor.documentTypes.passport",
    color: "green",
  },
};

const OPTIONAL_STEPS = ["EMAIL_VERIFICATION", "PHONE_VERIFICATION", "AML_QUESTIONNAIRE"];

const OPTIONAL_STEP_META = {
  EMAIL_VERIFICATION: {
    labelKey:
      "moduleDefinitions.journeyTemplates.configJsonEditor.optionalSteps.emailVerification",
    color: "blue",
  },
  PHONE_VERIFICATION: {
    labelKey:
      "moduleDefinitions.journeyTemplates.configJsonEditor.optionalSteps.phoneVerification",
    color: "green",
  },
  AML_QUESTIONNAIRE: {
    labelKey:
      "moduleDefinitions.journeyTemplates.configJsonEditor.optionalSteps.amlQuestionnaire",
    color: "orange",
  },
};

export function ConfigJsonEditor({ control, readOnly }) {
  const { t } = useTranslation();

  return (
    <Controller
      name="configJson"
      control={control}
      render={({ field }) => {
        const configJson = field.value || {};
        const allowedDocumentTypes = Array.isArray(configJson.allowedDocumentTypes)
          ? configJson.allowedDocumentTypes
          : [];
        const optionalSteps = Array.isArray(configJson.optionalSteps)
          ? configJson.optionalSteps
          : [];

        const toggleDocumentType = (type) => {
          const next = allowedDocumentTypes.includes(type)
            ? allowedDocumentTypes.filter((t) => t !== type)
            : [...allowedDocumentTypes, type];
          field.onChange({ ...configJson, allowedDocumentTypes: next });
        };

        const toggleOptionalStep = (step) => {
          const next = optionalSteps.includes(step)
            ? optionalSteps.filter((s) => s !== step)
            : [...optionalSteps, step];
          field.onChange({ ...configJson, optionalSteps: next });
        };

        if (readOnly) {
          return (
            <div className="space-y-4">
              <div className="space-y-1.5">
                <Label className="text-sm font-medium">
                  {t(
                    "moduleDefinitions.journeyTemplates.configJsonEditor.labels.allowedDocumentTypes",
                  )}
                </Label>
                <TagContainer>
                  {allowedDocumentTypes.length === 0 ? (
                    <span className="text-sm text-muted-foreground">
                      {t(
                        "moduleDefinitions.journeyTemplates.configJsonEditor.noneConfigured",
                      )}
                    </span>
                  ) : (
                    allowedDocumentTypes.map((type) => {
                      const meta = DOCUMENT_TYPE_META[type];
                      return (
                        <Tag
                          key={type}
                          label={meta?.labelKey ? t(meta.labelKey) : type}
                          color={meta?.color ?? "neutral"}
                        />
                      );
                    })
                  )}
                </TagContainer>
              </div>
              <div className="space-y-1.5">
                <Label className="text-sm font-medium">
                  {t("moduleDefinitions.journeyTemplates.configJsonEditor.labels.optionalSteps")}
                </Label>
                <TagContainer>
                  {optionalSteps.length === 0 ? (
                    <span className="text-sm text-muted-foreground">
                      {t(
                        "moduleDefinitions.journeyTemplates.configJsonEditor.noneConfigured",
                      )}
                    </span>
                  ) : (
                    optionalSteps.map((step) => {
                      const meta = OPTIONAL_STEP_META[step];
                      return (
                        <Tag
                          key={step}
                          label={meta?.labelKey ? t(meta.labelKey) : step}
                          color={meta?.color ?? "neutral"}
                        />
                      );
                    })
                  )}
                </TagContainer>
              </div>
            </div>
          );
        }

        return (
          <div className="space-y-4">
            <div className="space-y-1.5">
              <Label className="text-sm font-medium">
                {t(
                  "moduleDefinitions.journeyTemplates.configJsonEditor.labels.allowedDocumentTypes",
                )}
              </Label>
              <div className="flex flex-wrap gap-2">
                {DOCUMENT_TYPES.map((type) => {
                  const meta = DOCUMENT_TYPE_META[type];
                  const enabled = allowedDocumentTypes.includes(type);
                  return (
                    <button
                      key={type}
                      type="button"
                      onClick={() => toggleDocumentType(type)}
                      className={cn(
                        "inline-flex items-center rounded-md px-3 py-1.5 text-sm font-medium border transition-colors cursor-pointer select-none",
                        enabled
                          ? "bg-primary text-primary-foreground border-primary"
                          : "bg-background text-muted-foreground border-input hover:border-ring hover:text-foreground",
                      )}
                    >
                      {meta?.labelKey ? t(meta.labelKey) : type}
                    </button>
                  );
                })}
              </div>
            </div>
            <div className="space-y-1.5">
              <Label className="text-sm font-medium">
                {t("moduleDefinitions.journeyTemplates.configJsonEditor.labels.optionalSteps")}
              </Label>
              <div className="flex flex-wrap gap-2">
                {OPTIONAL_STEPS.map((step) => {
                  const meta = OPTIONAL_STEP_META[step];
                  const enabled = optionalSteps.includes(step);
                  return (
                    <button
                      key={step}
                      type="button"
                      onClick={() => toggleOptionalStep(step)}
                      className={cn(
                        "inline-flex items-center rounded-md px-3 py-1.5 text-sm font-medium border transition-colors cursor-pointer select-none",
                        enabled
                          ? "bg-primary text-primary-foreground border-primary"
                          : "bg-background text-muted-foreground border-input hover:border-ring hover:text-foreground",
                      )}
                    >
                      {meta?.labelKey ? t(meta.labelKey) : step}
                    </button>
                  );
                })}
              </div>
              <p className="text-xs text-muted-foreground">
                {t("moduleDefinitions.journeyTemplates.configJsonEditor.toggleHint")}
              </p>
            </div>
          </div>
        );
      }}
    />
  );
}
