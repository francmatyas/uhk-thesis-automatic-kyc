package com.francmatyas.uhk_thesis_automatic_kyc_api.security;

import java.util.UUID;

public record ApiKeyPrincipal(
        UUID apiKeyId,
        UUID tenantId,
        String name,
        String publicKey
) {
}

