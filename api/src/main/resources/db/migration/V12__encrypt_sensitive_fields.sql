-- Widen columns that are being converted to AES-GCM encrypted text.
-- FieldCrypto.decrypt() already handles backward compatibility:
-- values without the "enc:v1:" prefix are returned as-is, so existing
-- plaintext rows remain readable until they are next written.

-- audit_logs: convert ip_address from inet to text (required for field-level encryption)
-- user_agent is already text; no column change needed.
ALTER TABLE audit_logs
    ALTER COLUMN ip_address TYPE text USING ip_address::text;

-- stored_documents: widen original_filename and storage_key to text
ALTER TABLE stored_documents
    ALTER COLUMN original_filename TYPE text,
    ALTER COLUMN storage_key TYPE text;

-- The storage_key index is based on equality lookups against the raw value.
-- After encryption, ciphertext is non-deterministic (random IV per write),
-- making equality-based index scans useless.
DROP INDEX IF EXISTS ix_stored_documents_storage_key;

-- tenants: widen billing_customer_id to text
ALTER TABLE tenants
    ALTER COLUMN billing_customer_id TYPE text;

-- webhook_delivery_jobs: convert event_payload from jsonb to text for encryption.
-- Existing JSONB values are cast to their text representation for backward compatibility.
-- The EncryptedJsonNodeConverter will transparently parse these on first read.
ALTER TABLE webhook_delivery_jobs
    ALTER COLUMN event_payload TYPE text USING event_payload::text;

-- webhook_endpoints.url is already text; no column change needed.
-- webhook_delivery_attempts.response_body is already text; no column change needed.