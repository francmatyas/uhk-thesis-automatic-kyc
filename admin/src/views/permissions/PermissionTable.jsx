import ResourceTablePage from "@/views/shared/ResourceTablePage";
import { providerModuleDefinitions } from "@/modules/provider/moduleDefinitions";

export default function PermissionTable() {
  return <ResourceTablePage {...providerModuleDefinitions.permissions.table} />;
}
