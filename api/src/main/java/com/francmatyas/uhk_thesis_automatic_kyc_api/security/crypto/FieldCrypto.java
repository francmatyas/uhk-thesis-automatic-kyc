package com.francmatyas.uhk_thesis_automatic_kyc_api.security.crypto;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

public final class FieldCrypto {

    private static final String CIPHER = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int GCM_IV_BYTES = 12;
    private static final String CIPHERTEXT_PREFIX = "enc:v1:";
    private static final String DEFAULT_MASTER_SECRET = "dev-only-change-APP_ENCRYPTION_MASTER_KEY";

    private static final Object LOCK = new Object();
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final Base64.Encoder BASE64_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_DECODER = Base64.getUrlDecoder();

    private static volatile SecretKeySpec encryptionKey;
    private static volatile SecretKeySpec hashKey;

    private FieldCrypto() {
    }

    public static void configure(String masterSecret) {
        byte[] master = sha256((masterSecret == null || masterSecret.isBlank()
                ? DEFAULT_MASTER_SECRET
                : masterSecret.trim()).getBytes(StandardCharsets.UTF_8));
        byte[] enc = sha256(concat(master, "enc:v1".getBytes(StandardCharsets.UTF_8)));
        byte[] hash = sha256(concat(master, "hash:v1".getBytes(StandardCharsets.UTF_8)));
        synchronized (LOCK) {
            encryptionKey = new SecretKeySpec(enc, "AES");
            hashKey = new SecretKeySpec(hash, "HmacSHA256");
        }
    }

    public static String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        initializeIfNeeded();
        try {
            byte[] iv = new byte[GCM_IV_BYTES];
            SECURE_RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            return CIPHERTEXT_PREFIX
                    + BASE64_ENCODER.encodeToString(iv)
                    + ":"
                    + BASE64_ENCODER.encodeToString(ciphertext);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Failed to encrypt field", ex);
        }
    }

    public static String decrypt(String dbValue) {
        if (dbValue == null) {
            return null;
        }
        if (!dbValue.startsWith(CIPHERTEXT_PREFIX)) {
            // Zpětná kompatibilita pro nešifrované řádky z období před šifrováním.
            return dbValue;
        }
        initializeIfNeeded();

        String payload = dbValue.substring(CIPHERTEXT_PREFIX.length());
        int sep = payload.indexOf(':');
        if (sep <= 0 || sep >= payload.length() - 1) {
            throw new IllegalStateException("Invalid encrypted payload format");
        }

        String ivPart = payload.substring(0, sep);
        String ctPart = payload.substring(sep + 1);
        try {
            byte[] iv = BASE64_DECODER.decode(ivPart);
            byte[] ciphertext = BASE64_DECODER.decode(ctPart);

            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException ex) {
            throw new IllegalStateException("Failed to decrypt field", ex);
        }
    }

    public static String normalizeEmail(String email) {
        if (email == null) {
            return null;
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? null : normalized;
    }

    public static String hashEmail(String email) {
        String normalized = normalizeEmail(email);
        if (normalized == null) {
            return null;
        }
        initializeIfNeeded();
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(hashKey);
            byte[] digest = mac.doFinal(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Failed to hash email", ex);
        }
    }

    private static void initializeIfNeeded() {
        if (encryptionKey == null || hashKey == null) {
            configure(System.getenv("APP_ENCRYPTION_MASTER_KEY"));
        }
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }
}
