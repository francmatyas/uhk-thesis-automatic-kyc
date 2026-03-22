package com.francmatyas.uhk_thesis_automatic_kyc_api.security.crypto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class FieldCryptoBootstrap {

    private static final Logger log = LoggerFactory.getLogger(FieldCryptoBootstrap.class);

    public FieldCryptoBootstrap(
            @Value("${app.encryption.master-key:}") String masterKey,
            @Value("${app.env:dev}") String appEnv
    ) {
        if ((masterKey == null || masterKey.isBlank()) && "prod".equalsIgnoreCase(appEnv)) {
            throw new IllegalStateException("APP_ENCRYPTION_MASTER_KEY is required in prod");
        }
        if (masterKey == null || masterKey.isBlank()) {
            log.warn("APP_ENCRYPTION_MASTER_KEY is not set; using a dev fallback key.");
        }
        FieldCrypto.configure(masterKey);
    }
}
