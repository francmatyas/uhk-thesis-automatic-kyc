import SimpleResourceDetail from "@/views/shared/SimpleResourceDetail";
import { providerModuleDefinitions } from "@/modules/provider/moduleDefinitions";

export default function RoleDetail({ mode }) {
  return <SimpleResourceDetail moduleDef={providerModuleDefinitions.roles} mode={mode} />;
}
