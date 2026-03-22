import { createElement } from "react";
import { providerModuleDefinitions } from "@/modules/provider/moduleDefinitions";
import SimpleResourceDetail from "@/views/shared/SimpleResourceDetail";
import TenantMembersSection from "../TenantMembersSection";

export default function TenantDetail({ mode }) {
  const moduleDef = {
    ...providerModuleDefinitions.tenants,
    detail: {
      ...providerModuleDefinitions.tenants.detail,
      renderAfterFields: ({ inEditMode, id, control }) =>
        inEditMode
          ? createElement(TenantMembersSection, {
              tenantId: id,
              control,
              permission: "provider.tenants:update",
            })
          : null,
    },
  };

  return <SimpleResourceDetail moduleDef={moduleDef} mode={mode} />;
}
