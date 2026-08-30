\set ON_ERROR_STOP on

DO $$
BEGIN
    IF current_database() <> 'claims_benchmark' THEN
        RAISE EXCEPTION 'Synthetic data may run only in claims_benchmark';
    END IF;
END $$;

CREATE TEMP TABLE benchmark_users (
    row_number INTEGER PRIMARY KEY,
    user_id BIGINT NOT NULL
);

WITH source_credentials AS (
    SELECT password_hash
    FROM users
    WHERE username = 'bench-claimant'
), inserted AS (
    INSERT INTO users (username, password_hash, email, role, created_at, status)
    SELECT 'synthetic-claimant-' || sequence,
           source_credentials.password_hash,
           'synthetic-' || sequence || '@benchmark.invalid',
           'CLAIMANT',
           TIMESTAMP '2024-01-01 00:00:00' + sequence * INTERVAL '1 second',
           'active'
    FROM generate_series(1, :claimant_count - 1) AS generated(sequence)
    CROSS JOIN source_credentials
    RETURNING user_id
)
INSERT INTO benchmark_users (row_number, user_id)
SELECT ROW_NUMBER() OVER (ORDER BY user_id), user_id
FROM inserted;

WITH benchmark_owner AS (
    SELECT user_id FROM users WHERE username = 'bench-claimant'
), generated_claims AS (
    SELECT sequence,
           CASE
               WHEN sequence <= LEAST(1000, GREATEST(100, :claim_count / 100))
                   THEN benchmark_owner.user_id
               ELSE benchmark_users.user_id
           END AS owner_id,
           CASE sequence % 20
               WHEN 0 THEN 'DRAFT'
               WHEN 1 THEN 'DRAFT'
               WHEN 2 THEN 'DRAFT'
               WHEN 3 THEN 'SUBMITTED'
               WHEN 4 THEN 'SUBMITTED'
               WHEN 5 THEN 'SUBMITTED'
               WHEN 6 THEN 'SUBMITTED'
               WHEN 7 THEN 'UNDER_REVIEW'
               WHEN 8 THEN 'UNDER_REVIEW'
               WHEN 9 THEN 'UNDER_REVIEW'
               WHEN 10 THEN 'APPROVED'
               WHEN 11 THEN 'APPROVED'
               WHEN 12 THEN 'APPROVED'
               WHEN 13 THEN 'APPROVED'
               WHEN 14 THEN 'REJECTED'
               WHEN 15 THEN 'REJECTED'
               WHEN 16 THEN 'REJECTED'
               ELSE 'SETTLED'
           END AS status,
           CASE sequence % 5
               WHEN 0 THEN 'MEDICAL'
               WHEN 1 THEN 'AUTO'
               WHEN 2 THEN 'HOME'
               WHEN 3 THEN 'TRAVEL'
               ELSE 'LIFE'
           END AS claim_type
    FROM generate_series(1, :claim_count) AS generated(sequence)
    CROSS JOIN benchmark_owner
    JOIN benchmark_users
      ON benchmark_users.row_number = 1 + ((sequence - 1) % (:claimant_count - 1))
)
INSERT INTO claims (
    user_id, claim_date, claim_amount, email_id, claim_type, claim_status,
    last_updated, idempotency_key, submission_idempotency_key, description
)
SELECT owner_id,
       :'anchor_timestamp'::timestamp
           - (sequence % 730) * INTERVAL '1 day'
           - (sequence % 86400) * INTERVAL '1 second',
       50.00 + ((sequence * 7919) % 250000)::numeric / 100,
       'claim-' || sequence || '@benchmark.invalid',
       claim_type,
       status,
       :'anchor_timestamp'::timestamp - (sequence % 86400) * INTERVAL '1 second',
       'bench-create-' || sequence,
       CASE WHEN status = 'DRAFT' THEN NULL ELSE 'bench-submit-' || sequence END,
       'Synthetic ' || lower(claim_type) || ' claim ' || sequence
           || ' with deterministic evidence reference ' || md5(sequence::text)
FROM generated_claims;

INSERT INTO claim_status_history (
    claim_id, previous_status, new_status, changed_by, reason, changed_at
)
SELECT claim_id, 'DRAFT', 'SUBMITTED', 'benchmark-loader', NULL,
       claim_date + INTERVAL '1 hour'
FROM claims
WHERE idempotency_key LIKE 'bench-create-%'
  AND claim_status <> 'DRAFT';

INSERT INTO claim_status_history (
    claim_id, previous_status, new_status, changed_by, reason, changed_at
)
SELECT claim_id, 'SUBMITTED', 'UNDER_REVIEW', 'benchmark-officer',
       'Synthetic review', claim_date + INTERVAL '2 hours'
FROM claims
WHERE idempotency_key LIKE 'bench-create-%'
  AND claim_status IN ('UNDER_REVIEW', 'APPROVED', 'REJECTED', 'SETTLED');

INSERT INTO claim_status_history (
    claim_id, previous_status, new_status, changed_by, reason, changed_at
)
SELECT claim_id, 'UNDER_REVIEW',
       CASE WHEN claim_status = 'SETTLED' THEN 'APPROVED' ELSE claim_status END,
       'benchmark-officer',
       CASE WHEN claim_status = 'REJECTED' THEN 'Synthetic policy exclusion' ELSE NULL END,
       claim_date + INTERVAL '3 hours'
FROM claims
WHERE idempotency_key LIKE 'bench-create-%'
  AND claim_status IN ('APPROVED', 'REJECTED', 'SETTLED');

INSERT INTO claim_status_history (
    claim_id, previous_status, new_status, changed_by, reason, changed_at
)
SELECT claim_id, 'APPROVED', 'SETTLED', 'benchmark-officer',
       'Synthetic settlement', claim_date + INTERVAL '4 hours'
FROM claims
WHERE idempotency_key LIKE 'bench-create-%'
  AND claim_status = 'SETTLED';

ANALYZE users;
ANALYZE claims;
ANALYZE claim_status_history;

SELECT COUNT(*) AS synthetic_users FROM users;
SELECT COUNT(*) AS synthetic_claims FROM claims;
SELECT COUNT(*) AS synthetic_history_rows FROM claim_status_history;
