import SimpleResourceDetail from "@/views/shared/SimpleResourceDetail";
import { tenantModuleDefinitions } from "@/modules/tenant/moduleDefinitions";

export default function ApiKeysDetail({ mode }) {
  return (
    <SimpleResourceDetail
      moduleDef={tenantModuleDefinitions.apiKeys}
      mode={mode}
    />
  );
}
