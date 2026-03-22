import { createElement } from "react";
import { tenantModuleDefinitions } from "@/modules/tenant/moduleDefinitions";
import SimpleResourceDetail from "@/views/shared/SimpleResourceDetail";
import TenantMembersSection from "@/views/tenants/TenantMembersSection";

export default function TenantMeDetail({ mode }) {
  const moduleDef = {
    ...tenantModuleDefinitions.tenantMe,
    detail: {
      ...tenantModuleDefinitions.tenantMe.detail,
      renderAfterFields: ({ entity, inEditMode, control }) =>
        inEditMode
          ? createElement(TenantMembersSection, {
              tenantId: entity?.id,
              control,
              permission: "tenant.tenants:update",
            })
          : null,
    },
  };

  return <SimpleResourceDetail moduleDef={moduleDef} mode={mode} />;
}
