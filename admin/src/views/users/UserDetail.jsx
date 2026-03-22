import SimpleResourceDetail from "@/views/shared/SimpleResourceDetail";
import { providerModuleDefinitions } from "@/modules/provider/moduleDefinitions";

export default function UserDetail({ mode }) {
  return <SimpleResourceDetail moduleDef={providerModuleDefinitions.users} mode={mode} />;
}
