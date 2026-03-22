package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.service;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.User;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.model.Tenant;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.model.UserTenantMembership;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.repository.TenantRepository;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.repository.UserTenantMembershipRepository;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.repository.UserTenantRoleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TenantAccessService {

    private final UserTenantMembershipRepository membershipRepository;
    private final UserTenantRoleRepository userTenantRoleRepository;
    private final TenantRepository tenantRepository;

    public List<UserTenantMembership> listMemberships(UUID userId) {
        return membershipRepository.findAllByUserId(userId);
    }

    public Optional<Tenant> findTenant(UUID tenantId) {
        return tenantRepository.findById(tenantId);
    }

    public Optional<Tenant> findTenantBySlug(String slug) {
        if (slug == null || slug.isBlank()) {
            return Optional.empty();
        }
        return tenantRepository.findBySlug(slug.trim());
    }

    public boolean canAccessTenant(User user, UUID tenantId) {
        if (user == null || tenantId == null) {
            return false;
        }
        if (user.isProviderUser()) {
            return userTenantRoleRepository.existsByUserIdAndTenantId(user.getId(), tenantId);
        }
        return membershipRepository.findByUserIdAndTenantId(user.getId(), tenantId).isPresent();
    }
}
