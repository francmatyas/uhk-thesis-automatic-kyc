package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.journey_template.controller;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.audit.service.AuditLogService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.User;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.journey_template.dto.CreateJourneyTemplateRequest;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.journey_template.dto.JourneyTemplateResponse;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.journey_template.dto.UpdateJourneyTemplateRequest;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.journey_template.model.JourneyTemplate;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.journey_template.service.JourneyTemplateService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.security.RequireActiveTenant;
import com.francmatyas.uhk_thesis_automatic_kyc_api.security.TenantContext;
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
@RequestMapping("/journey-templates")
@RequireActiveTenant
@RequiredArgsConstructor
public class TenantJourneyTemplateController {

    private final JourneyTemplateService service;
    private final AuditLogService auditLogService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('PERM_tenant.journey-templates:read')")
    public ResponseEntity<?> list(
            @AuthenticationPrincipal User currentUser,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String dir,
            @RequestParam(required = false) String q) {
        if (currentUser == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        UUID tenantId = TenantContext.getTenantId();
        return ResponseEntity.ok(service.getTenantJourneyTemplatesTable(tenantId, page, size, sort, dir));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('PERM_tenant.journey-templates:read')")
    public ResponseEntity<?> get(@AuthenticationPrincipal User currentUser, @PathVariable UUID id) {
        if (currentUser == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        try {
            return ResponseEntity.ok(JourneyTemplateResponse.from(
                    service.findByIdAndTenant(id, TenantContext.getTenantId())));
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('PERM_tenant.journey-templates:create')")
    public ResponseEntity<?> create(
            @AuthenticationPrincipal User currentUser,
            @Valid @RequestBody CreateJourneyTemplateRequest req,
            HttpServletRequest httpReq) {
        if (currentUser == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        UUID tenantId = TenantContext.getTenantId();
        JourneyTemplate template = JourneyTemplate.builder()
                .tenantId(tenantId)
                .name(req.getName())
                .description(req.getDescription())
                .configJson(req.getConfigJson())
                .build();
        JourneyTemplateResponse created = JourneyTemplateResponse.from(service.create(template));
        audit(currentUser, httpReq, tenantId, created.id(), "JOURNEY_TEMPLATE_CREATE", null, created);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('PERM_tenant.journey-templates:update')")
    public ResponseEntity<?> update(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID id,
            @Valid @RequestBody UpdateJourneyTemplateRequest req,
            HttpServletRequest httpReq) {
        if (currentUser == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        UUID tenantId = TenantContext.getTenantId();
        try {
            JourneyTemplate patch = new JourneyTemplate();
            patch.setName(req.getName());
            patch.setDescription(req.getDescription());
            patch.setStatus(req.getStatus());
            patch.setConfigJson(req.getConfigJson());
            JourneyTemplateResponse updated = JourneyTemplateResponse.from(service.update(id, tenantId, patch));
            audit(currentUser, httpReq, tenantId, id, "JOURNEY_TEMPLATE_UPDATE", null, updated);
            return ResponseEntity.ok(updated);
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('PERM_tenant.journey-templates:delete')")
    public ResponseEntity<?> archive(
            @AuthenticationPrincipal User currentUser,
            @PathVariable UUID id,
            HttpServletRequest httpReq) {
        if (currentUser == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        UUID tenantId = TenantContext.getTenantId();
        try {
            service.archive(id, tenantId);
            audit(currentUser, httpReq, tenantId, id, "JOURNEY_TEMPLATE_ARCHIVE", null, null);
            return ResponseEntity.noContent().build();
        } catch (NoSuchElementException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    private void audit(User actor, HttpServletRequest req, UUID tenantId,
                       UUID entityId, String action, Object oldValue, Object newValue) {
        try {
            String ip = req.getHeader("CF-Connecting-IP");
            if (ip == null || ip.isBlank()) ip = req.getRemoteAddr();
            auditLogService.logUserAction(actor.getId(), tenantId, "JOURNEY_TEMPLATE",
                    entityId != null ? entityId.toString() : null,
                    action, oldValue, newValue, null, ip, req.getHeader("User-Agent"), null, null);
        } catch (Exception ignored) {}
    }
}