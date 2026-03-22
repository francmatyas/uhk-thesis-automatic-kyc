import ResourceTablePage from "@/views/shared/ResourceTablePage";
import { tenantModuleDefinitions } from "@/modules/tenant/moduleDefinitions";
import ApiKeyCreateWizardDialog from "@/views/apiKeys/ApiKeyCreateWizardDialog";

export default function ApiKeysTable() {
  return (
    <ResourceTablePage
      {...tenantModuleDefinitions.apiKeys.table}
      showCreateButton={false}
      buttons={[<ApiKeyCreateWizardDialog key="api-key-create-wizard" />]}
    />
  );
}
