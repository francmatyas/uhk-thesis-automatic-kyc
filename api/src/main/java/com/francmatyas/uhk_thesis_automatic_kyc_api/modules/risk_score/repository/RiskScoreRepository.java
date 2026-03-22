package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.risk_score.repository;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.risk_score.model.RiskScore;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RiskScoreRepository extends JpaRepository<RiskScore, UUID> {

    Optional<RiskScore> findByVerificationId(UUID verificationId);
}