package com.francmatyas.uhk_thesis_automatic_kyc_api.security;

import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.integration.model.ApiKey;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.integration.model.ApiKeyStatus;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.integration.repository.ApiKeyRepository;
import com.francmatyas.uhk_thesis_automatic_kyc_api.modules.tenant.model.Tenant;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ApiKeyAuthenticationServiceTests {

    @Test
    void authenticateActiveKeyWithMatchingSecret() {
        ApiKeyRepository repo = mock(ApiKeyRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        ApiKeyAuthenticationService service = new ApiKeyAuthenticationService(repo, encoder);

        UUID keyId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        Tenant tenant = new Tenant();
        tenant.setId(tenantId);

        ApiKey key = new ApiKey();
        key.setId(keyId);
        key.setTenant(tenant);
        key.setName("Primary key");
        key.setPublicKey("pk_live_123");
        key.setSecretHash("$2a$12$hash");
        key.setStatus(ApiKeyStatus.ACTIVE);

        when(repo.findByPublicKeyAndStatus("pk_live_123", ApiKeyStatus.ACTIVE)).thenReturn(Optional.of(key));
        when(encoder.matches("super-secret", "$2a$12$hash")).thenReturn(true);

        Optional<ApiKeyPrincipal> principal = service.authenticate("pk_live_123", "super-secret");

        assertTrue(principal.isPresent());
        assertEquals(keyId, principal.get().apiKeyId());
        assertEquals(tenantId, principal.get().tenantId());
        verify(repo, times(1)).touchLastUsedAt(eq(keyId), any(Instant.class));
    }

    @Test
    void rejectWhenSecretDoesNotMatch() {
        ApiKeyRepository repo = mock(ApiKeyRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        ApiKeyAuthenticationService service = new ApiKeyAuthenticationService(repo, encoder);

        Tenant tenant = new Tenant();
        tenant.setId(UUID.randomUUID());

        ApiKey key = new ApiKey();
        key.setId(UUID.randomUUID());
        key.setTenant(tenant);
        key.setPublicKey("pk_live_123");
        key.setSecretHash("$2a$12$hash");
        key.setStatus(ApiKeyStatus.ACTIVE);

        when(repo.findByPublicKeyAndStatus("pk_live_123", ApiKeyStatus.ACTIVE)).thenReturn(Optional.of(key));
        when(encoder.matches("wrong", "$2a$12$hash")).thenReturn(false);

        Optional<ApiKeyPrincipal> principal = service.authenticate("pk_live_123", "wrong");

        assertTrue(principal.isEmpty());
        verify(repo, never()).touchLastUsedAt(any(UUID.class), any(Instant.class));
    }
}

