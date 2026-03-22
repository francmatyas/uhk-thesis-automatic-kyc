import ResourceTablePage from "@/views/shared/ResourceTablePage";
import { tenantModuleDefinitions } from "@/modules/tenant/moduleDefinitions";

export default function TenantJourneyTemplateTable() {
  return <ResourceTablePage {...tenantModuleDefinitions.journeyTemplates.table} />;
}
