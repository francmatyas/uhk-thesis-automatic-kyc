import { Controller } from "react-hook-form";
import { cn } from "@/lib/utils";
import { Label } from "@/components/ui/label";
import { Tag, TagContainer } from "@/components/ui/tag";

const ALL_CHECKS = ["DOC_OCR", "LIVENESS", "SANCTIONS", "PEP"];

const CHECK_META = {
  DOC_OCR: { label: "Document OCR", color: "blue" },
  LIVENESS: { label: "Liveness", color: "green" },
  SANCTIONS: { label: "Sanctions", color: "red" },
  PEP: { label: "PEP", color: "orange" },
};

export function ConfigJsonEditor({ control, readOnly }) {
  return (
    <Controller
      name="configJson"
      control={control}
      render={({ field }) => {
        const configJson = field.value || {};
        const checks = Array.isArray(configJson.checks) ? configJson.checks : [];

        const toggleCheck = (check) => {
          const next = checks.includes(check)
            ? checks.filter((c) => c !== check)
            : [...checks, check];
          field.onChange({ ...configJson, checks: next });
        };

        if (readOnly) {
          return (
            <div className="space-y-1.5">
              <Label className="text-sm font-medium">Checks</Label>
              <TagContainer>
                {checks.length === 0 ? (
                  <span className="text-sm text-muted-foreground">
                    No checks configured
                  </span>
                ) : (
                  checks.map((check) => {
                    const meta = CHECK_META[check];
                    return (
                      <Tag
                        key={check}
                        label={meta?.label ?? check}
                        color={meta?.color ?? "neutral"}
                      />
                    );
                  })
                )}
              </TagContainer>
            </div>
          );
        }

        return (
          <div className="space-y-1.5">
            <Label className="text-sm font-medium">Checks</Label>
            <div className="flex flex-wrap gap-2">
              {ALL_CHECKS.map((check) => {
                const meta = CHECK_META[check];
                const enabled = checks.includes(check);
                return (
                  <button
                    key={check}
                    type="button"
                    onClick={() => toggleCheck(check)}
                    className={cn(
                      "inline-flex items-center rounded-md px-3 py-1.5 text-sm font-medium border transition-colors cursor-pointer select-none",
                      enabled
                        ? "bg-primary text-primary-foreground border-primary"
                        : "bg-background text-muted-foreground border-input hover:border-ring hover:text-foreground",
                    )}
                  >
                    {meta?.label ?? check}
                  </button>
                );
              })}
            </div>
            <p className="text-xs text-muted-foreground">
              Click to toggle checks on or off
            </p>
          </div>
        );
      }}
    />
  );
}
