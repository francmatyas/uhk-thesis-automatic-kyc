-- Widen columns that are being converted to AES-GCM encrypted text.
-- FieldCrypto.decrypt() already handles the backward-compatibility case:
-- values without the "enc:v1:" prefix are returned as-is, so existing
-- plaintext rows remain readable until they are next written.

-- user_profiles: phoneNumber, dialCode, gender, dateOfBirth
ALTER TABLE user_profiles
    ALTER COLUMN phone_number  TYPE text,
    ALTER COLUMN dial_code     TYPE text,
    ALTER COLUMN gender        TYPE text,
    ALTER COLUMN date_of_birth TYPE text USING date_of_birth::text;

-- user_sessions: ipAddress, userAgent
ALTER TABLE user_sessions
    ALTER COLUMN ip_address TYPE text,
    ALTER COLUMN user_agent TYPE text;
