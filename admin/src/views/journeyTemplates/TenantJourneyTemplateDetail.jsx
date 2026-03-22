import SimpleResourceDetail from "@/views/shared/SimpleResourceDetail";
import { tenantModuleDefinitions } from "@/modules/tenant/moduleDefinitions";

export default function TenantJourneyTemplateDetail({ mode }) {
  return <SimpleResourceDetail moduleDef={tenantModuleDefinitions.journeyTemplates} mode={mode} />;
}
