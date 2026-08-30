ALTER TABLE claims ADD COLUMN submission_idempotency_key VARCHAR(128);

ALTER TABLE claims
    ADD CONSTRAINT uk_claims_submission_idempotency_key UNIQUE (submission_idempotency_key);
