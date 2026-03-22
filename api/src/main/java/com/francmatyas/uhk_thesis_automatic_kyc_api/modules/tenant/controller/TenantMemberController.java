package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.controller;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.Role;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.RoleScope;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.User;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.repository.RoleRepository;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.repository.UserRepository;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.service.UserEmailLookupService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.model.Tenant;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.model.UserTenantMembership;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.model.UserTenantRole;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.repository.TenantRepository;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.repository.UserTenantMembershipRepository;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.repository.UserTenantRoleRepository;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.service.TenantAccessService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.security.TenantContext;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/tenants")
@RequiredArgsConstructor
public class TenantMemberController {

    private final UserRepository userRepository;
    private final UserEmailLookupService userEmailLookupService;
    private final TenantRepository tenantRepository;
    private final UserTenantMembershipRepository membershipRepository;
    private final UserTenantRoleRepository userTenantRoleRepository;
    private final RoleRepository roleRepository;
    private final TenantAccessService tenantAccessService;

    @GetMapping("/members/roles")
    @PreAuthorize("hasAnyAuthority('PERM_tenant.roles:read','PERM_tenant.members:update','PERM_provider.roles:read','PERM_provider.tenants:update')")
    public ResponseEntity<?> listAssignableRoles(
            @AuthenticationPrincipal User currentUser,
            Authentication authentication,
            @RequestParam(required = false) String tenantId
    ) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        if (!currentUser.isProviderUser()) {
            UUID activeTenantId = TenantContext.getTenantId();
            if (activeTenantId == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "tenant_required"));
            }
            if (!tenantAccessService.canAccessTenant(currentUser, activeTenantId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "forbidden", "reason", "not_member_of_tenant"));
            }
        } else if (tenantId != null && !tenantId.isBlank()) {
            try {
                UUID.fromString(tenantId.trim());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "invalid_tenant_id"));
            }
        }

        boolean canAssignOwner = hasAnyAuthority(authentication, List.of(
                "PERM_tenant.roles:update",
                "PERM_tenant.tenants:update",
                "PERM_provider.roles:update",
                "PERM_provider.tenants:update"
        ));
        var roles = roleRepository.findAllByScope(RoleScope.TENANT).stream()
                .filter(r -> {
                    if (currentUser.isProviderUser()) return true;
                    if (canAssignOwner) return true;
                    return !isTenantOwnerRoleName(r.getName());
                })
                .map(r -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", r.getId());
                    m.put("name", r.getName());
                    m.put("slug", r.getSlug());
                    m.put("scope", r.getScope());
                    m.put("description", r.getDescription());
                    m.put("priority", r.getPriority());
                    return m;
                })
                .toList();

        return ResponseEntity.ok(Map.of("roles", roles));
    }

    @GetMapping("/members/search")
    @PreAuthorize("hasAnyAuthority('PERM_tenant.members:read','PERM_tenant.members:update','PERM_provider.users:read','PERM_provider.tenants:update')")
    public ResponseEntity<?> searchUsers(
            @AuthenticationPrincipal User currentUser,
            @RequestParam String q,
            @RequestParam(required = false) String tenantId,
            @RequestParam(defaultValue = "20") int limit
    ) {
        if (currentUser == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        if (q == null || q.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "q_required"));
        }

        UUID effectiveTenantId;
        if (currentUser.isProviderUser()) {
            if (tenantId == null || tenantId.isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "tenant_required"));
            }
            try {
                effectiveTenantId = UUID.fromString(tenantId.trim());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "invalid_tenant_id"));
            }
        } else {
            effectiveTenantId = TenantContext.getTenantId();
            if (effectiveTenantId == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "tenant_required"));
            }
            if (!tenantAccessService.canAccessTenant(currentUser, effectiveTenantId)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(Map.of("error", "forbidden", "reason", "not_member_of_tenant"));
            }
        }

        int pageSize = Math.min(Math.max(limit, 1), 50);
        String needle = q.trim().toLowerCase(Locale.ROOT);

        Set<UUID> memberIds = membershipRepository.findAllByTenantId(effectiveTenantId).stream()
                .map(m -> m.getUser().getId())
                .collect(Collectors.toSet());

        // Email cesta: přesné vyhledání přes hash — O(1), bez full table scanu a hromadného dešifrování PII
        if (needle.contains("@")) {
            List<Map<String, Object>> results = userEmailLookupService.findByEmail(q.trim())
                    .filter(u -> !memberIds.contains(u.getId()))
                    .map(u -> {
                        Map<String, Object> m = new HashMap<>();
                        m.put("id", u.getId());
                        m.put("email", u.getEmail());
                        m.put("fullName", u.getFullName());
                        return m;
                    })
                    .map(result -> List.of(result))
                    .orElse(List.of());
            return ResponseEntity.ok(Map.of("results", results));
        }

        // Cesta podle jména: načíst z DB jen nečleny, limitovat scan kvůli omezení hromadného dešifrování PII
        int scanCap = Math.min(pageSize * 10, 200);
        List<User> candidates = userRepository.findUsersNotMemberOf(
                effectiveTenantId, PageRequest.of(0, scanCap)).getContent();

        var results = candidates.stream()
                .filter(u -> containsIgnoreCase(u.getFullName(), needle)
                        || containsIgnoreCase(u.getGivenName(), needle)
                        || containsIgnoreCase(u.getFamilyName(), needle))
                .limit(pageSize)
                .map(u -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("id", u.getId());
                    m.put("email", u.getEmail());
                    m.put("fullName", u.getFullName());
                    return m;
                })
                .toList();

        return ResponseEntity.ok(Map.of("results", results));
    }

    private boolean containsIgnoreCase(String value, String needle) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private boolean hasAnyAuthority(Authentication authentication, List<String> authorities) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(a -> authorities.stream().anyMatch(allowed -> allowed.equalsIgnoreCase(a.getAuthority())));
    }

    private boolean isTenantOwnerRoleName(String roleName) {
        if (roleName == null || roleName.isBlank()) {
            return false;
        }
        return roleName.equalsIgnoreCase("TENANT_OWNER")
                || roleName.equalsIgnoreCase("OWNER");
    }
}
