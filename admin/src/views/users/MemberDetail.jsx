import SimpleResourceDetail from "@/views/shared/SimpleResourceDetail";
import { tenantModuleDefinitions } from "@/modules/tenant/moduleDefinitions";

export default function MemberDetail({ mode }) {
  return (
    <SimpleResourceDetail
      moduleDef={tenantModuleDefinitions.members}
      mode={mode}
    />
  );
}
