import { Link } from "react-router";
import { Image } from "@/components/ui/image";
import { Badge } from "@/components/ui/badge";
import { Checkbox } from "@/components/ui/checkbox";

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
  const filteredColumns = columns.map((column) => {
    const columnConfig =
      context.enumConfig?.[column.accessorKey] ||
      context.enumConfig?.[column.id] ||
      {};
    const effectiveColumn = { ...column, ...columnConfig };
    const originalHeader = effectiveColumn.header;

    switch (effectiveColumn.type) {
      case "CURRENCY":
        return {
          ...effectiveColumn,
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
          ...effectiveColumn,
          header: <div className="w-full text-right">{originalHeader}</div>,
          cell: ({ row }) => {
            const value = row.getValue(effectiveColumn.accessorKey);
            return <div className="text-right font-medium">{value}</div>;
          },
        };

      case "REFERENCE":
        return {
          ...effectiveColumn,
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
          ...effectiveColumn,
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
          ...effectiveColumn,
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
          ...effectiveColumn,
          cell: ({ row }) => {
            const value = row.getValue(effectiveColumn.accessorKey);
            const enumOption = resolveEnumOption(effectiveColumn, value);
            const label = resolveEnumLabel(effectiveColumn, value, enumOption);
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
          ...effectiveColumn,
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

      default:
        return effectiveColumn;
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
