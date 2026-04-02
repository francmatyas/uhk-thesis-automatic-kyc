import SimpleResourceDetail from "@/views/shared/SimpleResourceDetail";
import { tenantModuleDefinitions } from "@/modules/tenant/moduleDefinitions";
import {
  VerificationClientIdentitySection,
  VerificationRiskScoreSection,
  VerificationStepsSection,
} from "@/views/verifications/VerificationDetailSections";
import VerificationDetailStatusSteps from "@/views/verifications/VerificationDetailStatusSteps";
import { VerificationReviewActions } from "@/views/verifications/VerificationReviewActions";
import {
  approveVerification,
  rejectVerification,
} from "@/api/tenant/verifications";

export default function TenantVerificationDetail({ mode }) {
  const moduleDef = {
    ...tenantModuleDefinitions.verifications,
    detail: {
      ...tenantModuleDefinitions.verifications.detail,
      renderBeforeFields: ({ entity }) => (
        <VerificationDetailStatusSteps status={entity?.status} />
      ),
      renderControlBarActions: ({ entity, id }) => (
        <VerificationReviewActions
          entity={entity}
          id={id}
          permission="tenant.verifications:review"
          approveApi={approveVerification}
          rejectApi={rejectVerification}
        />
      ),
      renderAfterFields: ({ entity }) => (
        <>
          <VerificationRiskScoreSection entity={entity} />
          <VerificationClientIdentitySection entity={entity} />
          <VerificationStepsSection entity={entity} />
        </>
      ),
    },
  };

  return <SimpleResourceDetail moduleDef={moduleDef} mode={mode} />;
}
