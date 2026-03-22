import SimpleResourceDetail from "@/views/shared/SimpleResourceDetail";
import { tenantModuleDefinitions } from "@/modules/tenant/moduleDefinitions";

export default function WebhooksDetail({ mode }) {
  return (
    <SimpleResourceDetail
      moduleDef={tenantModuleDefinitions.webhooks}
      mode={mode}
    />
  );
}
