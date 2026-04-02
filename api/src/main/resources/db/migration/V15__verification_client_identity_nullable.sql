-- client_identity_id is nullable while a verification is in INITIATED state.
-- The two-step flow: create verification → client fills form (submitFlow) → identity is linked.
ALTER TABLE verifications ALTER COLUMN client_identity_id DROP NOT NULL;
