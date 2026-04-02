import { cn } from "@/lib/utils";
import { useTranslation } from "react-i18next";
import {
  CheckCircle2,
  CircleDot,
  Clock,
  Eye,
  Play,
  ScanSearch,
  ShieldCheck,
  ShieldX,
  XCircle,
} from "lucide-react";

const REVIEW_PATH_STATUSES = new Set([
  "AUTO_FAILED",
  "REQUIRES_REVIEW",
  "APPROVED",
  "REJECTED",
]);

const AUTO_PATH = [
  {
    value: "INITIATED",
    labelKey: "moduleDefinitions.verifications.verificationDetail.statusTimeline.initiated",
    icon: CircleDot,
    color: "bg-blue-500",
  },
  {
    value: "IN_PROGRESS",
    labelKey: "moduleDefinitions.verifications.verificationDetail.statusTimeline.inProgress",
    icon: Play,
    color: "bg-fuchsia-500",
  },
  {
    value: "READY_FOR_AUTOCHECK",
    labelKey: "moduleDefinitions.verifications.verificationDetail.statusTimeline.autoCheck",
    icon: ScanSearch,
    color: "bg-amber-500",
  },
  {
    value: "AUTO_PASSED",
    labelKey: "moduleDefinitions.verifications.verificationDetail.statusTimeline.autoPassed",
    icon: CheckCircle2,
    color: "bg-green-600",
  },
];

const REVIEW_PATH_BASE = [
  {
    value: "INITIATED",
    labelKey: "moduleDefinitions.verifications.verificationDetail.statusTimeline.initiated",
    icon: CircleDot,
    color: "bg-blue-500",
  },
  {
    value: "IN_PROGRESS",
    labelKey: "moduleDefinitions.verifications.verificationDetail.statusTimeline.inProgress",
    icon: Play,
    color: "bg-fuchsia-500",
  },
  {
    value: "READY_FOR_AUTOCHECK",
    labelKey: "moduleDefinitions.verifications.verificationDetail.statusTimeline.autoCheck",
    icon: ScanSearch,
    color: "bg-amber-500",
  },
  {
    value: "AUTO_FAILED",
    labelKey: "moduleDefinitions.verifications.verificationDetail.statusTimeline.autoFailed",
    icon: XCircle,
    color: "bg-red-500",
  },
  {
    value: "REQUIRES_REVIEW",
    labelKey: "moduleDefinitions.verifications.verificationDetail.statusTimeline.inReview",
    icon: Eye,
    color: "bg-orange-500",
  },
];

const REVIEW_TERMINALS = {
  APPROVED: {
    value: "APPROVED",
    labelKey: "moduleDefinitions.verifications.verificationDetail.statusTimeline.approved",
    icon: ShieldCheck,
    color: "bg-green-600",
  },
  REJECTED: {
    value: "REJECTED",
    labelKey: "moduleDefinitions.verifications.verificationDetail.statusTimeline.rejected",
    icon: ShieldX,
    color: "bg-red-700",
  },
};

const SOLO_STATES = {
  EXPIRED: {
    labelKey: "moduleDefinitions.verifications.verificationDetail.statusTimeline.expired",
    icon: Clock,
    color: "bg-gray-500",
  },
};

export default function VerificationDetailStatusSteps({ status }) {
  const { t } = useTranslation();
  if (!status) return null;

  const solo = SOLO_STATES[status];
  if (solo) {
    const Icon = solo.icon;
    return (
      <div
        className={cn(
          "flex items-center justify-center gap-2 rounded-lg px-4 py-2",
          solo.color,
        )}
      >
        <Icon className="w-5 h-5 text-white" />
        <span className="text-sm font-medium text-white">
          {t(solo.labelKey)}
        </span>
      </div>
    );
  }

  const isReviewPath = REVIEW_PATH_STATUSES.has(status);
  const terminal = isReviewPath
    ? (REVIEW_TERMINALS[status] ?? REVIEW_TERMINALS.APPROVED)
    : null;
  const steps = isReviewPath ? [...REVIEW_PATH_BASE, terminal] : AUTO_PATH;

  const currentIndex = steps.findIndex((s) => s.value === status);

  const getState = (index) => {
    if (index < currentIndex) return "completed";
    if (index === currentIndex) return "current";
    return "upcoming";
  };

  return (
    <div className="w-full overflow-x-hidden px-6 pb-16 pt-1">
      <div className="flex items-center justify-between">
        {steps.map((step, index) => {
          const state = getState(index);
          const Icon = step.icon;
          const isLast = index === steps.length - 1;

          return (
            <div
              key={step.value}
              className={cn(
                "flex min-w-0 items-center",
                !isLast && "flex-1",
              )}
            >
              <div className="relative flex w-8 shrink-0 flex-col items-center">
                <div
                  className={cn(
                    "w-8 h-8 rounded-md flex items-center justify-center transition-all duration-300",
                    state === "current" && [
                      step.color,
                      "text-white shadow-lg scale-110",
                    ],
                    state === "completed" && "bg-neutral-700 text-neutral-300",
                    state === "upcoming" && "bg-neutral-800 text-neutral-500",
                  )}
                >
                  <Icon className="w-5 h-5" />
                </div>
                <div className="absolute left-1/2 top-10 w-32 -translate-x-1/2 text-center">
                  <p
                    className={cn(
                      "text-xs font-medium leading-tight break-words [overflow-wrap:anywhere] transition-colors duration-300",
                      state === "current" && "text-neutral-100",
                      state === "completed" && "text-neutral-300",
                      state === "upcoming" && "text-neutral-500",
                    )}
                  >
                    {t(step.labelKey)}
                  </p>
                </div>
              </div>
              {!isLast && (
                <div className="mx-2 flex-1">
                  <div
                    className={cn(
                      "h-0.5 transition-colors duration-300",
                      state === "completed"
                        ? "bg-neutral-600"
                        : "bg-neutral-800",
                    )}
                  />
                </div>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
}
