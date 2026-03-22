import ResourceTablePage from "@/views/shared/ResourceTablePage";
import { tenantModuleDefinitions } from "@/modules/tenant/moduleDefinitions";

export default function MemberTable() {
  return <ResourceTablePage {...tenantModuleDefinitions.members.table} />;
}
