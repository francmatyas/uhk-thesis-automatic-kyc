import { useMemo, useState } from "react";
import { Controller } from "react-hook-form";
import { useQuery } from "@tanstack/react-query";
import { useTranslation } from "react-i18next";
import { fetchPermissionsForRoles } from "@/api/provider/permissions";
import { queryKeys } from "@/modules/queryKeys";
import { Label } from "@/components/ui/label";
import { Input } from "@/components/ui/input";
import { Checkbox } from "@/components/ui/checkbox";

const DEFAULT_ACTION_ORDER = ["create", "read", "update", "delete"];

function normalizePermissionValue(permission) {
  if (permission === null || permission === undefined) return null;
  if (typeof permission === "string" || typeof permission === "number") {
    return String(permission);
  }

  return permission.id;
}

function normalizePermissionsArray(value) {
  if (!Array.isArray(value)) return [];
  return value.map(normalizePermissionValue).filter(Boolean).map(String);
}

function parseResourceAndAction(permission) {
  const resource = permission?.resource;
  const action = permission?.action;

  if (resource && action) {
    return { resource: String(resource), action: String(action).toLowerCase() };
  }

  const key = permission?.label;
  if (typeof key === "string" && key.includes(":")) {
    const [left, right] = key.split(":");
    if (left && right) {
      return { resource: left.trim(), action: right.trim().toLowerCase() };
    }
  }

  return null;
}

function buildPermissionMatrix(rows) {
  const groupedByResource = new Map();
  const discoveredActions = new Set();

  rows?.forEach((permission) => {
    const id = normalizePermissionValue(permission);
    const parsed = parseResourceAndAction(permission);
    if (!id || !parsed) return;

    const resourceKey = parsed.resource;
    const actionKey = parsed.action;
    discoveredActions.add(actionKey);

    if (!groupedByResource.has(resourceKey)) {
      groupedByResource.set(resourceKey, {
        resource: resourceKey,
        actions: {},
      });
    }

    groupedByResource.get(resourceKey).actions[actionKey] = String(id);
  });

  const discoveredActionList = Array.from(discoveredActions);
  const orderedActions = [
    ...DEFAULT_ACTION_ORDER.filter((action) =>
      discoveredActionList.includes(action),
    ),
    ...discoveredActionList.filter(
      (action) => !DEFAULT_ACTION_ORDER.includes(action),
    ),
  ];

  const matrixRows = Array.from(groupedByResource.values()).sort((a, b) =>
    a.resource.localeCompare(b.resource),
  );

  return { matrixRows, actions: orderedActions };
}

function setAll(targetValues, ids, shouldEnable) {
  const set = new Set(targetValues);
  ids.forEach((id) => {
    if (shouldEnable) set.add(id);
    else set.delete(id);
  });
  return Array.from(set);
}

function capitalize(value) {
  if (!value) return "";
  return value.charAt(0).toUpperCase() + value.slice(1);
}

export default function RolePermissionsField({
  control,
  readOnly = false,
  name = "permissions",
  label = "moduleDefinitions.roles.permissionsMatrix.label",
  roleScope,
}) {
  const [query, setQuery] = useState("");

  const { data, isLoading } = useQuery({
    queryKey: queryKeys.permissions.list(),
    queryFn: () => fetchPermissionsForRoles({ size: 500 }),
  });

  const scopedRows = useMemo(() => {
    const scope = String(roleScope || "").toLowerCase();
    const rows = Array.isArray(data?.rows) ? data.rows : [];

    if (scope !== "tenant" && scope !== "provider") return rows;

    return rows.filter((permission) => {
      const parsed = parseResourceAndAction(permission);
      const resource = String(parsed?.resource || "").toLowerCase();
      if (!resource) return false;
      return resource.startsWith(`${scope}.`);
    });
  }, [data, roleScope]);

  const matrix = useMemo(() => buildPermissionMatrix(scopedRows), [scopedRows]);

  return (
    <Controller
      name={name}
      control={control}
      render={({ field }) => (
        <RolePermissionsMatrix
          field={field}
          readOnly={readOnly}
          label={label}
          query={query}
          onQueryChange={setQuery}
          matrix={matrix}
          isLoading={isLoading}
        />
      )}
    />
  );
}

function RolePermissionsMatrix({
  field,
  readOnly,
  label,
  query,
  onQueryChange,
  matrix,
  isLoading,
}) {
  const { t } = useTranslation();
  const selectedValues = normalizePermissionsArray(field.value);

  const filteredRows = matrix.matrixRows.filter((row) =>
    row.resource.toLowerCase().includes(query.trim().toLowerCase()),
  );

  const allPermissionIds = filteredRows.flatMap((row) =>
    Object.values(row.actions).filter(Boolean),
  );
  const allChecked =
    allPermissionIds.length > 0 &&
    allPermissionIds.every((id) => selectedValues.includes(id));

  const toggleSingle = (permissionId) => {
    const hasValue = selectedValues.includes(permissionId);
    const next = hasValue
      ? selectedValues.filter((value) => value !== permissionId)
      : [...selectedValues, permissionId];
    field.onChange(next);
  };

  const toggleAllVisible = () => {
    if (readOnly) return;
    field.onChange(setAll(selectedValues, allPermissionIds, !allChecked));
  };

  const toggleRow = (row) => {
    if (readOnly) return;
    const rowIds = Object.values(row.actions).filter(Boolean);
    const rowChecked =
      rowIds.length > 0 && rowIds.every((id) => selectedValues.includes(id));
    field.onChange(setAll(selectedValues, rowIds, !rowChecked));
  };

  const toggleColumn = (action) => {
    if (readOnly) return;
    const colIds = filteredRows
      .map((row) => row.actions[action])
      .filter(Boolean);
    const colChecked =
      colIds.length > 0 && colIds.every((id) => selectedValues.includes(id));
    field.onChange(setAll(selectedValues, colIds, !colChecked));
  };

  return (
    <div className="flex flex-col gap-2">
      <Label className="ml-0.5 text-xs font-medium">
        {t(label, { defaultValue: label })}
      </Label>

      <Input
        value={query}
        onChange={(event) => onQueryChange(event.target.value)}
        placeholder={t("moduleDefinitions.roles.permissionsMatrix.searchPlaceholder")}
        disabled={readOnly}
      />

      <div className="overflow-hidden rounded-md border">
        <table className="w-full border-collapse">
          <thead className="bg-muted/30">
            <tr>
              <th className="border-b px-3 py-2 text-left text-sm font-medium">
                <label className="flex items-center gap-2">
                  <Checkbox
                    checked={allChecked}
                    onCheckedChange={toggleAllVisible}
                    disabled={readOnly || allPermissionIds.length === 0}
                  />
                  <span>{t("moduleDefinitions.roles.permissionsMatrix.module")}</span>
                </label>
              </th>
              {matrix.actions.map((action) => {
                const colIds = filteredRows
                  .map((row) => row.actions[action])
                  .filter(Boolean);
                const colChecked =
                  colIds.length > 0 &&
                  colIds.every((id) => selectedValues.includes(id));
                return (
                  <th
                    key={action}
                    className="border-b border-l px-3 py-2 text-left text-sm font-medium"
                  >
                    <label className="flex items-center gap-2">
                      <Checkbox
                        checked={colChecked}
                        onCheckedChange={() => toggleColumn(action)}
                        disabled={readOnly || colIds.length === 0}
                      />
                      <span>
                        {t(
                          `moduleDefinitions.roles.permissionsMatrix.actions.${action}`,
                          { defaultValue: capitalize(action) },
                        )}
                      </span>
                    </label>
                  </th>
                );
              })}
            </tr>
          </thead>
          <tbody>
            {isLoading && (
              <tr>
                <td
                  colSpan={1 + matrix.actions.length}
                  className="px-3 py-4 text-sm text-muted-foreground"
                >
                  {t("moduleDefinitions.roles.permissionsMatrix.loading")}
                </td>
              </tr>
            )}

            {!isLoading && filteredRows.length === 0 && (
              <tr>
                <td
                  colSpan={1 + matrix.actions.length}
                  className="px-3 py-4 text-sm text-muted-foreground"
                >
                  {t("moduleDefinitions.roles.permissionsMatrix.noModules")}
                </td>
              </tr>
            )}

            {!isLoading &&
              filteredRows.map((row) => {
                const rowIds = Object.values(row.actions).filter(Boolean);
                const rowChecked =
                  rowIds.length > 0 &&
                  rowIds.every((id) => selectedValues.includes(id));

                return (
                  <tr key={row.resource}>
                    <td className="border-t px-3 py-2 text-sm">
                      <label className="flex items-center gap-2">
                        <Checkbox
                          checked={rowChecked}
                          onCheckedChange={() => toggleRow(row)}
                          disabled={readOnly || rowIds.length === 0}
                        />
                        <span>{row.resource}</span>
                      </label>
                    </td>
                    {matrix.actions.map((action) => {
                      const permissionId = row.actions[action];
                      const checked =
                        Boolean(permissionId) &&
                        selectedValues.includes(permissionId);
                      return (
                        <td
                          key={`${row.resource}-${action}`}
                          className="border-t border-l px-3 py-2"
                        >
                          {permissionId ? (
                            <Checkbox
                              checked={checked}
                              onCheckedChange={() => toggleSingle(permissionId)}
                              disabled={readOnly}
                            />
                          ) : (
                            <span className="text-muted-foreground">-</span>
                          )}
                        </td>
                      );
                    })}
                  </tr>
                );
              })}
          </tbody>
        </table>
      </div>
    </div>
  );
}
