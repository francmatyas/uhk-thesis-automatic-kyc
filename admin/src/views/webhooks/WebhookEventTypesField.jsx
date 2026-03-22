import { Controller } from "react-hook-form";
import { Label } from "@/components/ui/label";
import { Checkbox } from "@/components/ui/checkbox";

export default function WebhookEventTypesField({
  control,
  name = "eventTypes",
  label = "Event Types",
  options = [],
  readOnly = false,
}) {
  return (
    <Controller
      name={name}
      control={control}
      render={({ field }) => {
        const selectedValues = Array.isArray(field.value) ? field.value : [];
        const availableOptions = Array.isArray(options) ? options : [];

        const toggle = (value) => {
          if (readOnly) return;
          const hasValue = selectedValues.includes(value);
          const next = hasValue
            ? selectedValues.filter((item) => item !== value)
            : [...selectedValues, value];
          field.onChange(next);
        };

        return (
          <div className="flex flex-col gap-2">
            <Label className="ml-0.5 text-xs font-medium">{label}</Label>
            <div className="rounded-md border p-2">
              {availableOptions.length === 0 && (
                <div className="text-sm text-muted-foreground">
                  No event type options available.
                </div>
              )}
              {availableOptions.length > 0 && (
                <div className="flex flex-col gap-2">
                  {availableOptions.map((eventType) => (
                    <label
                      key={eventType}
                      className="flex items-center gap-2 text-sm"
                    >
                      <Checkbox
                        checked={selectedValues.includes(eventType)}
                        onCheckedChange={() => toggle(eventType)}
                        disabled={readOnly}
                      />
                      <span>{eventType}</span>
                    </label>
                  ))}
                </div>
              )}
            </div>
          </div>
        );
      }}
    />
  );
}

