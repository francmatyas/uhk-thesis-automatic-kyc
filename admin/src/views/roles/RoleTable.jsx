import ResourceTablePage from "@/views/shared/ResourceTablePage";
import { providerModuleDefinitions } from "@/modules/provider/moduleDefinitions";

export default function RoleTable() {
  return <ResourceTablePage {...providerModuleDefinitions.roles.table} />;
}
