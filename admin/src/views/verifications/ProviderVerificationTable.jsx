import ResourceTablePage from "@/views/shared/ResourceTablePage";
import { providerModuleDefinitions } from "@/modules/provider/moduleDefinitions";

export default function ProviderVerificationTable() {
  return <ResourceTablePage {...providerModuleDefinitions.verifications.table} />;
}
