package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.repository;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface UserRoleRepository extends JpaRepository<UserRole, UUID> {
}
