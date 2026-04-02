import { useState } from "react";
import { Controller } from "react-hook-form";
import { useTranslation } from "react-i18next";
import { Check, Copy } from "lucide-react";
import Card from "@/views/Card";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Combobox } from "@/components/ui/combobox";
import { DatePicker } from "@/components/ui/date-picker";
import { RelationFieldController } from "@/views/shared/RelationField";
import { cn } from "@/lib/utils";

function CopyableField({ label, value }) {
  const [copied, setCopied] = useState(false);

  function handleCopy() {
    navigator.clipboard.writeText(String(value ?? "")).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    });
  }

  return (
    <div className="flex flex-col gap-1.5">
      {label && (
        <label className="text-sm font-medium leading-none">{label}</label>
      )}
      <div
        onClick={handleCopy}
        title={copied ? "Zkopírováno!" : "Kliknutím zkopírovat"}
        className="inline-flex items-center justify-between gap-2 cursor-pointer rounded-md border bg-muted px-3 py-2 text-sm font-mono text-muted-foreground transition-colors hover:bg-muted/70 select-none"
      >
        <span className="truncate">{value}</span>
        {copied
          ? <Check className="size-3.5 text-green-500 shrink-0" />
          : <Copy className="size-3.5 shrink-0" />
        }
      </div>
    </div>
  );
}

function normalizeOptions(options) {
  if (!Array.isArray(options)) return [];
  return options.map((option) => {
    if (typeof option === "string") {
      return { value: option, label: option };
    }
    return option;
  });
}

function padTwo(value) {
  return String(value).padStart(2, "0");
}

function toDatetimeLocalValue(value) {
  if (!value) return "";

  if (typeof value === "string") {
    if (/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}/.test(value)) {
      return value.slice(0, 16);
    }
    const parsed = new Date(value);
    if (Number.isNaN(parsed.getTime())) return "";
    const year = parsed.getFullYear();
    const month = padTwo(parsed.getMonth() + 1);
    const day = padTwo(parsed.getDate());
    const hour = padTwo(parsed.getHours());
    const minute = padTwo(parsed.getMinutes());
    return `${year}-${month}-${day}T${hour}:${minute}`;
  }

  if (value instanceof Date) {
    if (Number.isNaN(value.getTime())) return "";
    const year = value.getFullYear();
    const month = padTwo(value.getMonth() + 1);
    const day = padTwo(value.getDate());
    const hour = padTwo(value.getHours());
    const minute = padTwo(value.getMinutes());
    return `${year}-${month}-${day}T${hour}:${minute}`;
  }

  return "";
}

function renderField({
  field,
  register,
  control,
  setValue,
  readOnly,
  tr,
}) {
  if (typeof field.render === "function") {
    return field.render({ field, register, control, setValue, readOnly });
  }

  if (field.type === "copy") {
    return (
      <Controller
        name={field.name}
        control={control}
        render={({ field: controllerField }) => (
          <CopyableField label={tr(field.label)} value={controllerField.value} />
        )}
      />
    );
  }

  if (field.type === "relation") {
    return (
      <RelationFieldController
        field={field}
        control={control}
        setValue={setValue}
        readOnly={readOnly}
      />
    );
  }

  if (field.type === "textarea") {
    return (
      <Textarea
        label={tr(field.label)}
        readOnly={readOnly}
        required={field.required}
        {...(field.name
          ? register(field.name, field.registerOptions || {})
          : {})}
        {...field.props}
      />
    );
  }

  if (field.type === "enum") {
    return (
      <Controller
        name={field.name}
        control={control}
        rules={field.rules}
        render={({ field: controllerField }) => (
          <Combobox
            value={controllerField.value}
            onChange={controllerField.onChange}
            label={tr(field.label)}
            readOnly={readOnly}
            required={field.required}
            options={normalizeOptions(field.options).map((option) => ({
              ...option,
              label: tr(option.label),
            }))}
            {...field.props}
          />
        )}
      />
    );
  }

  if (field.type === "date") {
    return (
      <Controller
        name={field.name}
        control={control}
        rules={field.rules}
        render={({ field: controllerField, fieldState }) => (
          <DatePicker
            id={field.name}
            label={tr(field.label)}
            value={controllerField.value}
            onChange={controllerField.onChange}
            readOnly={readOnly}
            error={fieldState.error?.message}
            {...field.props}
          />
        )}
      />
    );
  }

  if (field.type === "datetime" || field.type === "date_time") {
    return (
      <Controller
        name={field.name}
        control={control}
        rules={field.rules}
        render={({ field: controllerField }) => (
          <Input
            label={tr(field.label)}
            type="datetime-local"
            readOnly={readOnly}
            required={field.required}
            value={toDatetimeLocalValue(controllerField.value)}
            onChange={(event) =>
              controllerField.onChange(event.target.value || null)
            }
            {...field.props}
          />
        )}
      />
    );
  }

  return (
    <Input
      label={tr(field.label)}
      readOnly={readOnly}
      required={field.required}
      type={field.type || "text"}
      {...(field.name ? register(field.name, field.registerOptions || {}) : {})}
      {...field.props}
    />
  );
}

export default function DetailFieldsSection({
  title,
  description,
  fields = [],
  register,
  control,
  setValue,
  readOnly = false,
  columns = 2,
  className,
  children,
  translateValue,
}) {
  const { t } = useTranslation();
  const tr =
    typeof translateValue === "function"
      ? translateValue
      : (value) =>
          typeof value === "string" ? t(value, { defaultValue: value }) : value;

  return (
    <Card className={className}>
      <div className="space-y-4 p-4">
        {title && (
          <div>
            <h3 className="text-base font-semibold">{tr(title)}</h3>
            {description && (
              <p className="text-sm text-muted-foreground">{tr(description)}</p>
            )}
          </div>
        )}

        <div
          className={cn(
            "grid gap-4",
            columns === 1 ? "grid-cols-1" : "grid-cols-2",
          )}
        >
          {fields.map((field) => (
            <div
              key={field.key || field.name || field.label}
              className={cn(field.fullWidth && "col-span-full")}
            >
              {renderField({
                field,
                register,
                setValue,
                control,
                readOnly,
                tr,
              })}
            </div>
          ))}
        </div>

        {children}
      </div>
    </Card>
  );
}
