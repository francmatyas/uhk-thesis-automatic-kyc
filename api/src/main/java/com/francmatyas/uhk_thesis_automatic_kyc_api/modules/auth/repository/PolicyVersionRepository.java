package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.repository;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.PolicyVersion;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PolicyVersionRepository extends JpaRepository<PolicyVersion, Integer> {
}
