package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.repository;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface PermissionRepository extends JpaRepository<Permission, UUID>, JpaSpecificationExecutor<Permission> {
    Optional<Permission> findByResourceAndAction(String resource, String action);
}
