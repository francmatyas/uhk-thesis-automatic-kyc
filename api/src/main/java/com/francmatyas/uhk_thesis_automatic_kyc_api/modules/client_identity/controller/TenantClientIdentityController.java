package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.client_identity.controller;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.User;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.client_identity.dto.ClientIdentityResponse;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.client_identity.service.ClientIdentityService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.security.RequireActiveTenant;
import com.francmatyas.uhk_thesis_automatic_kyc_api.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Čtení a úprava klientských identit, které se automaticky vytvořily
 * při vytvoření odpovídající verifikace.
 */
@RestController
@RequestMapping("/client-identities")
@RequireActiveTenant
@RequiredArgsConstructor
public class TenantClientIdentityController {

    private final ClientIdentityService service;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('PERM_tenant.client-identities:read')")
    public ResponseEntity<?> list(@AuthenticationPrincipal User currentUser) {
        if (currentUser == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        List<ClientIdentityResponse> result = service.findAllByTenant(TenantContext.getTenantId())
                .stream().map(ClientIdentityResponse::from).toList();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('PERM_tenant.client-identities:read')")
    public ResponseEntity<?> get(@AuthenticationPrincipal User currentUser, @PathVariable UUID id) {
        if (currentUser == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        try {
            return ResponseEntity.ok(ClientIdentityResponse.from(
                    service.findByIdAndTenant(id, TenantContext.getTenantId())));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

}
