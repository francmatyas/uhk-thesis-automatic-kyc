-- Convert audit_logs.old_value and new_value from jsonb to text to support
-- AES-GCM field-level encryption on new inserts going forward.
--
-- Existing rows are cast to their text (JSON) representation. The
-- EncryptedJsonNodeConverter backward-compat path handles these: values
-- that do not start with "enc:v1:" are parsed directly as plain JSON.
--
-- Note: ALTER COLUMN is DDL and does NOT trigger the append-only DML
-- triggers (trg_audit_logs_no_update / trg_audit_logs_no_delete).
ALTER TABLE audit_logs
    ALTER COLUMN old_value TYPE text USING old_value::text,
    ALTER COLUMN new_value TYPE text USING new_value::text;
