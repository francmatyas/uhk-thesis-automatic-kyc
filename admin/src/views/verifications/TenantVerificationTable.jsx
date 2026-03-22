import ResourceTablePage from "@/views/shared/ResourceTablePage";
import { tenantModuleDefinitions } from "@/modules/tenant/moduleDefinitions";

export default function TenantVerificationTable() {
  return <ResourceTablePage {...tenantModuleDefinitions.verifications.table} />;
}
