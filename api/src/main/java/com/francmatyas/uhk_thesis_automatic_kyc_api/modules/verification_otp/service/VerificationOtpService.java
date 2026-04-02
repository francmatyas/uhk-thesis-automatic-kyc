package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification_otp.service;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.client_identity.model.ClientIdentity;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.client_identity.repository.ClientIdentityRepository;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.model.Verification;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification.repository.VerificationRepository;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification_otp.model.OtpType;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification_otp.model.VerificationOtp;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification_otp.repository.VerificationOtpRepository;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification_step.model.VerificationStepType;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.verification_step.service.VerificationStepService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;

@Slf4j
@Service
@RequiredArgsConstructor
public class VerificationOtpService {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration OTP_TTL = Duration.ofMinutes(15);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final VerificationOtpRepository otpRepository;
    private final VerificationStepService verificationStepService;
    private final VerificationRepository verificationRepository;
    private final ClientIdentityRepository clientIdentityRepository;
    private final ObjectMapper objectMapper;

    /**
     * Vygeneruje a uloží nové OTP pro danou verifikaci a kontakt.
     * Nejprve zneplatní případné existující aktivní OTP stejného typu.
     *
     * @return surový OTP kód – pouze pro demo účely, loguje/vrací se volajícímu místo odeslání přes SMS/e-mail
     */
    @Transactional
    public String generate(Verification verification, OtpType type, String contact, String dialCode) {
        // Zneplatnit případné existující aktivní OTP stejného typu
        otpRepository.findAllByVerificationIdAndType(verification.getId(), type)
                .forEach(otpRepository::delete);

        String rawCode = generateCode();
        VerificationOtp otp = VerificationOtp.builder()
                .tenantId(verification.getTenantId())
                .verification(verification)
                .type(type)
                .codeHash(hashCode(rawCode))
                .contact(contact)
                .dialCode(dialCode)
                .expiresAt(Instant.now().plus(OTP_TTL))
                .attempts(0)
                .build();
        otpRepository.save(otp);

        // Demo: zalogovat kód místo odeslání skutečného e-mailu/SMS
        log.info("[DEMO] OTP for verification={} type={} contact={} code={}",
                verification.getId(), type, contact, rawCode);

        return rawCode;
    }

    /**
     * Ověří zadaný OTP kód. Při úspěchu dokončí odpovídající krok.
     *
     * @return true, pokud je kód správný a není expirovaný
     * @throws OtpException pokud OTP expirovalo, byl překročen maximální počet pokusů nebo nebylo nalezeno
     */
    @Transactional
    public boolean verify(Verification verification, OtpType type, String submittedCode) {
        VerificationOtp otp = otpRepository
                .findByVerificationIdAndType(verification.getId(), type)
                .orElseThrow(() -> new OtpException("otp_not_found"));

        if (otp.getExpiresAt().isBefore(Instant.now())) {
            throw new OtpException("otp_expired");
        }

        if (otp.getAttempts() >= MAX_ATTEMPTS) {
            failStep(verification, type, otp, "max_attempts_exceeded");
            throw new OtpException("otp_max_attempts");
        }

        otp.setAttempts(otp.getAttempts() + 1);

        if (!hashCode(submittedCode).equals(otp.getCodeHash())) {
            otpRepository.save(otp);
            if (otp.getAttempts() >= MAX_ATTEMPTS) {
                failStep(verification, type, otp, "max_attempts_exceeded");
                throw new OtpException("otp_max_attempts");
            }
            return false;
        }

        otp.setVerifiedAt(Instant.now());
        otpRepository.save(otp);

        saveContactToClientIdentity(verification, type, otp.getContact(), otp.getDialCode());

        VerificationStepType stepType = type == OtpType.EMAIL
                ? VerificationStepType.EMAIL_VERIFICATION
                : VerificationStepType.PHONE_VERIFICATION;

        ObjectNode details = objectMapper.createObjectNode();
        details.put("contact", otp.getContact());
        details.put("verifiedAt", otp.getVerifiedAt().toString());

        verificationStepService.completeOptionalStep(verification, stepType, details);
        return true;
    }

    // ------------------------------------------------------------------

    private void saveContactToClientIdentity(Verification verification, OtpType type,
                                              String contact, String dialCode) {
        try {
            verificationRepository.findById(verification.getId())
                    .map(Verification::getClientIdentity)
                    .ifPresent(ci -> {
                        if (type == OtpType.EMAIL) {
                            if (ci.getEmail() == null) ci.setEmail(contact);
                        } else {
                            if (ci.getPhone() == null) ci.setPhone(contact);
                            if (ci.getDialCode() == null && dialCode != null) ci.setDialCode(dialCode);
                        }
                        clientIdentityRepository.save(ci);
                    });
        } catch (Exception e) {
            log.warn("Failed to save contact to ClientIdentity for verification {}: {}",
                    verification.getId(), e.getMessage());
        }
    }

    private void failStep(Verification verification, OtpType type, VerificationOtp otp, String reason) {
        VerificationStepType stepType = type == OtpType.EMAIL
                ? VerificationStepType.EMAIL_VERIFICATION
                : VerificationStepType.PHONE_VERIFICATION;

        ObjectNode details = objectMapper.createObjectNode();
        details.put("reason", reason);
        details.put("contact", otp.getContact());

        verificationStepService.failOptionalStep(verification, stepType, details);
    }

    private static String generateCode() {
        return String.format("%06d", 100_000 + RANDOM.nextInt(900_000));
    }

    private static String hashCode(String code) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(code.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public static class OtpException extends RuntimeException {
        public OtpException(String message) {
            super(message);
        }
    }
}
