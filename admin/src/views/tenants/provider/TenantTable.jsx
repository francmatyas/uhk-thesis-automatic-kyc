import ResourceTablePage from "@/views/shared/ResourceTablePage";
import { providerModuleDefinitions } from "@/modules/provider/moduleDefinitions";

export default function TenantTable() {
  return <ResourceTablePage {...providerModuleDefinitions.tenants.table} />;
}
