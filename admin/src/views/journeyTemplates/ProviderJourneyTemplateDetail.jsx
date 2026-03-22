import SimpleResourceDetail from "@/views/shared/SimpleResourceDetail";
import { providerModuleDefinitions } from "@/modules/provider/moduleDefinitions";

export default function ProviderJourneyTemplateDetail({ mode }) {
  return <SimpleResourceDetail moduleDef={providerModuleDefinitions.journeyTemplates} mode={mode} />;
}
