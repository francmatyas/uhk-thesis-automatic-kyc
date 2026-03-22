package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.check_result.repository;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.check_result.model.CheckResult;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.check_result.model.CheckType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CheckResultRepository extends JpaRepository<CheckResult, UUID> {

    List<CheckResult> findAllByVerificationId(UUID verificationId);

    Optional<CheckResult> findByVerificationIdAndCheckType(UUID verificationId, CheckType checkType);
}