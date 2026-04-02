package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.service;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.audit.service.AuditLogService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.check_result.service.CheckResultService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.client_identity.service.ClientIdentityService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.document.repository.StoredDocumentRepository;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.integration.service.WebhookDispatcherService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.risk_score.service.RiskScoreService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.repository.TenantRepository;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.model.Verification;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.model.VerificationStatus;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.repository.VerificationRepository;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification_step.service.VerificationStepService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.r2_storage.R2StorageService;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class VerificationServiceTests {

    @Test
    void expireIfFlowTimedOutExpiresInitiatedVerification() {
        VerificationRepository repository = mock(VerificationRepository.class);
        VerificationService service = buildService(repository);

        Verification verification = new Verification();
        verification.setStatus(VerificationStatus.INITIATED);
        verification.setExpiresAt(Instant.now().minusSeconds(5));

        boolean expired = service.expireIfFlowTimedOut(verification);

        assertTrue(expired);
        assertEquals(VerificationStatus.EXPIRED, verification.getStatus());
        verify(repository).save(verification);
    }

    @Test
    void expireIfFlowTimedOutExpiresInProgressVerification() {
        VerificationRepository repository = mock(VerificationRepository.class);
        VerificationService service = buildService(repository);

        Verification verification = new Verification();
        verification.setStatus(VerificationStatus.IN_PROGRESS);
        verification.setExpiresAt(Instant.now().minusSeconds(5));

        boolean expired = service.expireIfFlowTimedOut(verification);

        assertTrue(expired);
        assertEquals(VerificationStatus.EXPIRED, verification.getStatus());
        verify(repository).save(verification);
    }

    @Test
    void expireIfFlowTimedOutDoesNotExpireNonActiveStatus() {
        VerificationRepository repository = mock(VerificationRepository.class);
        VerificationService service = buildService(repository);

        Verification verification = new Verification();
        verification.setStatus(VerificationStatus.READY_FOR_AUTOCHECK);
        verification.setExpiresAt(Instant.now().minusSeconds(5));

        boolean expired = service.expireIfFlowTimedOut(verification);

        assertFalse(expired);
        assertEquals(VerificationStatus.READY_FOR_AUTOCHECK, verification.getStatus());
        verify(repository, never()).save(verification);
    }

    @Test
    void expireIfFlowTimedOutDoesNotExpireBeforeDeadline() {
        VerificationRepository repository = mock(VerificationRepository.class);
        VerificationService service = buildService(repository);

        Verification verification = new Verification();
        verification.setStatus(VerificationStatus.INITIATED);
        verification.setExpiresAt(Instant.now().plusSeconds(60));

        boolean expired = service.expireIfFlowTimedOut(verification);

        assertFalse(expired);
        assertEquals(VerificationStatus.INITIATED, verification.getStatus());
        verify(repository, never()).save(verification);
    }

    private VerificationService buildService(VerificationRepository repository) {
        KycJobDispatcher kycJobDispatcher = mock(KycJobDispatcher.class);
        ClientIdentityService clientIdentityService = mock(ClientIdentityService.class);
        StoredDocumentRepository storedDocumentRepository = mock(StoredDocumentRepository.class);
        R2StorageService r2StorageService = mock(R2StorageService.class);
        VerificationStepService verificationStepService = mock(VerificationStepService.class);
        CheckResultService checkResultService = mock(CheckResultService.class);
        RiskScoreService riskScoreService = mock(RiskScoreService.class);
        WebhookDispatcherService webhookDispatcherService = mock(WebhookDispatcherService.class);
        TenantRepository tenantRepository = mock(TenantRepository.class);
        AuditLogService auditLogService = mock(AuditLogService.class);
        return new VerificationService(
                repository,
                kycJobDispatcher,
                auditLogService,
                clientIdentityService,
                storedDocumentRepository,
                r2StorageService,
                verificationStepService,
                checkResultService,
                riskScoreService,
                webhookDispatcherService,
                tenantRepository
        );
    }
}
