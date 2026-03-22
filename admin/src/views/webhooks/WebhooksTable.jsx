import ResourceTablePage from "@/views/shared/ResourceTablePage";
import { tenantModuleDefinitions } from "@/modules/tenant/moduleDefinitions";
import WebhookCreateWizardDialog from "@/views/webhooks/WebhookCreateWizardDialog";

export default function WebhooksTable() {
  return (
    <ResourceTablePage
      {...tenantModuleDefinitions.webhooks.table}
      showCreateButton={false}
      buttons={[<WebhookCreateWizardDialog key="webhook-create-wizard" />]}
    />
  );
}
