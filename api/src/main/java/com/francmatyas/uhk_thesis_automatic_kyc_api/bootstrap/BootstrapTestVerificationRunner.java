package com.francmatyas.uhk_thesis_automatic_kyc_api.bootstrap;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.client_identity.model.ClientIdentity;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.client_identity.service.ClientIdentityService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.journey_template.model.JourneyTemplate;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.journey_template.model.JourneyTemplateStatus;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.journey_template.repository.JourneyTemplateRepository;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.model.Tenant;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.repository.TenantRepository;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.model.Verification;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.repository.VerificationRepository;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.service.KycJobDispatcher;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.service.VerificationService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
@Order(10)
@ConditionalOnProperty(prefix = "app.bootstrap.test-verification", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class BootstrapTestVerificationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapTestVerificationRunner.class);

    private final TenantRepository tenantRepository;
    private final JourneyTemplateRepository journeyTemplateRepository;
    private final VerificationService verificationService;
    private final VerificationRepository verificationRepository;
    private final ClientIdentityService clientIdentityService;
    private final KycJobDispatcher kycJobDispatcher;

    @Value("${app.bootstrap.test-tenant.slug:test-tenant}")
    private String testTenantSlug;

    @Value("${app.bootstrap.test-verification.doc-type:passport}")
    private String docType;

    @Value("${app.bootstrap.test-verification.front-image-path:}")
    private String frontImagePath;

    @Value("${app.bootstrap.test-verification.back-image-path:}")
    private String backImagePath;

    @Value("${app.bootstrap.test-verification.liveness-image-paths:}")
    private List<String> livenessImagePaths;

    @Value("${app.bootstrap.test-verification.first-name:Test}")
    private String firstName;

    @Value("${app.bootstrap.test-verification.last-name:User}")
    private String lastName;

    @Value("${app.bootstrap.test-verification.date-of-birth:1990-01-15}")
    private String dateOfBirth;

    @Value("${app.bootstrap.test-verification.country-of-residence:CZE}")
    private String countryOfResidence;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Tenant tenant = tenantRepository.findBySlug(testTenantSlug).orElse(null);
        if (tenant == null) {
            log.warn("Test verification skipped: tenant '{}' not found.", testTenantSlug);
            return;
        }

        JourneyTemplate template = journeyTemplateRepository
                .findAllByTenantId(tenant.getId(), org.springframework.data.domain.PageRequest.of(0, 100))
                .getContent().stream()
                .filter(t -> t.getStatus() == JourneyTemplateStatus.ACTIVE)
                .findFirst()
                .orElse(null);
        if (template == null) {
            log.warn("Test verification skipped: no active journey template found for tenant '{}'.", testTenantSlug);
            return;
        }

        // Vytvoření verifikace
        Verification v = Verification.builder()
                .tenantId(tenant.getId())
                .journeyTemplate(template)
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        var result = verificationService.create(v);
        log.info("Test verification created: id={} token={}...", result.verification().getId(),
                result.rawToken().substring(0, 12));

        // Vytvoření a propojení ClientIdentity, přechod do READY_FOR_AUTOCHECK, odeslání AML
        ClientIdentity pii = ClientIdentity.builder()
                .tenantId(tenant.getId())
                .firstName(firstName)
                .lastName(lastName)
                .dateOfBirth(dateOfBirth)
                .countryOfResidence(countryOfResidence)
                .build();
        ClientIdentity savedPii = clientIdentityService.create(pii);

        Verification verification = verificationRepository.findById(result.verification().getId()).orElseThrow();
        verification.setClientIdentity(savedPii);
        verification.setStatus(com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.model.VerificationStatus.READY_FOR_AUTOCHECK);
        verificationRepository.save(verification);

        // AML (sankce + PEP)
        kycJobDispatcher.dispatchChecks(verification);
        log.info("AML check dispatched.");

        // Kontrola dokumentu
        if (frontImagePath != null && !frontImagePath.isBlank()) {
            String jobType = "passport".equalsIgnoreCase(docType) ? "verify_passport" : "verify_czech_id";
            String back = (backImagePath != null && !backImagePath.isBlank()) ? backImagePath : null;
            kycJobDispatcher.dispatchDocumentCheck(verification, jobType, frontImagePath, back);
            log.info("Document check dispatched: jobType={} front={} back={}", jobType, frontImagePath, back);
        } else {
            log.warn("No front image path set – document check not dispatched. Set app.bootstrap.test-verification.front-image-path.");
        }

        // Kontrola živosti
        if (livenessImagePaths != null && !livenessImagePaths.isEmpty()) {
            kycJobDispatcher.dispatchLiveness(verification, livenessImagePaths);
            log.info("Liveness check dispatched: {} images.", livenessImagePaths.size());
        } else {
            log.warn("No liveness image paths set – liveness check not dispatched. Set app.bootstrap.test-verification.liveness-image-paths.");
        }

        log.info("Test verification setup complete: verificationId={}", verification.getId());
    }
}
