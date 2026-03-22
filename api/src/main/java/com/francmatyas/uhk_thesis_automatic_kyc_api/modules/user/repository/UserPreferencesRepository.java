package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.user.repository;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.user.model.UserPreferences;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface UserPreferencesRepository extends JpaRepository<UserPreferences, UUID>, JpaSpecificationExecutor<UserPreferences> {
    Optional<UserPreferences> findByUserId(UUID userId);
}

