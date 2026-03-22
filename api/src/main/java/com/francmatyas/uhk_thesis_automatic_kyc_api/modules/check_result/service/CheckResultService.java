package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.check_result.service;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.check_result.model.CheckResult;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.check_result.model.CheckType;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.check_result.repository.CheckResultRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CheckResultService {

    private final CheckResultRepository repository;

    public List<CheckResult> findAllByVerification(UUID verificationId) {
        return repository.findAllByVerificationId(verificationId);
    }

    public Optional<CheckResult> findByVerificationAndType(UUID verificationId, CheckType checkType) {
        return repository.findByVerificationIdAndCheckType(verificationId, checkType);
    }

    @Transactional
    public CheckResult save(CheckResult checkResult) {
        return repository.save(checkResult);
    }
}