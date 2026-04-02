package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification_otp.repository;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification_otp.model.OtpType;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification_otp.model.VerificationOtp;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface VerificationOtpRepository extends JpaRepository<VerificationOtp, UUID> {

    Optional<VerificationOtp> findByVerificationIdAndType(UUID verificationId, OtpType type);

    List<VerificationOtp> findAllByVerificationIdAndType(UUID verificationId, OtpType type);
}