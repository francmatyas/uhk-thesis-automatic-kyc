import ResourceTablePage from "@/views/shared/ResourceTablePage";
import { providerModuleDefinitions } from "@/modules/provider/moduleDefinitions";

export default function ProviderAuditLogTable() {
  return <ResourceTablePage {...providerModuleDefinitions.auditLogs.table} />;
}
