import { Controller } from "react-hook-form";
import Card from "@/views/Card";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { Combobox } from "@/components/ui/combobox";
import { DatePicker } from "@/components/ui/date-picker";
import { cn } from "@/lib/utils";

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
  readOnly,
}) {
  if (typeof field.render === "function") {
    return field.render({ field, register, control, readOnly });
  }

  if (field.type === "textarea") {
    return (
      <Textarea
        label={field.label}
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
            label={field.label}
            readOnly={readOnly}
            required={field.required}
            options={normalizeOptions(field.options)}
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
            label={field.label}
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
            label={field.label}
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
      label={field.label}
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
  readOnly = false,
  columns = 2,
  className,
  children,
}) {
  return (
    <Card className={className}>
      <div className="space-y-4 p-4">
        {title && (
          <div>
            <h3 className="text-base font-semibold">{title}</h3>
            {description && (
              <p className="text-sm text-muted-foreground">{description}</p>
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
                control,
                readOnly,
              })}
            </div>
          ))}
        </div>

        {children}
      </div>
    </Card>
  );
}
