import ResourceTablePage from "@/views/shared/ResourceTablePage";
import { providerModuleDefinitions } from "@/modules/provider/moduleDefinitions";

export default function UserTable() {
  return <ResourceTablePage {...providerModuleDefinitions.users.table} />;
}
