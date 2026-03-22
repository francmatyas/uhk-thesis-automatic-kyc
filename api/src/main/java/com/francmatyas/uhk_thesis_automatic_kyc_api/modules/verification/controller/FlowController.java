package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.client_identity.model.ClientIdentity;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.dto.FlowSubmitRequest;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.dto.VerificationResponse;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.model.Verification;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.model.VerificationStatus;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.service.VerificationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;
import java.util.NoSuchElementException;

@RestController
@RequestMapping("/flow")
@RequiredArgsConstructor
public class FlowController {

    private final VerificationService verificationService;

    /**
     * Vrací metadata verifikace a konfiguraci journey template, aby
     * klientská webová aplikace mohla vykreslit správný formulář.
     */
    @GetMapping("/{token}")
    public ResponseEntity<?> getFlow(@PathVariable String token) {
        try {
            Verification v = verificationService.findByToken(token);
            if (v.getStatus() != VerificationStatus.INITIATED) {
                return ResponseEntity.status(HttpStatus.GONE)
                        .body(Map.of("error", "verification_already_submitted"));
            }
            if (v.getExpiresAt() != null && v.getExpiresAt().isBefore(Instant.now())) {
                return ResponseEntity.status(HttpStatus.GONE)
                        .body(Map.of("error", "verification_expired"));
            }
            JsonNode config = v.getJourneyTemplate() != null ? v.getJourneyTemplate().getConfigJson() : null;
            return ResponseEntity.ok(Map.of(
                    "verificationId", v.getId(),
                    "status", v.getStatus(),
                    "expiresAt", v.getExpiresAt() != null ? v.getExpiresAt().toString() : "",
                    "journeyConfig", config != null ? config : Map.of()
            ));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "not_found"));
        }
    }

    /**
     * Klient odešle vyplněný formulář. Vytvoří se ClientIdentity, verifikace přejde
     * do READY_FOR_AUTOCHECK a odešlou se automatické kontroly.
     */
    @PostMapping("/{token}/submit")
    public ResponseEntity<?> submit(
            @PathVariable String token,
            @Valid @RequestBody FlowSubmitRequest req) {
        try {
            ClientIdentity pii = ClientIdentity.builder()
                    .firstName(req.getFirstName())
                    .lastName(req.getLastName())
                    .dateOfBirth(req.getDateOfBirth())
                    .countryOfResidence(req.getCountryOfResidence())
                    .email(req.getEmail())
                    .dialCode(req.getDialCode())
                    .phone(req.getPhone())
                    .build();
            Verification v = verificationService.submitFlow(token, pii);
            return ResponseEntity.ok(VerificationResponse.from(v));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "not_found"));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }
}
