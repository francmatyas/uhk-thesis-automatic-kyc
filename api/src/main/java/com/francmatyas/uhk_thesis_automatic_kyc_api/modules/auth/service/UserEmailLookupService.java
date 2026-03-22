package com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.service;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.model.User;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.auth.repository.UserRepository;
import com.francmatyas.uhk_thesis_automatic_kyc_api.security.crypto.FieldCrypto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserEmailLookupService {

    private final UserRepository userRepository;

    public Optional<User> findByEmail(String rawEmail) {
        String normalized = FieldCrypto.normalizeEmail(rawEmail);
        if (normalized == null) {
            return Optional.empty();
        }

        String emailHash = FieldCrypto.hashEmail(normalized);
        return userRepository.findByEmailHash(emailHash)
                .or(() -> userRepository.findLegacyByNormalizedEmail(normalized));
    }
}
