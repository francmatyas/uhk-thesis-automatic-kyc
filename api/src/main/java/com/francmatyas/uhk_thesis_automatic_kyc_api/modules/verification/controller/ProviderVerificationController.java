package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.controller;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.User;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.check_result.dto.CheckResultResponse;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.check_result.service.CheckResultService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.risk_score.dto.RiskScoreResponse;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.risk_score.service.RiskScoreService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.repository.VerificationRepository;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.service.VerificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/provider/verifications")
@RequiredArgsConstructor
public class ProviderVerificationController {

    private final VerificationService verificationService;
    private final VerificationRepository verificationRepository;
    private final CheckResultService checkResultService;
    private final RiskScoreService riskScoreService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('PERM_provider.verifications:read')")
    public ResponseEntity<?> list(
            @AuthenticationPrincipal User currentUser,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String dir,
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(required = false) String q) {
        if (currentUser == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(verificationService.getProviderVerificationsTable(page, size, sort, dir, tenantId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('PERM_provider.verifications:read')")
    public ResponseEntity<?> get(@AuthenticationPrincipal User currentUser, @PathVariable UUID id) {
        if (currentUser == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return verificationRepository.findById(id)
                .<ResponseEntity<?>>map(v -> ResponseEntity.ok(verificationService.toDetailResponse(v, false)))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "not_found")));
    }

    @GetMapping("/{id}/checks")
    @PreAuthorize("hasAnyAuthority('PERM_provider.verifications:read')")
    public ResponseEntity<?> checks(@AuthenticationPrincipal User currentUser, @PathVariable UUID id) {
        if (currentUser == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        if (!verificationRepository.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "not_found"));
        }
        List<CheckResultResponse> results = checkResultService.findAllByVerification(id)
                .stream().map(CheckResultResponse::from).toList();
        return ResponseEntity.ok(results);
    }

    @GetMapping("/{id}/risk-score")
    @PreAuthorize("hasAnyAuthority('PERM_provider.verifications:read')")
    public ResponseEntity<?> riskScore(@AuthenticationPrincipal User currentUser, @PathVariable UUID id) {
        if (currentUser == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        if (!verificationRepository.existsById(id)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "not_found"));
        }
        return riskScoreService.findByVerification(id)
                .<ResponseEntity<?>>map(rs -> ResponseEntity.ok(RiskScoreResponse.from(rs)))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "risk_score_not_yet_available")));
    }

}