package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification_step.repository;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification_step.model.VerificationStep;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification_step.model.VerificationStepType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VerificationStepRepository extends JpaRepository<VerificationStep, UUID> {

    List<VerificationStep> findAllByVerificationId(UUID verificationId);

    Optional<VerificationStep> findByVerificationIdAndStepType(UUID verificationId, VerificationStepType stepType);
}