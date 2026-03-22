package com.francmatyas.uhk_thesis_automatic_kyc_api.security;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.integration.model.ApiKeyStatus;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.integration.repository.ApiKeyRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ApiKeyAuthenticationService {

    private final ApiKeyRepository apiKeyRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public Optional<ApiKeyPrincipal> authenticate(String publicKey, String secret) {
        if (publicKey == null || publicKey.isBlank() || secret == null || secret.isBlank()) {
            return Optional.empty();
        }

        return apiKeyRepository.findByPublicKeyAndStatus(publicKey.trim(), ApiKeyStatus.ACTIVE)
                .filter(k -> passwordEncoder.matches(secret, k.getSecretHash()))
                .map(k -> {
                    apiKeyRepository.touchLastUsedAt(k.getId(), Instant.now());
                    return new ApiKeyPrincipal(k.getId(), k.getTenant().getId(), k.getName(), k.getPublicKey());
                });
    }
}

