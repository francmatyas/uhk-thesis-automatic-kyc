package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.controller;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.User;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.check_result.dto.CheckResultResponse;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.check_result.service.CheckResultService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.risk_score.dto.RiskScoreResponse;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.risk_score.service.RiskScoreService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.model.Verification;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.model.VerificationStatus;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.service.VerificationService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.security.RequireActiveTenant;
import com.francmatyas.uhk_thesis_automatic_kyc_api.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/verifications")
@RequireActiveTenant
@RequiredArgsConstructor
public class TenantVerificationController {

    private final VerificationService verificationService;
    private final CheckResultService checkResultService;
    private final RiskScoreService riskScoreService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('PERM_tenant.verifications:read')")
    public ResponseEntity<?> list(
            @AuthenticationPrincipal User currentUser,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String dir,
            @RequestParam(required = false) VerificationStatus status,
            @RequestParam(required = false) String q) {
        if (currentUser == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        UUID tenantId = TenantContext.getTenantId();
        return ResponseEntity.ok(verificationService.getTenantVerificationsTable(tenantId, page, size, sort, dir, status));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('PERM_tenant.verifications:read')")
    public ResponseEntity<?> get(@AuthenticationPrincipal User currentUser, @PathVariable UUID id,
                                 Authentication authentication) {
        if (currentUser == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        try {
            Verification v = verificationService.findByIdAndTenant(id, TenantContext.getTenantId());
            boolean canReadIdentity = hasAuthority(authentication, "PERM_tenant.client-identities:read");
            return ResponseEntity.ok(verificationService.toDetailResponse(v, canReadIdentity));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}/checks")
    @PreAuthorize("hasAnyAuthority('PERM_tenant.verifications:read')")
    public ResponseEntity<?> checks(@AuthenticationPrincipal User currentUser, @PathVariable UUID id) {
        if (currentUser == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        try {
            verificationService.findByIdAndTenant(id, TenantContext.getTenantId()); // ownership check
            List<CheckResultResponse> results = checkResultService.findAllByVerification(id)
                    .stream().map(CheckResultResponse::from).toList();
            return ResponseEntity.ok(results);
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyAuthority('PERM_tenant.verifications:review')")
    public ResponseEntity<?> approve(@AuthenticationPrincipal User currentUser, @PathVariable UUID id) {
        if (currentUser == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        try {
            return ResponseEntity.ok(verificationService.approveReview(id, TenantContext.getTenantId(), currentUser.getId()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyAuthority('PERM_tenant.verifications:review')")
    public ResponseEntity<?> reject(@AuthenticationPrincipal User currentUser, @PathVariable UUID id) {
        if (currentUser == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        try {
            return ResponseEntity.ok(verificationService.rejectReview(id, TenantContext.getTenantId(), currentUser.getId()));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}/risk-score")
    @PreAuthorize("hasAnyAuthority('PERM_tenant.verifications:read')")
    public ResponseEntity<?> riskScore(@AuthenticationPrincipal User currentUser, @PathVariable UUID id) {
        if (currentUser == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        try {
            verificationService.findByIdAndTenant(id, TenantContext.getTenantId()); // ownership check
            return riskScoreService.findByVerification(id)
                    .<ResponseEntity<?>>map(rs -> ResponseEntity.ok(RiskScoreResponse.from(rs)))
                    .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(Map.of("error", "risk_score_not_yet_available")));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    private static boolean hasAuthority(Authentication auth, String authority) {
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(authority));
    }
}