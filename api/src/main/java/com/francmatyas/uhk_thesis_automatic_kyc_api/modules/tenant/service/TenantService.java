package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.service;

import com.francmatyas.uhk_thesis_automatic_kyc_api.model.Column;
import com.francmatyas.uhk_thesis_automatic_kyc_api.model.TableDTO;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.Role;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.RoleScope;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.User;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.repository.RoleRepository;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.repository.UserRepository;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.service.UserEmailLookupService;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.dto.TenantListDTO;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.dto.TenantMemberUpsertRequest;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.model.Tenant;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.model.TenantStatus;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.model.UserTenantMembership;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.model.UserTenantRole;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.repository.TenantRepository;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.repository.UserTenantMembershipRepository;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.repository.UserTenantRoleRepository;
import com.francmatyas.uhk_thesis_automatic_kyc_api.security.TenantContext;
import com.francmatyas.uhk_thesis_automatic_kyc_api.util.DisplayFieldScanner;
import com.francmatyas.uhk_thesis_automatic_kyc_api.util.SlugUtils;
import com.francmatyas.uhk_thesis_automatic_kyc_api.util.SpecBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TenantService {
    private final TenantRepository tenantRepository;
    private final TenantAccessService tenantAccessService;
    private final UserTenantMembershipRepository membershipRepository;
    private final UserTenantRoleRepository userTenantRoleRepository;
    private final UserRepository userRepository;
    private final UserEmailLookupService userEmailLookupService;
    private final RoleRepository roleRepository;

    public TableDTO getAllTenants(int page, int size, String sortBy, String sortDir, String q) {
        List<Column> columns = DisplayFieldScanner.getColumns(TenantListDTO.class);
        Specification<Tenant> spec = SpecBuilder.buildStringSearchSpec(q, columns);

        Sort.Direction dir = Sort.Direction.fromString(sortDir);
        Pageable pageReq = PageRequest.of(page, size, dir, sortBy);

        Page<Tenant> pageEntity = tenantRepository.findAll(spec, pageReq);
        List<TenantListDTO> dtos = pageEntity.stream().map(this::toListDto).toList();
        List<Map<String, Object>> rows = DisplayFieldScanner.getDataMaps(dtos, columns);

        return TableDTO.builder()
                .columns(columns)
                .rows(rows)
                .pageNumber(pageEntity.getNumber())
                .pageSize(pageEntity.getSize())
                .totalPages(pageEntity.getTotalPages())
                .totalElements(pageEntity.getTotalElements())
                .build();
    }

    public Map<String, Object> getTenantDetail(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new NoSuchElementException("tenant_not_found"));

        var rolesByUserId = userTenantRoleRepository.findAllByTenantId(tenantId).stream()
                .filter(r -> r.getUser() != null && r.getRole() != null)
                .collect(Collectors.groupingBy(
                        r -> r.getUser().getId(),
                        Collectors.mapping(r -> r.getRole().getName(), Collectors.toList())
                ));

        var members = membershipRepository.findAllByTenantId(tenantId).stream().map(m -> {
            Map<String, Object> member = new HashMap<>();
            member.put("id", m.getUser().getId());
            member.put("email", m.getUser().getEmail());
            member.put("fullName", m.getUser().getFullName());
            member.put("isDefault", m.isDefault());
            member.put("roles", rolesByUserId.getOrDefault(m.getUser().getId(), List.of()));
            return member;
        }).toList();

        return Map.of(
                "id", tenant.getId(),
                "name", tenant.getName(),
                "slug", tenant.getSlug(),
                "status", tenant.getStatus(),
                "members", members
        );
    }

    @Transactional
    public Map<String, Object> createTenant(String name, String slugInput, String statusInput) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name_required");
        }

        String normalizedSlugInput = slugInput == null ? "" : slugInput.trim();
        String slug = SlugUtils.slugify(normalizedSlugInput.isBlank() ? name : normalizedSlugInput);
        if (slug.isBlank()) {
            throw new IllegalArgumentException("invalid_slug");
        }
        if (tenantRepository.findBySlug(slug).isPresent()) {
            throw new IllegalStateException("slug_taken");
        }

        Tenant tenant = new Tenant();
        tenant.setName(name.trim());
        tenant.setSlug(slug);
        tenant.setStatus(parseStatus(statusInput, TenantStatus.ACTIVE));
        tenantRepository.save(tenant);

        return Map.of(
                "id", tenant.getId(),
                "name", tenant.getName(),
                "slug", tenant.getSlug(),
                "status", tenant.getStatus()
        );
    }

    @Transactional
    public Map<String, Object> updateTenant(
            UUID tenantId,
            String name,
            String slugInput,
            String statusInput,
            List<TenantMemberUpsertRequest> members
    ) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new NoSuchElementException("tenant_not_found"));

        if (name != null && !name.isBlank()) {
            tenant.setName(name.trim());
        }

        if (slugInput != null) {
            String slug = SlugUtils.slugify(slugInput);
            if (slug.isBlank()) {
                throw new IllegalArgumentException("invalid_slug");
            }
            boolean taken = tenantRepository.findBySlug(slug)
                    .filter(existing -> !existing.getId().equals(tenantId))
                    .isPresent();
            if (taken) {
                throw new IllegalStateException("slug_taken");
            }
            tenant.setSlug(slug);
        }

        if (statusInput != null && !statusInput.isBlank()) {
            tenant.setStatus(parseStatus(statusInput, tenant.getStatus()));
        }

        if (members != null) {
            syncTenantMembers(tenant, members);
        }

        tenantRepository.save(tenant);
        return Map.of(
                "id", tenant.getId(),
                "name", tenant.getName(),
                "slug", tenant.getSlug(),
                "status", tenant.getStatus()
        );
    }

    @Transactional
    public void deleteTenant(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new NoSuchElementException("tenant_not_found"));
        tenantRepository.delete(tenant);
    }

    public Map<String, Object> getCurrentTenant(User currentUser) {
        UUID activeTenantId = requireActiveTenantId();
        if (!tenantAccessService.canAccessTenant(currentUser, activeTenantId)) {
            throw new AccessDeniedException("not_member_of_tenant");
        }

        Tenant tenant = tenantRepository.findById(activeTenantId)
                .orElseThrow(() -> new NoSuchElementException("tenant_not_found"));

        var rolesByUserId = userTenantRoleRepository.findAllByTenantId(activeTenantId).stream()
                .filter(r -> r.getUser() != null && r.getRole() != null)
                .collect(Collectors.groupingBy(
                        r -> r.getUser().getId(),
                        Collectors.mapping(r -> r.getRole().getName(), Collectors.toList())
                ));

        var members = membershipRepository.findAllByTenantId(activeTenantId).stream().map(m -> {
            Map<String, Object> member = new HashMap<>();
            member.put("id", m.getUser().getId());
            member.put("email", m.getUser().getEmail());
            member.put("fullName", m.getUser().getFullName());
            member.put("isDefault", m.isDefault());
            member.put("roles", rolesByUserId.getOrDefault(m.getUser().getId(), List.of()));
            return member;
        }).toList();

        return Map.of(
                "id", tenant.getId(),
                "name", tenant.getName(),
                "slug", tenant.getSlug(),
                "status", tenant.getStatus(),
                "members", members
        );
    }

    @Transactional
    public Map<String, Object> updateCurrentTenant(
            User currentUser,
            String name,
            String slugInput,
            List<TenantMemberUpsertRequest> members
    ) {
        UUID activeTenantId = requireActiveTenantId();
        if (!tenantAccessService.canAccessTenant(currentUser, activeTenantId)) {
            throw new AccessDeniedException("not_member_of_tenant");
        }

        Tenant tenant = tenantRepository.findById(activeTenantId)
                .orElseThrow(() -> new NoSuchElementException("tenant_not_found"));

        if (name != null) {
            if (name.isBlank()) {
                throw new IllegalArgumentException("name_required");
            }
            tenant.setName(name.trim());
        }

        if (slugInput != null) {
            String slug = SlugUtils.slugify(slugInput);
            if (slug.isBlank()) {
                throw new IllegalArgumentException("invalid_slug");
            }

            boolean taken = tenantRepository.findBySlug(slug)
                    .filter(existing -> !existing.getId().equals(activeTenantId))
                    .isPresent();
            if (taken) {
                throw new IllegalStateException("slug_taken");
            }
            tenant.setSlug(slug);
        }

        if (members != null) {
            syncTenantMembers(tenant, members);
        }

        tenantRepository.save(tenant);
        return Map.of(
                "id", tenant.getId(),
                "name", tenant.getName(),
                "slug", tenant.getSlug(),
                "status", tenant.getStatus()
        );
    }

    private void syncTenantMembers(Tenant tenant, List<TenantMemberUpsertRequest> members) {
        UUID tenantId = tenant.getId();
        Map<UUID, UserTenantMembership> existingMembershipsByUserId = membershipRepository.findAllByTenantId(tenantId).stream()
                .collect(Collectors.toMap(m -> m.getUser().getId(), m -> m));
        Map<UUID, DesiredMember> desiredByUserId = new LinkedHashMap<>();

        for (TenantMemberUpsertRequest memberRequest : members) {
            if (memberRequest == null) {
                throw new IllegalArgumentException("invalid_member");
            }
            User user = resolveTargetUser(memberRequest);
            if (desiredByUserId.containsKey(user.getId())) {
                throw new IllegalArgumentException("duplicate_member");
            }
            desiredByUserId.put(user.getId(), new DesiredMember(user, resolveTenantRoles(memberRequest.getRoles())));
        }

        for (UUID existingUserId : existingMembershipsByUserId.keySet()) {
            if (desiredByUserId.containsKey(existingUserId)) {
                continue;
            }
            userTenantRoleRepository.hardDeleteByUserIdAndTenantId(existingUserId, tenantId);
            membershipRepository.hardDeleteByUserIdAndTenantId(existingUserId, tenantId);
        }

        for (DesiredMember desired : desiredByUserId.values()) {
            UUID userId = desired.user().getId();
            if (!existingMembershipsByUserId.containsKey(userId)) {
                membershipRepository.purgeSoftDeleted(userId, tenantId);
                UserTenantMembership membership = new UserTenantMembership();
                membership.setUser(desired.user());
                membership.setTenant(tenant);
                membership.setDefault(false);
                membershipRepository.save(membership);
            }

            userTenantRoleRepository.hardDeleteByUserIdAndTenantId(userId, tenantId);
            if (desired.roles().isEmpty()) {
                continue;
            }

            userTenantRoleRepository.purgeSoftDeleted(userId, tenantId);
            for (Role role : desired.roles()) {
                UserTenantRole assignment = new UserTenantRole();
                assignment.setUser(desired.user());
                assignment.setTenant(tenant);
                assignment.setRole(role);
                userTenantRoleRepository.save(assignment);
            }
        }
    }

    private User resolveTargetUser(TenantMemberUpsertRequest memberRequest) {
        String rawUserId = firstNonBlank(memberRequest.getUserId(), memberRequest.getId());
        if (rawUserId != null) {
            UUID userId;
            try {
                userId = UUID.fromString(rawUserId.trim());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("invalid_user_id");
            }
            return userRepository.findById(userId)
                    .orElseThrow(() -> new NoSuchElementException("user_not_found"));
        }

        if (memberRequest.getEmail() != null && !memberRequest.getEmail().isBlank()) {
            return userEmailLookupService.findByEmail(memberRequest.getEmail())
                    .orElseThrow(() -> new NoSuchElementException("user_not_found"));
        }

        throw new IllegalArgumentException("member_user_required");
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }

    private List<Role> resolveTenantRoles(List<String> requestedRoleNames) {
        if (requestedRoleNames == null || requestedRoleNames.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<String> uniqueRoleNames = new LinkedHashSet<>();
        for (String requestedRoleName : requestedRoleNames) {
            if (requestedRoleName == null) {
                continue;
            }
            String roleName = requestedRoleName.trim();
            if (roleName.isBlank()) {
                continue;
            }
            uniqueRoleNames.add(roleName);
        }

        if (uniqueRoleNames.isEmpty()) {
            return List.of();
        }

        List<Role> roles = new ArrayList<>(uniqueRoleNames.size());
        for (String roleName : uniqueRoleNames) {
            Role role = roleRepository.findByNameAndScope(roleName, RoleScope.TENANT)
                    .orElseThrow(() -> new NoSuchElementException("role_not_found"));
            roles.add(role);
        }
        return roles;
    }

    private record DesiredMember(User user, List<Role> roles) {
    }

    private UUID requireActiveTenantId() {
        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new IllegalArgumentException("tenant_required");
        }
        return tenantId;
    }

    private TenantStatus parseStatus(String rawStatus, TenantStatus defaultStatus) {
        if (rawStatus == null || rawStatus.isBlank()) {
            return defaultStatus;
        }
        try {
            return TenantStatus.valueOf(rawStatus.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("invalid_status");
        }
    }

    private TenantListDTO toListDto(Tenant t) {
        return TenantListDTO.builder()
                .id(t.getId() == null ? null : t.getId().toString())
                .name(t.getName())
                .slug(t.getSlug())
                .status(t.getStatus() == null ? null : t.getStatus().name())
                .build();
    }
}
