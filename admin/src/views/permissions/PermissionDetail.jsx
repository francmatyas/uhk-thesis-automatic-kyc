import SimpleResourceDetail from "@/views/shared/SimpleResourceDetail";
import { providerModuleDefinitions } from "@/modules/provider/moduleDefinitions";

export default function PermissionDetail({ mode }) {
  return (
    <SimpleResourceDetail
      moduleDef={providerModuleDefinitions.permissions}
      mode={mode}
    />
  );
}
