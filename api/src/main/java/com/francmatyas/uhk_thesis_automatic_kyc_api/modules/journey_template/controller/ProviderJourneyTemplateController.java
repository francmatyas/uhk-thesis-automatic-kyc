package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.journey_template.controller;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.audit.service.AuditLogService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.User;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.journey_template.dto.CreateJourneyTemplateRequest;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.journey_template.dto.JourneyTemplateResponse;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.journey_template.dto.UpdateJourneyTemplateRequest;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.journey_template.model.JourneyTemplate;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.journey_template.repository.JourneyTemplateRepository;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.journey_template.service.JourneyTemplateService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@RestController
@RequestMapping("/provider/journey-templates")
@RequiredArgsConstructor
public class ProviderJourneyTemplateController {

    private final JourneyTemplateService service;
    private final JourneyTemplateRepository repository;
    private final AuditLogService auditLogService;

    /** Seznam všech šablon se stránkováním (formát TableDTO), volitelně omezený na tenanta. */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('PERM_provider.journey-templates:read')")
    public ResponseEntity<?> list(
            @AuthenticationPrincipal User currentUser,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String dir,
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(required = false) String q) {
        if (currentUser == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return ResponseEntity.ok(service.getProviderJourneyTemplatesTable(page, size, sort, dir, tenantId));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('PERM_provider.journey-templates:read')")
    public ResponseEntity<?> get(@AuthenticationPrincipal User currentUser, @PathVariable UUID id) {
        if (currentUser == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        return repository.findById(id)
                .<ResponseEntity<?>>map(t -> ResponseEntity.ok(JourneyTemplateResponse.from(t)))
                .orElse(ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "not_found")));
    }
}
