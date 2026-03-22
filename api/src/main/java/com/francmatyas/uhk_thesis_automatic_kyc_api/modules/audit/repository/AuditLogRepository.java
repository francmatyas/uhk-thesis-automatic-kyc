package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.audit.repository;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.audit.model.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    @Query("""
            SELECT a FROM AuditLog a
            WHERE a.tenantId = :tenantId
              AND (:entityType IS NULL OR a.entityType = :entityType)
              AND (:action IS NULL OR a.action = :action)
              AND (:actorUserId IS NULL OR a.actorUserId = :actorUserId)
            ORDER BY a.createdAt DESC
            """)
    Page<AuditLog> findByTenantFiltered(
            @Param("tenantId") UUID tenantId,
            @Param("entityType") String entityType,
            @Param("action") String action,
            @Param("actorUserId") UUID actorUserId,
            Pageable pageable);

    @Query("""
            SELECT a FROM AuditLog a
            WHERE (:tenantId IS NULL OR a.tenantId = :tenantId)
              AND (:entityType IS NULL OR a.entityType = :entityType)
              AND (:action IS NULL OR a.action = :action)
              AND (:actorUserId IS NULL OR a.actorUserId = :actorUserId)
            ORDER BY a.createdAt DESC
            """)
    Page<AuditLog> findAllFiltered(
            @Param("tenantId") UUID tenantId,
            @Param("entityType") String entityType,
            @Param("action") String action,
            @Param("actorUserId") UUID actorUserId,
            Pageable pageable);
}
