package com.francmatyas.uhk_thesis_automatic_kyc_api.bootstrap;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.client_identity.model.ClientIdentity;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.client_identity.service.ClientIdentityService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.journey_template.model.JourneyTemplate;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.journey_template.model.JourneyTemplateStatus;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.journey_template.repository.JourneyTemplateRepository;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.model.Tenant;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.repository.TenantRepository;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.model.Verification;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.model.VerificationStatus;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.repository.VerificationRepository;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.service.KycJobDispatcher;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.service.VerificationService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.r2_storage.R2StorageService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Component
@Order(10)
@ConditionalOnProperty(prefix = "app.bootstrap.test-verification", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class BootstrapTestVerificationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapTestVerificationRunner.class);

    private static final Duration PRESIGN_TTL = Duration.ofMinutes(30);

    private final TenantRepository tenantRepository;
    private final JourneyTemplateRepository journeyTemplateRepository;
    private final VerificationService verificationService;
    private final VerificationRepository verificationRepository;
    private final ClientIdentityService clientIdentityService;
    private final KycJobDispatcher kycJobDispatcher;
    private final R2StorageService storageService;

    @Value("${app.bootstrap.test-tenant.slug:test-tenant}")
    private String testTenantSlug;

    @Value("${app.bootstrap.test-verification.image-key-prefix:bootstrap/test}")
    private String imageKeyPrefix;

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
    public void run(ApplicationArguments args) throws IOException {
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

        // Nahrají se testovací obrázky z classpath do objektového úložiště a vygeneruje presigned GET URL
        String frontUrl = uploadAndPresign("bootstrap/test-idcard-front.jpeg", imageKeyPrefix + "/idcard-front.jpeg");
        String backUrl  = uploadAndPresign("bootstrap/test-idcard-back.jpeg",  imageKeyPrefix + "/idcard-back.jpeg");
        List<String> livenessUrls = List.of(
                uploadAndPresign("bootstrap/test-liveness-center.jpg", imageKeyPrefix + "/liveness-center.jpg"),
                uploadAndPresign("bootstrap/test-liveness-left.jpg",   imageKeyPrefix + "/liveness-left.jpg"),
                uploadAndPresign("bootstrap/test-liveness-right.jpg",  imageKeyPrefix + "/liveness-right.jpg"),
                uploadAndPresign("bootstrap/test-liveness-up.jpg",     imageKeyPrefix + "/liveness-up.jpg")
        );
        log.info("Test images uploaded and presigned (prefix={}).", imageKeyPrefix);

        // Nejprve se vytvoří ClientIdentity, aby měla verifikace od začátku platný FK
        ClientIdentity pii = ClientIdentity.builder()
                .tenantId(tenant.getId())
                .firstName(firstName)
                .lastName(lastName)
                .dateOfBirth(dateOfBirth)
                .countryOfResidence(countryOfResidence)
                .build();
        ClientIdentity savedPii = clientIdentityService.create(pii);

        // Vytvoří se Verification s již propojenou clientIdentity
        Verification v = Verification.builder()
                .tenantId(tenant.getId())
                .journeyTemplate(template)
                .clientIdentity(savedPii)
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        var result = verificationService.create(v);
        log.info("Test verification created: id={} token={}...", result.verification().getId(),
                result.rawToken().substring(0, 12));

        // Přepne se stav na READY_FOR_AUTOCHECK a provede se flush před spuštěním jobů
        Verification verification = result.verification();
        verification.setStatus(VerificationStatus.READY_FOR_AUTOCHECK);
        verificationRepository.saveAndFlush(verification);

        // AML (sankce + PEP)
        kycJobDispatcher.dispatchChecks(verification);
        log.info("AML check dispatched.");

        // Kontrola českého dokladu totožnosti
        kycJobDispatcher.dispatchDocumentCheck(verification, "verify_czech_id", frontUrl, backUrl);
        log.info("Document check dispatched.");

        // Liveness kontrola
        kycJobDispatcher.dispatchLiveness(verification, livenessUrls);
        log.info("Liveness check dispatched: {} frames.", livenessUrls.size());

        // Porovnání obličeje – pro testovací účely použijeme center liveness snímek jako selfie
        kycJobDispatcher.dispatchFaceMatch(verification, frontUrl, livenessUrls.get(0));
        log.info("Face match dispatched.");

        log.info("Test verification setup complete: verificationId={}", verification.getId());
    }

    private String uploadAndPresign(String classpathPath, String s3Key) throws IOException {
        byte[] bytes = new ClassPathResource(classpathPath).getInputStream().readAllBytes();
        String contentType = classpathPath.endsWith(".png") ? "image/png" : "image/jpeg";
        storageService.upload(s3Key, bytes, contentType);
        return storageService.createWorkerDownloadUrl(s3Key, PRESIGN_TTL);
    }
}
