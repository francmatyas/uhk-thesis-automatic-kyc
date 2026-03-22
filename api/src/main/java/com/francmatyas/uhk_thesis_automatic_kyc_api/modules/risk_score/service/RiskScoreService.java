package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.risk_score.service;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.risk_score.model.RiskScore;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.risk_score.repository.RiskScoreRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RiskScoreService {

    private final RiskScoreRepository repository;

    public Optional<RiskScore> findByVerification(UUID verificationId) {
        return repository.findByVerificationId(verificationId);
    }

    @Transactional
    public RiskScore save(RiskScore riskScore) {
        return repository.save(riskScore);
    }
}