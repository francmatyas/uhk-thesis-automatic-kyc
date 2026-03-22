package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.repository;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {
    Optional<User> findByEmailHash(String emailHash);

    List<User> findDistinctByUserTenantMembershipsTenantId(UUID tenantId);

    Optional<User> findByIdAndUserTenantMembershipsTenantId(UUID id, UUID tenantId);

    @Query(value = "select * from users where lower(trim(email)) = :normalizedEmail limit 1", nativeQuery = true)
    Optional<User> findLegacyByNormalizedEmail(@Param("normalizedEmail") String normalizedEmail);

    @Query("""
            SELECT u FROM User u
            WHERE u.id NOT IN (
                SELECT m.user.id FROM UserTenantMembership m WHERE m.tenant.id = :tenantId
            )
            """)
    Page<User> findUsersNotMemberOf(@Param("tenantId") UUID tenantId, Pageable pageable);
}
