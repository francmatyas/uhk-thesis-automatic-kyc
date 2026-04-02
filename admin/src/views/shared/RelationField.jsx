import { useEffect, useState } from "react";
import { useWatch } from "react-hook-form";
import { useTranslation } from "react-i18next";
import { useParams } from "react-router";
import { ExternalLink, XIcon } from "lucide-react";
import { Label } from "@/components/ui/label";
import { Link } from "@/components/ui/link";
import { SearchSelect } from "@/components/ui/search-select";

function RelationDisplay({
  label,
  nameValue,
  idValue,
  detailPath,
  onClear,
  onChangeClick,
}) {
  const { t } = useTranslation();
  return (
    <div className="flex flex-col gap-1.5 pt-1">
      <Label className="ml-0.5 text-xs font-medium leading-none">{label}</Label>
      <div className="flex items-center justify-between gap-2 rounded-md border px-3 shadow-xs min-h-9 bg-transparent dark:bg-input/30">
        <div className="py-1.5 min-w-0">
          <div className="text-sm truncate">
            {nameValue || idValue || t("shared.relationField.empty")}
          </div>
          {/* {nameValue && idValue && (
            <div className="text-xs text-muted-foreground truncate">{idValue}</div>
          )} */}
        </div>
        <div className="flex items-center gap-1 shrink-0">
          {detailPath && idValue && (
            <Link
              to={detailPath}
              aria-label={t("shared.relationField.openDetail")}
              className="text-muted-foreground hover:text-foreground"
            >
              <ExternalLink className="size-4" />
            </Link>
          )}
          {onChangeClick && (
            <button
              type="button"
              onClick={onChangeClick}
              className="text-xs text-muted-foreground hover:text-foreground px-1 py-0.5 rounded hover:bg-muted/40"
            >
              {t("shared.relationField.change")}
            </button>
          )}
          {onClear && (
            <button
              type="button"
              onClick={onClear}
              aria-label={t("shared.relationField.clear")}
              className="text-muted-foreground hover:text-foreground"
            >
              <XIcon className="size-4" />
            </button>
          )}
        </div>
      </div>
    </div>
  );
}

function RelationEditField({
  field,
  idValue,
  nameValue,
  detailPath,
  onSelect,
  onClear,
}) {
  const { t } = useTranslation();
  const [searching, setSearching] = useState(!idValue);
  const translatedLabel =
    typeof field.label === "string"
      ? t(field.label, { defaultValue: field.label })
      : field.label;
  const translatedPlaceholder =
    typeof field.placeholder === "string"
      ? t(field.placeholder, { defaultValue: field.placeholder })
      : field.placeholder;

  useEffect(() => {
    if (!idValue) setSearching(true);
  }, [idValue]);

  if (!searching && idValue) {
    return (
      <RelationDisplay
        label={translatedLabel}
        nameValue={nameValue}
        idValue={idValue}
        detailPath={detailPath}
        onChangeClick={() => setSearching(true)}
        onClear={() => {
          onClear();
          setSearching(true);
        }}
      />
    );
  }

  return (
    <SearchSelect
      label={translatedLabel}
      endpoint={field.endpoint}
      formatter={field.formatter}
      queryMinLength={field.queryMinLength}
      placeholder={
        translatedPlaceholder || t("shared.relationField.searchPlaceholder")
      }
      onSelect={(raw, formatted) => {
        onSelect(raw, formatted);
        setSearching(false);
      }}
      onClear={() => {
        if (idValue) setSearching(false);
        else onClear();
      }}
      {...field.searchSelectProps}
    />
  );
}

export function RelationFieldController({
  field,
  control,
  setValue,
  readOnly,
}) {
  const { t } = useTranslation();
  const params = useParams();
  const idValue = useWatch({ control, name: field.idField });
  const nameValue = useWatch({ control, name: field.nameField });
  const translatedLabel =
    typeof field.label === "string"
      ? t(field.label, { defaultValue: field.label })
      : field.label;

  const detailPath =
    field.getDetailPath && idValue
      ? field.getDetailPath(idValue, params)
      : null;

  if (readOnly) {
    return (
      <RelationDisplay
        label={translatedLabel}
        nameValue={nameValue}
        idValue={idValue}
        detailPath={detailPath}
      />
    );
  }

  return (
    <RelationEditField
      field={field}
      idValue={idValue}
      nameValue={nameValue}
      detailPath={detailPath}
      onSelect={(raw, formatted) => {
        setValue(field.idField, formatted.value, { shouldDirty: true });
        setValue(field.nameField, formatted.label, { shouldDirty: true });
      }}
      onClear={() => {
        setValue(field.idField, null, { shouldDirty: true });
        setValue(field.nameField, null, { shouldDirty: true });
      }}
    />
  );
}
