import ResourceTablePage from "@/views/shared/ResourceTablePage";
import { providerModuleDefinitions } from "@/modules/provider/moduleDefinitions";

export default function ProviderJourneyTemplateTable() {
  return <ResourceTablePage {...providerModuleDefinitions.journeyTemplates.table} />;
}
