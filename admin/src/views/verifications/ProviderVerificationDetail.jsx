import SimpleResourceDetail from "@/views/shared/SimpleResourceDetail";
import { providerModuleDefinitions } from "@/modules/provider/moduleDefinitions";

export default function ProviderVerificationDetail({ mode }) {
  return <SimpleResourceDetail moduleDef={providerModuleDefinitions.verifications} mode={mode} />;
}
