import SimpleResourceDetail from "@/views/shared/SimpleResourceDetail";
import { providerModuleDefinitions } from "@/modules/provider/moduleDefinitions";
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
} from "@/api/provider/verifications";

export default function ProviderVerificationDetail({ mode }) {
  const moduleDef = {
    ...providerModuleDefinitions.verifications,
    detail: {
      ...providerModuleDefinitions.verifications.detail,
      renderBeforeFields: ({ entity }) => (
        <VerificationDetailStatusSteps status={entity?.status} />
      ),
      renderControlBarActions: ({ entity, id }) => (
        <VerificationReviewActions
          entity={entity}
          id={id}
          permission="provider.verifications:review"
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
