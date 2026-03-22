import SimpleResourceDetail from "@/views/shared/SimpleResourceDetail";
import { providerModuleDefinitions } from "@/modules/provider/moduleDefinitions";

export default function ProviderAuditLogDetail({ mode }) {
  return <SimpleResourceDetail moduleDef={providerModuleDefinitions.auditLogs} mode={mode} />;
}
