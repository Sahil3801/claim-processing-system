SELECT CAST(claim_date AS DATE) AS report_date,
       COUNT(*) AS total_claims,
       COALESCE(SUM(claim_amount), 0) AS total_amount,
       COALESCE(AVG(claim_amount), 0) AS average_amount
FROM claims
WHERE claim_date >= TIMESTAMP '2025-12-01 00:00:00'
  AND claim_date < TIMESTAMP '2025-12-08 00:00:00'
GROUP BY CAST(claim_date AS DATE)
ORDER BY CAST(claim_date AS DATE);
