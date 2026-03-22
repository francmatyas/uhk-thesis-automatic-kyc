import ResourceTablePage from "@/views/shared/ResourceTablePage";
import { tenantModuleDefinitions } from "@/modules/tenant/moduleDefinitions";

export default function TenantAuditLogTable() {
  return <ResourceTablePage {...tenantModuleDefinitions.auditLogs.table} />;
}
