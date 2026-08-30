-- Supports bounded date-range scans used by live daily reporting.
CREATE INDEX idx_claims_claim_date ON claims (claim_date);
