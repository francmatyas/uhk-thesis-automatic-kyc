package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.repository;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.model.UserTenantRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface UserTenantRoleRepository extends JpaRepository<UserTenantRole, UUID> {
    List<UserTenantRole> findAllByUserIdAndTenantId(UUID userId, UUID tenantId);
    List<UserTenantRole> findAllByTenantId(UUID tenantId);

    boolean existsByUserIdAndTenantId(UUID userId, UUID tenantId);
    boolean existsByUserIdAndRoleIdAndTenantId(UUID userId, UUID roleId, UUID tenantId);

    @Modifying
    @Query("""
            delete from UserTenantRole r
            where r.user.id = :userId and r.tenant.id = :tenantId and r.isDeleted = true
            """)
    int purgeSoftDeleted(@Param("userId") UUID userId, @Param("tenantId") UUID tenantId);

    @Modifying
    @Query("""
            delete from UserTenantRole r
            where r.user.id = :userId and r.tenant.id = :tenantId
            """)
    int hardDeleteByUserIdAndTenantId(@Param("userId") UUID userId, @Param("tenantId") UUID tenantId);
}
