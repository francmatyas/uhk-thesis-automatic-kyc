import { useState } from "react";
import { useTranslation } from "react-i18next";
import {
  ChevronDown,
  ClipboardList,
  Eye,
  FileText,
  Mail,
  Phone,
  ShieldCheck,
  User,
  UserCheck,
} from "lucide-react";
import {
  Label,
  PolarAngleAxis,
  PolarRadiusAxis,
  RadialBar,
  RadialBarChart,
} from "recharts";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { ChartContainer } from "@/components/ui/chart";
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from "@/components/ui/collapsible";

const STEP_STATUS_MAP = {
  COMPLETED: "completed",
  FAILED: "failed",
  PENDING: "queued",
  SKIPPED: "cancelled",
  WARNING: "warning",
};

const CHECK_STATUS_MAP = {
  PASSED: "success",
  FAILED: "failed",
  PENDING: "queued",
  ERROR: "error",
  WARNING: "warning",
};

const STEP_TYPE_LABEL_KEYS = {
  PERSONAL_INFO:
    "moduleDefinitions.verifications.verificationDetail.steps.stepType.personalInfo",
  DOC_OCR:
    "moduleDefinitions.verifications.verificationDetail.steps.stepType.docOcr",
  FACE_MATCH:
    "moduleDefinitions.verifications.verificationDetail.steps.stepType.faceMatch",
  LIVENESS:
    "moduleDefinitions.verifications.verificationDetail.steps.stepType.livenessDetection",
  AML_SCREEN:
    "moduleDefinitions.verifications.verificationDetail.steps.stepType.amlScreening",
  AML_QUESTIONNAIRE:
    "moduleDefinitions.verifications.verificationDetail.steps.stepType.amlQuestionnaire",
  EMAIL_VERIFICATION:
    "moduleDefinitions.verifications.verificationDetail.steps.stepType.emailVerification",
  PHONE_VERIFICATION:
    "moduleDefinitions.verifications.verificationDetail.steps.stepType.phoneVerification",
};

const STEP_TYPE_ICONS = {
  PERSONAL_INFO: User,
  DOC_OCR: FileText,
  FACE_MATCH: UserCheck,
  LIVENESS: Eye,
  AML_SCREEN: ShieldCheck,
  AML_QUESTIONNAIRE: ClipboardList,
  EMAIL_VERIFICATION: Mail,
  PHONE_VERIFICATION: Phone,
};

const CHECK_TYPE_LABEL_KEYS = {
  PERSONAL_INFO:
    "moduleDefinitions.verifications.verificationDetail.steps.checkType.personalInfo",
  DOC_DATA_MATCH:
    "moduleDefinitions.verifications.verificationDetail.steps.checkType.docDataMatch",
  DOC_OCR:
    "moduleDefinitions.verifications.verificationDetail.steps.checkType.docOcr",
  FACE_MATCH:
    "moduleDefinitions.verifications.verificationDetail.steps.checkType.faceMatch",
  LIVENESS:
    "moduleDefinitions.verifications.verificationDetail.steps.checkType.liveness",
  SANCTIONS:
    "moduleDefinitions.verifications.verificationDetail.steps.checkType.sanctions",
  PEP: "moduleDefinitions.verifications.verificationDetail.steps.checkType.pep",
  EMAIL_VERIFICATION:
    "moduleDefinitions.verifications.verificationDetail.steps.checkType.emailVerification",
  PHONE_VERIFICATION:
    "moduleDefinitions.verifications.verificationDetail.steps.checkType.phoneVerification",
  AML_QUESTIONNAIRE:
    "moduleDefinitions.verifications.verificationDetail.steps.checkType.amlQuestionnaire",
};

const COUNTRY_NAMES = {
  CZE: "Czech Republic",
};

function formatDocDate(value) {
  if (!value) return null;
  const date = new Date(value);
  if (isNaN(date.getTime())) return value;
  return date.toLocaleDateString("cs-CZ");
}

function DocField({
  label,
  value,
  mono = false,
  labelClassName = "",
  valueClassName = "",
}) {
  if (!value) return null;
  return (
    <div>
      <div
        className={`text-[9px] font-medium tracking-[0.2em] uppercase mb-0.5 text-slate-700 ${labelClassName}`}
      >
        {label}
      </div>
      <div
        className={`text-xs leading-tight text-slate-900 ${mono ? "font-mono tracking-wide" : "font-medium"} ${valueClassName}`}
      >
        {value}
      </div>
    </div>
  );
}

function PhotoPlaceholder({ className }) {
  return (
    <div
      className={`bg-white/10 rounded border border-white/20 flex items-center justify-center shrink-0 ${className}`}
    >
      <User className="w-1/2 h-1/2 text-white/25" />
    </div>
  );
}

function PassportCard({ identity }) {
  const { t } = useTranslation();
  const countryName =
    COUNTRY_NAMES[identity.issuingCountry] || identity.issuingCountry || "";
  const sex = (identity.sex || "X").toUpperCase();

  return (
    <div className="w-[360px] h-[228px] bg-gradient-to-br from-sky-100 via-indigo-100 to-rose-100 rounded-xl overflow-hidden shadow-xl text-slate-900 border border-indigo-300/80">
      <div className="px-3 pt-2 pb-1.5 bg-gradient-to-r from-indigo-200/80 via-sky-200/70 to-rose-200/70 border-b border-indigo-300/70 flex items-center justify-between">
        <div className="text-[8px] font-semibold tracking-[0.2em] text-slate-800 uppercase">
          {t(
            "moduleDefinitions.verifications.verificationDetail.clientIdentity.passport",
          )}
        </div>
        <div className="text-[8px] font-semibold tracking-[0.2em] text-slate-800 uppercase">
          {countryName}
        </div>
      </div>

      <div className="px-3 py-2 flex gap-2.5">
        <PhotoPlaceholder className="w-[92px] h-[118px] bg-indigo-200/60 border-indigo-400/60" />

        <div className="flex-1 min-w-0 flex flex-col gap-1.5">
          <div className="grid grid-cols-2 gap-2">
            <DocField
              label={t(
                "moduleDefinitions.verifications.verificationDetail.clientIdentity.fields.surname",
              )}
              value={identity.lastName}
              valueClassName="truncate text-sm font-semibold"
            />
            <DocField
              label={t(
                "moduleDefinitions.verifications.verificationDetail.clientIdentity.fields.passportNo",
              )}
              value={identity.documentNumber}
              mono
              valueClassName="text-sm font-semibold"
            />
          </div>
          <div className="grid grid-cols-2 gap-2">
            <DocField
              label={t(
                "moduleDefinitions.verifications.verificationDetail.clientIdentity.fields.givenNames",
              )}
              value={identity.firstName}
              valueClassName="truncate text-sm font-semibold"
            />
            <DocField
              label={t(
                "moduleDefinitions.verifications.verificationDetail.clientIdentity.fields.personalNo",
              )}
              value={identity.nationalNumber}
              mono
              valueClassName="text-sm font-semibold"
            />
          </div>
          <div className="grid grid-cols-3 gap-8">
            <DocField
              label={t(
                "moduleDefinitions.verifications.verificationDetail.clientIdentity.fields.nationality",
              )}
              value={identity.issuingCountry}
              valueClassName="text-sm font-semibold"
            />
            <DocField
              label={t(
                "moduleDefinitions.verifications.verificationDetail.clientIdentity.fields.sex",
              )}
              value={sex}
              valueClassName="text-sm font-semibold"
            />
            <DocField
              label={t(
                "moduleDefinitions.verifications.verificationDetail.clientIdentity.fields.type",
              )}
              value={t(
                "moduleDefinitions.verifications.verificationDetail.clientIdentity.values.passport",
              )}
              valueClassName="text-sm font-semibold"
            />
          </div>
          <div className="grid grid-cols-2 gap-2">
            <DocField
              label={t(
                "moduleDefinitions.verifications.verificationDetail.clientIdentity.fields.dateOfBirth",
              )}
              value={formatDocDate(identity.dateOfBirth)}
              valueClassName="text-sm font-semibold"
            />
            <DocField
              label={t(
                "moduleDefinitions.verifications.verificationDetail.clientIdentity.fields.expiry",
              )}
              value={formatDocDate(identity.documentExpiresAt)}
              valueClassName="text-sm font-semibold"
            />
          </div>
        </div>
      </div>
    </div>
  );
}

function IdCardFront({ identity }) {
  const { t } = useTranslation();
  const countryName =
    COUNTRY_NAMES[identity.issuingCountry] || identity.issuingCountry || "";
  const sex = (identity.sex || "").toUpperCase();

  return (
    <div className="w-[340px] min-h-[214px] bg-gradient-to-br from-sky-100 via-indigo-100 to-rose-100 rounded-xl overflow-hidden shadow-xl text-slate-900 border border-indigo-300/80">
      <div className="h-10 px-3 pt-2 pb-1 border-b border-indigo-300/70 flex items-center justify-between bg-white/25">
        <div>
          <div className="text-[8px] font-semibold tracking-[0.16em] text-slate-800 uppercase">
            {t(
              "moduleDefinitions.verifications.verificationDetail.clientIdentity.czechRepublic",
            )}
          </div>
          <div className="text-[7px] font-medium tracking-[0.12em] text-slate-600 uppercase">
            {t(
              "moduleDefinitions.verifications.verificationDetail.clientIdentity.identityCard",
            )}
          </div>
        </div>
        <div className="text-[8px] font-semibold tracking-[0.16em] text-slate-700 uppercase">
          {t(
            "moduleDefinitions.verifications.verificationDetail.clientIdentity.front",
          )}
        </div>
      </div>

      <div className="px-3 py-2 flex gap-2.5">
        <PhotoPlaceholder className="w-[96px] h-[112px] bg-indigo-200/60 border-indigo-400/70" />
        <div className="flex-1 flex flex-col gap-1 min-w-0 overflow-hidden">
          <DocField
            label={t(
              "moduleDefinitions.verifications.verificationDetail.clientIdentity.fields.surname",
            )}
            value={identity.lastName}
            valueClassName="font-semibold text-xs"
          />
          <DocField
            label={t(
              "moduleDefinitions.verifications.verificationDetail.clientIdentity.fields.givenNames",
            )}
            value={identity.firstName}
            valueClassName="font-semibold text-xs"
          />
          <div className="grid grid-cols-2 gap-2">
            <DocField
              label={t(
                "moduleDefinitions.verifications.verificationDetail.clientIdentity.fields.dateOfBirth",
              )}
              value={formatDocDate(identity.dateOfBirth)}
              valueClassName="font-semibold text-xs"
            />
            <DocField
              label={t(
                "moduleDefinitions.verifications.verificationDetail.clientIdentity.fields.sex",
              )}
              value={sex}
              valueClassName="font-semibold text-xs"
            />
          </div>
          <div className="grid grid-cols-2 gap-2">
            <DocField
              label={t(
                "moduleDefinitions.verifications.verificationDetail.clientIdentity.fields.cardNo",
              )}
              value={identity.documentNumber}
              mono
              valueClassName="font-semibold text-xs"
            />
            <DocField
              label={t(
                "moduleDefinitions.verifications.verificationDetail.clientIdentity.fields.validUntil",
              )}
              value={formatDocDate(identity.documentExpiresAt)}
              valueClassName="font-semibold text-xs"
            />
          </div>
          <DocField
            label={t(
              "moduleDefinitions.verifications.verificationDetail.clientIdentity.fields.nationality",
            )}
            value={countryName}
            valueClassName="font-semibold text-xs truncate"
          />
        </div>
      </div>
    </div>
  );
}

function IdCardBack({ identity }) {
  const { t } = useTranslation();
  return (
    <div className="w-[340px] min-h-[214px] bg-gradient-to-br from-sky-100 via-indigo-100 to-rose-100 rounded-xl overflow-hidden shadow-xl text-slate-900 border border-indigo-300/80 flex flex-col">
      <div className="h-10 px-3 pt-2 pb-1 border-b border-indigo-300/70 flex items-center justify-between bg-white/25">
        <div>
          <div className="text-[8px] font-semibold tracking-[0.16em] text-slate-800 uppercase">
            {t(
              "moduleDefinitions.verifications.verificationDetail.clientIdentity.czechRepublic",
            )}
          </div>
          <div className="text-[7px] font-medium tracking-[0.12em] text-slate-600 uppercase">
            {t(
              "moduleDefinitions.verifications.verificationDetail.clientIdentity.identityCard",
            )}
          </div>
        </div>
        <div className="text-[8px] font-semibold tracking-[0.16em] text-slate-700 uppercase">
          {t(
            "moduleDefinitions.verifications.verificationDetail.clientIdentity.back",
          )}
        </div>
      </div>

      <div className="px-3 py-2 flex-1 flex flex-col gap-1.5">
        <DocField
          label={t(
            "moduleDefinitions.verifications.verificationDetail.clientIdentity.fields.placeOfBirth",
          )}
          value={identity.placeOfBirth}
          valueClassName="font-semibold text-xs"
        />
        <DocField
          label={t(
            "moduleDefinitions.verifications.verificationDetail.clientIdentity.fields.nationalNo",
          )}
          value={identity.nationalNumber}
          mono
          valueClassName="font-semibold text-xs"
        />
        <DocField
          label={t(
            "moduleDefinitions.verifications.verificationDetail.clientIdentity.fields.address",
          )}
          value={identity.address}
          valueClassName="font-semibold text-xs break-words"
        />
        <DocField
          label={t(
            "moduleDefinitions.verifications.verificationDetail.clientIdentity.fields.countryOfResidence",
          )}
          value={identity.countryOfResidence}
          valueClassName="font-semibold text-xs"
        />
      </div>
    </div>
  );
}

export function VerificationClientIdentitySection({ entity }) {
  const { t } = useTranslation();
  const identity = entity?.clientIdentity;
  if (!identity) return null;

  const category = identity.documentType;
  const docTypeLabel = identity.documentType?.replace(/_/g, " ");

  return (
    <Card>
      <CardHeader>
        <CardTitle className="flex items-center gap-2">
          {t(
            "moduleDefinitions.verifications.verificationDetail.clientIdentity.title",
          )}
          {docTypeLabel && (
            <Badge variant="outline" className="font-mono text-xs font-normal">
              {docTypeLabel}
            </Badge>
          )}
        </CardTitle>
      </CardHeader>
      <CardContent>
        {category === "PASSPORT" && <PassportCard identity={identity} />}
        {category === "CZECH_ID" && (
          <div className="flex gap-3">
            <div className="flex justify-center">
              <IdCardFront identity={identity} />
            </div>
            <div className="flex justify-center">
              <IdCardBack identity={identity} />
            </div>
          </div>
        )}
      </CardContent>
    </Card>
  );
}

function VerificationStep({ step }) {
  const { t } = useTranslation();
  const [open, setOpen] = useState(false);
  const Icon = STEP_TYPE_ICONS[step.stepType];
  const hasChecks = step.checkResults?.length > 0;
  const stepTypeLabelKey = STEP_TYPE_LABEL_KEYS[step.stepType];
  const stepTypeLabel = stepTypeLabelKey ? t(stepTypeLabelKey) : step.stepType;
  const stepStatusLabel = t(
    `moduleDefinitions.verifications.verificationDetail.steps.stepStatus.${step.status}`,
    {
      defaultValue: step.status,
    },
  );

  return (
    <Collapsible open={open} onOpenChange={setOpen} disabled={!hasChecks}>
      <div className="border rounded-lg overflow-hidden">
        <CollapsibleTrigger asChild>
          <button
            type="button"
            className="w-full flex items-center justify-between px-4 py-3 text-left hover:bg-muted/40 transition-colors disabled:cursor-default"
            disabled={!hasChecks}
          >
            <div className="flex items-center gap-2">
              {Icon && (
                <Icon className="size-4 text-muted-foreground shrink-0" />
              )}
              <span className="font-medium text-sm">{stepTypeLabel}</span>
            </div>
            <div className="flex items-center gap-2">
              <Badge status={STEP_STATUS_MAP[step.status] ?? "default"}>
                {stepStatusLabel}
              </Badge>
              {hasChecks && (
                <ChevronDown
                  className="size-4 text-muted-foreground transition-transform duration-200"
                  style={{
                    transform: open ? "rotate(180deg)" : "rotate(0deg)",
                  }}
                />
              )}
            </div>
          </button>
        </CollapsibleTrigger>
        {hasChecks && (
          <CollapsibleContent>
            <div className="flex flex-col border-t px-4 py-2 gap-0.5">
              {step.checkResults.map((check) => (
                <div
                  key={check.id}
                  className="flex items-center justify-between text-sm py-1.5"
                >
                  <span className="text-muted-foreground">
                    {CHECK_TYPE_LABEL_KEYS[check.checkType]
                      ? t(CHECK_TYPE_LABEL_KEYS[check.checkType])
                      : check.checkType}
                  </span>
                  <div className="flex items-center gap-2">
                    {check.score != null && (
                      <span className="text-xs text-muted-foreground">
                        {t(
                          "moduleDefinitions.verifications.verificationDetail.steps.score",
                        )}
                        : {(check.score * 100).toFixed(0)}%
                      </span>
                    )}
                    <Badge status={CHECK_STATUS_MAP[check.status] ?? "default"}>
                      {t(
                        `moduleDefinitions.verifications.verificationDetail.steps.checkStatus.${check.status}`,
                        { defaultValue: check.status },
                      )}
                    </Badge>
                  </div>
                </div>
              ))}
            </div>
          </CollapsibleContent>
        )}
      </div>
    </Collapsible>
  );
}

export function VerificationStepsSection({ entity }) {
  const { t } = useTranslation();
  const steps = entity?.steps;
  if (!steps?.length) return null;

  return (
    <Card>
      <CardHeader>
        <CardTitle>
          {t("moduleDefinitions.verifications.verificationDetail.steps.title")}
        </CardTitle>
      </CardHeader>
      <CardContent>
        <div className="flex flex-col gap-2">
          {steps.map((step) => (
            <VerificationStep key={step.id} step={step} />
          ))}
        </div>
      </CardContent>
    </Card>
  );
}

// Skóre 0–100: LOW < 33, MEDIUM < 66, HIGH >= 66
const RISK_LEVEL_STATUS_MAP = {
  LOW: "success",
  MEDIUM: "queued",
  HIGH: "failed",
};

const RISK_LEVEL_COLOR = {
  LOW: "var(--color-success, #22c55e)",
  MEDIUM: "var(--color-warning, #f59e0b)",
  HIGH: "var(--color-destructive, #ef4444)",
};

export function VerificationRiskScoreSection({ entity }) {
  const { t } = useTranslation();
  const riskScore = entity?.riskScore;
  if (!riskScore) return null;

  const checks = riskScore.breakdownJson?.checks ?? [];
  const weightedAverage = riskScore.breakdownJson?.weightedAverage;
  const score = riskScore.overallScore ?? 0;
  const gaugeColor = RISK_LEVEL_COLOR[riskScore.level] ?? RISK_LEVEL_COLOR.HIGH;

  const chartConfig = {
    score: {
      label: t(
        "moduleDefinitions.verifications.verificationDetail.riskScore.overallScore",
      ),
    },
  };

  // Půlkruhový ukazatel — score/100 určuje výplň oblouku.
  const chartData = [{ score }];

  return (
    <Card>
      <CardHeader>
        <CardTitle>
          {t(
            "moduleDefinitions.verifications.verificationDetail.riskScore.title",
          )}
        </CardTitle>
      </CardHeader>
      <CardContent>
        <div className="flex justify-center">
          <div className="flex flex-col sm:flex-row items-center gap-6 mb-4">
            {/* Half-circle gauge — height = outerRadius so the arc sits flush at the bottom */}
            <ChartContainer
              config={chartConfig}
              className="w-[220px] h-[120px] shrink-0 overflow-visible pb-4"
            >
              <RadialBarChart
                data={chartData}
                startAngle={180}
                endAngle={0}
                cx="50%"
                cy="100%"
                innerRadius={75}
                outerRadius={110}
              >
                <PolarAngleAxis
                  type="number"
                  domain={[0, 100]}
                  angleAxisId={0}
                  tick={false}
                />
                <RadialBar
                  dataKey="score"
                  angleAxisId={0}
                  background={{ fill: "var(--muted, #e2e8f0)" }}
                  cornerRadius={6}
                  fill={gaugeColor}
                  className="stroke-transparent stroke-2"
                />
                <PolarRadiusAxis tick={false} tickLine={false} axisLine={false}>
                  <Label
                    content={({ viewBox }) => {
                      if (viewBox && "cx" in viewBox && "cy" in viewBox) {
                        return (
                          <text
                            x={viewBox.cx}
                            y={viewBox.cy}
                            textAnchor="middle"
                          >
                            <tspan
                              x={viewBox.cx}
                              y={(viewBox.cy || 0) - 22}
                              style={{
                                fontSize: "1.6rem",
                                fontWeight: 700,
                                fill: "currentColor",
                              }}
                            >
                              {score}
                            </tspan>
                            <tspan
                              x={viewBox.cx}
                              y={(viewBox.cy || 0) - 8}
                              style={{
                                fontSize: "0.7rem",
                                fill: "var(--muted-foreground, #64748b)",
                              }}
                            >
                              / 100
                            </tspan>
                          </text>
                        );
                      }
                    }}
                  />
                </PolarRadiusAxis>
              </RadialBarChart>
            </ChartContainer>

            {/* Level + weighted average */}
            <div className="flex flex-col gap-3">
              <div>
                <div className="text-xs text-muted-foreground mb-1">
                  {t(
                    "moduleDefinitions.verifications.verificationDetail.riskScore.level",
                  )}
                </div>
                <Badge
                  status={RISK_LEVEL_STATUS_MAP[riskScore.level] ?? "default"}
                >
                  {t(
                    `moduleDefinitions.verifications.verificationDetail.riskScore.levels.${riskScore.level}`,
                    { defaultValue: riskScore.level },
                  )}
                </Badge>
              </div>
              {weightedAverage != null && (
                <div>
                  <div className="text-xs text-muted-foreground mb-0.5">
                    {t(
                      "moduleDefinitions.verifications.verificationDetail.riskScore.weightedAverage",
                    )}
                  </div>
                  <div className="text-sm font-semibold tabular-nums">
                    {(weightedAverage * 100).toFixed(1)}%
                  </div>
                </div>
              )}
            </div>
          </div>
        </div>

        {checks.length > 0 && (
          <div className="flex flex-col border rounded-lg overflow-hidden">
            {checks.map((check, i) => (
              <div
                key={check.checkType + i}
                className="flex items-center justify-between px-4 py-2.5 text-sm border-b last:border-b-0"
              >
                <span className="text-muted-foreground">
                  {CHECK_TYPE_LABEL_KEYS[check.checkType]
                    ? t(CHECK_TYPE_LABEL_KEYS[check.checkType])
                    : check.checkType}
                </span>
                <div className="flex items-center gap-2">
                  {check.score != null && (
                    <span className="text-xs text-muted-foreground">
                      {t(
                        "moduleDefinitions.verifications.verificationDetail.steps.score",
                      )}
                      : {(check.score * 100).toFixed(0)}%
                    </span>
                  )}
                  <Badge status={CHECK_STATUS_MAP[check.status] ?? "default"}>
                    {t(
                      `moduleDefinitions.verifications.verificationDetail.steps.checkStatus.${check.status}`,
                      { defaultValue: check.status },
                    )}
                  </Badge>
                </div>
              </div>
            ))}
          </div>
        )}
      </CardContent>
    </Card>
  );
}
