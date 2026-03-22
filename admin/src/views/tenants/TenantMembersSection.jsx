import { useMemo, useState } from "react";
import { Controller, useFieldArray } from "react-hook-form";
import { useQuery } from "@tanstack/react-query";
import { Button } from "@/components/ui/button";
import { Combobox } from "@/components/ui/combobox";
import { SearchSelect } from "@/components/ui/search-select";
import DetailFieldsSection from "@/views/shared/DetailFieldsSection";
import { fetchTenantMemberRoles } from "@/api/provider/tenants";
import { toast } from "sonner";
import { Trash } from "lucide-react";
import { useAuth } from "@/contexts/AuthContext";

export default function TenantMembersSection({ tenantId, control, permission }) {
  const { hasPermission } = useAuth();
  const [selectedUser, setSelectedUser] = useState(null);
  const [selectedRoles, setSelectedRoles] = useState([]);
  const [searchSelectResetKey, setSearchSelectResetKey] = useState(0);

  const { fields, append, remove } = useFieldArray({
    control,
    name: "members",
    keyName: "fieldId",
  });

  const { data: memberRolesData } = useQuery({
    queryKey: ["tenant-member-roles"],
    queryFn: fetchTenantMemberRoles,
    staleTime: 5 * 60 * 1000,
    enabled: Boolean(tenantId),
  });

  if (permission && !hasPermission(permission)) return null;

  const roleOptions = useMemo(
    () =>
      memberRolesData?.roles?.map((role) => ({
        value: role.name,
        label: role.name,
      })) || [],
    [memberRolesData],
  );

  const handleAddMember = () => {
    if (!selectedUser?.id) {
      toast.error("Select a user first.");
      return;
    }

    const alreadyAdded = fields.some((member) => member.id === selectedUser.id);
    if (alreadyAdded) {
      toast.error("This user is already in the member list.");
      return;
    }

    append({
      id: selectedUser.id,
      email: selectedUser.email || "",
      fullName: selectedUser.fullName || "",
      isDefault: false,
      roles: selectedRoles,
    });

    setSelectedUser(null);
    setSelectedRoles([]);
    setSearchSelectResetKey((current) => current + 1);
  };

  return (
    <DetailFieldsSection title="Members" columns={1}>
      <div className="@container/members space-y-4">
        <div className="grid grid-cols-1 items-end gap-2 @lg/members:grid-cols-[2fr_1fr_auto]">
          <SearchSelect
            key={searchSelectResetKey}
            label="Search user"
            endpoint="/tenants/members/search"
            timeout={300}
            params={{ tenantId, limit: 20 }}
            formatter={(user) => ({
              label: user.fullName || user.email || user.id,
              sublabel: user.email || "",
              value: user.id,
            })}
            onSelect={(user) => {
              setSelectedUser({
                id: user.id,
                email: user.email || "",
                fullName: user.fullName || "",
              });
            }}
            onClear={() => {
              setSelectedUser(null);
            }}
          />
          <Combobox
            label="Roles (optional)"
            value={selectedRoles}
            onChange={setSelectedRoles}
            options={roleOptions}
            placeholder="Select roles"
            multiple
          />
          <Button
            type="button"
            size="icon"
            onClick={handleAddMember}
            className="h-9 w-9 self-start @lg/members:self-auto"
          >
            +
          </Button>
        </div>

        <div className="divide-y rounded-md border">
          {fields.length === 0 ? (
            <div className="p-3 text-sm text-muted-foreground">No members</div>
          ) : (
            fields.map((member, index) => (
              <div
                key={member.fieldId}
                className="flex items-center justify-between p-3"
              >
                <div className="flex flex-col">
                  <span className="text-sm font-medium">
                    {member.fullName || member.email || member.id}
                  </span>
                  <span className="text-xs text-muted-foreground">
                    {member.email || "—"}
                  </span>
                </div>
                <div className="flex items-center gap-2">
                  <div className="w-56">
                    <Controller
                      name={`members.${index}.roles`}
                      control={control}
                      defaultValue={member.roles || []}
                      render={({ field }) => (
                        <Combobox
                          value={field.value || []}
                          onChange={field.onChange}
                          options={roleOptions}
                          placeholder="Select roles"
                          multiple
                        />
                      )}
                    />
                  </div>
                  {member.isDefault && (
                    <span className="text-xs text-muted-foreground">Default</span>
                  )}
                  <Button
                    type="button"
                    variant="destructive"
                    size="icon"
                    onClick={() => remove(index)}
                  >
                    <Trash />
                  </Button>
                </div>
              </div>
            ))
          )}
        </div>
      </div>
    </DetailFieldsSection>
  );
}
