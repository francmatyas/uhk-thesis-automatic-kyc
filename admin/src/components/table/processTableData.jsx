import { useState } from "react";
import { Link } from "react-router";
import { Check, Copy } from "lucide-react";
import { Image } from "@/components/ui/image";
import { Badge } from "@/components/ui/badge";
import { Checkbox } from "@/components/ui/checkbox";

function CopyableCell({ value, children, className = "" }) {
  const [copied, setCopied] = useState(false);

  function handleCopy(e) {
    e.stopPropagation();
    navigator.clipboard.writeText(String(value ?? "")).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    });
  }

  return (
    <span
      onClick={handleCopy}
      title={copied ? "Zkopírováno!" : "Kliknutím zkopírovat"}
      className={`inline-flex items-center gap-1.5 cursor-pointer select-none rounded px-1.5 py-0.5 bg-muted transition-colors hover:bg-muted/70 ${className}`}
    >
      {children ?? value}
      {copied
        ? <Check className="size-3 text-green-500 shrink-0" />
        : <Copy className="size-3 text-muted-foreground shrink-0" />
      }
    </span>
  );
}

function isEmptyDateValue(value) {
  return value === null || value === undefined || value === "";
}

function parseSafeDate(value) {
  if (isEmptyDateValue(value)) return null;
  const parsed = new Date(value);
  return Number.isNaN(parsed.getTime()) ? null : parsed;
}

function resolveEnumOption(column, value) {
  const options = Array.isArray(column.enumValues)
    ? column.enumValues
    : Array.isArray(column.options)
      ? column.options
      : [];

  return options.find(
    (option) => option?.value === value || String(option?.value) === String(value)
  );
}

function resolveEnumLabel(column, value, option) {
  if (typeof column.translate === "function") {
    return column.translate(value, option);
  }

  if (column.translateMap && Object.prototype.hasOwnProperty.call(column.translateMap, value)) {
    return column.translateMap[value];
  }

  if (option?.label !== undefined && option?.label !== null) {
    return option.label;
  }

  return value;
}

export default function processTableData({
  columns,
  rows,
  enableRowSelection,
  context = {},
}) {
  const tr =
    typeof context.translate === "function"
      ? context.translate
      : (value) => value;
  const hasTranslation =
    typeof context.hasTranslation === "function"
      ? context.hasTranslation
      : () => false;
  const resolveScopedLegacyLabel = (legacyKey, bucket = "columns") => {
    if (typeof legacyKey !== "string") return null;
    if (!legacyKey.startsWith("moduleDefinitions.labels.")) return null;

    const moduleKey =
      typeof context.module === "string" && context.module.length > 0
        ? context.module
        : null;
    if (!moduleKey) return null;

    const token = legacyKey.slice("moduleDefinitions.labels.".length);
    if (!token) return null;

    const scopedKey = `moduleDefinitions.${moduleKey}.${bucket}.${token}`;
    if (!hasTranslation(scopedKey)) return null;
    return tr(scopedKey);
  };
  const resolveHeaderLabel = (headerKey) => {
    if (typeof headerKey !== "string") return headerKey;
    if (headerKey.includes(".")) {
      const scopedLegacy = resolveScopedLegacyLabel(headerKey, "columns");
      return scopedLegacy ?? tr(headerKey);
    }

    const moduleKey =
      typeof context.module === "string" && context.module.length > 0
        ? context.module
        : null;

    if (moduleKey) {
      const moduleScopedKey = `moduleDefinitions.${moduleKey}.columns.${headerKey}`;
      if (hasTranslation(moduleScopedKey)) return tr(moduleScopedKey);
    }

    const legacyKey = `moduleDefinitions.labels.${headerKey}`;
    if (hasTranslation(legacyKey)) return tr(legacyKey);

    return tr(headerKey);
  };

  const filteredColumns = columns.map((column) => {
    const columnConfig =
      context.enumConfig?.[column.accessorKey] ||
      context.enumConfig?.[column.id] ||
      {};
    const effectiveColumn = { ...column, ...columnConfig };
    const originalHeader = resolveHeaderLabel(effectiveColumn.header);
    const effectiveColumnWithHeader = {
      ...effectiveColumn,
      header: originalHeader,
    };

    switch (effectiveColumn.type) {
      case "CURRENCY":
        return {
          ...effectiveColumnWithHeader,
          header: <div className="w-full text-right">{originalHeader}</div>,
          cell: ({ row }) => {
            const amount = parseFloat(row.getValue(effectiveColumn.accessorKey));
            const formatted = new Intl.NumberFormat("cs-CZ", {
              style: "currency",
              currency: "CZK",
            }).format(amount);
            return <div className="text-right font-medium">{formatted}</div>;
          },
        };

      case "NUMBER":
        return {
          ...effectiveColumnWithHeader,
          header: <div className="w-full text-right">{originalHeader}</div>,
          cell: ({ row }) => {
            const value = row.getValue(effectiveColumn.accessorKey);
            return <div className="text-right font-medium">{value}</div>;
          },
        };

      case "REFERENCE":
        return {
          ...effectiveColumnWithHeader,
          cell: ({ row }) => {
            const value = row.getValue(effectiveColumn.accessorKey);
            const uri = (effectiveColumn.referenceTemplate || "").replace(
              /\{([^}]+)\}/g,
              (_fullMatch, placeholderKey) => {
                if (placeholderKey === "tenantSlug") {
                  return context.tenantSlug || "";
                }

                const directValue =
                  row.original?.[placeholderKey] ??
                  row.getValue?.(placeholderKey);
                if (directValue !== undefined && directValue !== null) {
                  return String(directValue);
                }

                if (effectiveColumn.referenceKey === placeholderKey) {
                  const referenceValue =
                    row.getValue(effectiveColumn.referenceKey) ??
                    row.original?.[effectiveColumn.referenceKey];
                  return referenceValue ? String(referenceValue) : "";
                }

                return "";
              }
            );
            return (
              <Link className="text-left font-medium text-indigo-400" to={uri}>
                {value}
              </Link>
            );
          },
        };

      case "DATE":
        return {
          ...effectiveColumnWithHeader,
          cell: ({ row }) => {
            const rawValue = row.getValue(effectiveColumn.accessorKey);
            const date = parseSafeDate(rawValue);
            if (!date) return <div className="text-left font-medium" />;
            const formatted = new Intl.DateTimeFormat("cs-CZ", {
              year: "numeric",
              month: "2-digit",
              day: "2-digit",
            }).format(date);
            return <div className="text-left font-medium">{formatted}</div>;
          },
        };

      case "DATETIME":
      case "DATE_TIME":
        return {
          ...effectiveColumnWithHeader,
          cell: ({ row }) => {
            const rawValue = row.getValue(effectiveColumn.accessorKey);
            const date = parseSafeDate(rawValue);
            if (!date) return <div className="text-left font-medium" />;
            const formatted = new Intl.DateTimeFormat("cs-CZ", {
              year: "numeric",
              month: "2-digit",
              day: "2-digit",
              hour: "2-digit",
              minute: "2-digit",
            }).format(date);
            return <div className="text-left font-medium">{formatted}</div>;
          },
        };

      case "ENUM":
        return {
          ...effectiveColumnWithHeader,
          cell: ({ row }) => {
            const value = row.getValue(effectiveColumn.accessorKey);
            const enumOption = resolveEnumOption(effectiveColumn, value);
            const rawLabel = resolveEnumLabel(
              effectiveColumn,
              value,
              enumOption,
            );
            const label =
              typeof rawLabel === "string"
                ? resolveScopedLegacyLabel(rawLabel, "columns") ?? tr(rawLabel)
                : rawLabel;
            const displayMode = (
              effectiveColumn.displayMode ||
              effectiveColumn.enumDisplayMode ||
              "badge"
            ).toLowerCase();

            if (displayMode === "text" || displayMode === "simple") {
              return <div className="text-left font-medium">{label}</div>;
            }

            const status = enumOption?.status || enumOption?.color || value;
            const variant =
              enumOption?.variant || effectiveColumn.badgeVariant || "secondary";
            const className =
              enumOption?.className || effectiveColumn.badgeClassName;

            return (
              <Badge variant={variant} status={status} className={className}>
                {label}
              </Badge>
            );
          },
        };

      case "IMAGE":
        return {
          ...effectiveColumnWithHeader,
          cell: ({ row }) => {
            return (
              <div className="w-24 h-24 flex justify-center items-center">
                <Image
                  src={row.getValue("imageUrl")}
                  alt={row.getValue("name")}
                  loaderClassName={"h-24 w-24"}
                />
              </div>
            );
          },
        };

      case "MONO":
        return {
          ...effectiveColumnWithHeader,
          cell: ({ row }) => {
            const value = row.getValue(effectiveColumn.accessorKey);
            const content = (
              <span className="font-mono text-xs tracking-wide">{value}</span>
            );
            if (effectiveColumn.copyable) {
              return <CopyableCell value={value}>{content}</CopyableCell>;
            }
            return content;
          },
        };

      default:
        if (effectiveColumn.copyable) {
          return {
            ...effectiveColumnWithHeader,
            cell: ({ row }) => {
              const value = row.getValue(effectiveColumn.accessorKey);
              return <CopyableCell value={value} />;
            },
          };
        }
        return effectiveColumnWithHeader;
    }
  });

  if (enableRowSelection) {
    filteredColumns.unshift({
      id: "select",
      header: ({ table }) => (
        <Checkbox
          className="peer cursor-pointer"
          checked={table.getIsAllRowsSelected()}
          onCheckedChange={(value) => {
            table.toggleAllRowsSelected(!!value);
          }}
        />
      ),
      cell: ({ row }) => (
        <Checkbox
          className="peer cursor-pointer"
          checked={row.getIsSelected()}
          onCheckedChange={(value) => {
            row.toggleSelected(!!value);
          }}
        />
      ),
    });
  }

  return { columns: filteredColumns, rows };
}
