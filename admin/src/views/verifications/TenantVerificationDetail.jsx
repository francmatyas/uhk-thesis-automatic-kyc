import SimpleResourceDetail from "@/views/shared/SimpleResourceDetail";
import { tenantModuleDefinitions } from "@/modules/tenant/moduleDefinitions";

export default function TenantVerificationDetail({ mode }) {
  return <SimpleResourceDetail moduleDef={tenantModuleDefinitions.verifications} mode={mode} />;
}
