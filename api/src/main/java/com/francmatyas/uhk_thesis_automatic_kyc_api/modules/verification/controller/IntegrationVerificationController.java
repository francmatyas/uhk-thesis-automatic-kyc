package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.controller;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.audit.model.AuditActorType;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.audit.model.AuditResult;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.audit.service.AuditLogCommand;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.audit.service.AuditLogService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.client_identity.model.ClientIdentity;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.client_identity.service.ClientIdentityService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.journey_template.model.JourneyTemplate;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.journey_template.model.JourneyTemplateStatus;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.journey_template.repository.JourneyTemplateRepository;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.dto.CreateVerificationRequest;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.dto.IntegrationCreateVerificationResponse;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.model.Verification;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.service.VerificationService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.security.ApiKeyAccessible;
import com.francmatyas.uhk_thesis_automatic_kyc_api.security.ApiKeyPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/integration/verifications")
@ApiKeyAccessible
@RequiredArgsConstructor
public class IntegrationVerificationController {

    private final VerificationService verificationService;
    private final JourneyTemplateRepository journeyTemplateRepository;
    private final ClientIdentityService clientIdentityService;
    private final AuditLogService auditLogService;

    @Value("${kyc.flow.base-url:http://localhost:5174/v}")
    private String flowBaseUrl;

    /**
     * Vytvoření nové KYC verifikace přes API klíč.
     * Vrací ID verifikace a URL, na které má tenant přesměrovat svého klienta.
     */
    @PostMapping
    public ResponseEntity<?> create(
            @AuthenticationPrincipal ApiKeyPrincipal principal,
            @Valid @RequestBody CreateVerificationRequest req,
            HttpServletRequest httpReq) {

        JourneyTemplate template = journeyTemplateRepository
                .findByIdAndTenantId(req.getJourneyTemplateId(), principal.tenantId())
                .orElse(null);
        if (template == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "journey_template_not_found"));
        }
        if (template.getStatus() != JourneyTemplateStatus.ACTIVE) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "journey_template_not_active"));
        }

        ClientIdentity clientIdentity = null;
        if (req.getExternalReference() != null) {
            clientIdentity = clientIdentityService.create(ClientIdentity.builder()
                    .tenantId(principal.tenantId())
                    .externalReference(req.getExternalReference())
                    .build());
        }

        Verification v = Verification.builder()
                .tenantId(principal.tenantId())
                .journeyTemplate(template)
                .expiresAt(req.getExpiresAt())
                .clientIdentity(clientIdentity)
                .build();

        var result = verificationService.create(v);
        String verificationUrl = flowBaseUrl + "/" + result.rawToken();

        try {
            String ip = httpReq.getHeader("CF-Connecting-IP");
            if (ip == null || ip.isBlank()) ip = httpReq.getRemoteAddr();
            auditLogService.log(new AuditLogCommand(
                    principal.tenantId(), null, AuditActorType.API_KEY, principal.apiKeyId(),
                    "VERIFICATION", result.verification().getId().toString(),
                    "VERIFICATION_CREATE", null, null,
                    Map.of("journeyTemplateId", req.getJourneyTemplateId().toString()),
                    ip, httpReq.getHeader("User-Agent"), null, null,
                    AuditResult.SUCCESS, null
            ));
        } catch (Exception ignored) {}

        return ResponseEntity.status(HttpStatus.CREATED).body(new IntegrationCreateVerificationResponse(
                result.verification().getId(),
                verificationUrl,
                result.verification().getStatus(),
                result.verification().getExpiresAt()
        ));
    }
}