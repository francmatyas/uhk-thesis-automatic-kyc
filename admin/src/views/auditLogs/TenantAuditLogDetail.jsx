import SimpleResourceDetail from "@/views/shared/SimpleResourceDetail";
import { tenantModuleDefinitions } from "@/modules/tenant/moduleDefinitions";

export default function TenantAuditLogDetail({ mode }) {
  return <SimpleResourceDetail moduleDef={tenantModuleDefinitions.auditLogs} mode={mode} />;
}
