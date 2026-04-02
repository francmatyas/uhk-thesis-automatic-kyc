package com.francmatyas.uhk_thesis_automatic_kyc_api.security;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.User;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.repository.UserRepository;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.model.UserTenantRole;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.repository.UserTenantRoleRepository;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.RoleScope;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PolicyService {

    private final UserRepository userRepo;
    private final UserTenantRoleRepository userTenantRoleRepository;

    @Transactional(readOnly = true)
    public PolicySnapshot buildForUser(UUID userId) {
        User u = userRepo.findById(userId).orElseThrow();

        UUID activeTenantId = TenantContext.getTenantId();

        List<UserTenantRole> assignments;
        Set<RoleScope> allowedScopes = EnumSet.noneOf(RoleScope.class);

        if (u.isProviderUser()) {
            if (activeTenantId != null) {
                // Provider uživatel přepnut do kontextu tenanta — pouze tenant scoped oprávnění.
                assignments = new ArrayList<>(userTenantRoleRepository.findAllByUserIdAndTenantId(u.getId(), activeTenantId));
                allowedScopes.add(RoleScope.TENANT);
            } else {
                // Provider uživatel v provider kontextu — pouze provider scoped oprávnění.
                assignments = new ArrayList<>(userTenantRoleRepository.findAllByUserIdAndTenantId(u.getId(), null));
                allowedScopes.add(RoleScope.PROVIDER);
            }
        } else {
            // Tenant uživatelé vyhodnocují pouze tenant scoped role pro aktivního tenanta.
            allowedScopes.add(RoleScope.TENANT);
            if (activeTenantId == null) {
                assignments = List.of();
            } else {
                assignments = userTenantRoleRepository.findAllByUserIdAndTenantId(u.getId(), activeTenantId);
            }
        }

        // Obranně: vracej jen role/oprávnění odpovídající povoleným oblastem.
        assignments = assignments.stream()
                .filter(utr -> utr.getRole() != null && allowedScopes.contains(utr.getRole().getScope()))
                .toList();

        Set<String> roles = assignments.stream()
                .map(utr -> utr.getRole().getName())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Set<String> perms = assignments.stream()
                .flatMap(utr -> utr.getRole().getRolePermissions().stream())
                .map(rp -> rp.getPermission().getResource() + ":" + rp.getPermission().getAction())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        int policyVersion = 1;
        return new PolicySnapshot(u.getId(), roles.stream().toList(), perms.stream().toList(), policyVersion);
    }

    public record PolicySnapshot(UUID userId, List<String> roles, List<String> permissions, int policyVersion) {
    }
}
