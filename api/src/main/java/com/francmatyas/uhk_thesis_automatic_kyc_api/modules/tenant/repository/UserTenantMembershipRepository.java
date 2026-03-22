package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.repository;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.model.UserTenantMembership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserTenantMembershipRepository extends JpaRepository<UserTenantMembership, UUID> {
    List<UserTenantMembership> findAllByUserId(UUID userId);
    List<UserTenantMembership> findAllByTenantId(UUID tenantId);

    Optional<UserTenantMembership> findByUserIdAndTenantId(UUID userId, UUID tenantId);

    Optional<UserTenantMembership> findFirstByUserIdAndIsDefaultTrue(UUID userId);

    @Modifying
    @Query("""
            delete from UserTenantMembership m
            where m.user.id = :userId and m.tenant.id = :tenantId and m.isDeleted = true
            """)
    int purgeSoftDeleted(@Param("userId") UUID userId, @Param("tenantId") UUID tenantId);

    @Modifying
    @Query("""
            delete from UserTenantMembership m
            where m.user.id = :userId and m.tenant.id = :tenantId
            """)
    int hardDeleteByUserIdAndTenantId(@Param("userId") UUID userId, @Param("tenantId") UUID tenantId);
}
