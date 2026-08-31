# Interview notes: Claims Processing System

Use these notes to explain implemented decisions and measured tradeoffs.
They are grounded in the [source architecture](architecture.md) and
[final Phase 16 report](../benchmarks/analysis/20260831T045941Z.md), not hypothetical
production outcomes. Setup and endpoint details are in the [README](../README.md).

## A concise project introduction

"I built a backend-focused claims workflow using Spring Boot and PostgreSQL,
with a React/TypeScript client. Claimants create and submit claims, officers
review and decide them, and admins see live aggregate reports. I implemented
role/ownership checks, database-backed idempotency, transactional status history,
Redis cache-aside reads, and post-commit Kafka notifications. I added tests,
Docker and CI configuration, manual AWS deployment preparation, and repeatable
local benchmarks on a 100K-claim synthetic dataset."

Avoid calling it a deployed enterprise system, microservices platform, payment
system, highly available architecture, or exactly-once event pipeline. There is
no evidence here of an AWS deployment, real customers, production traffic,
availability SLA, or cloud cost savings.

## Design questions and honest answers

### Why PostgreSQL and Flyway?

Claims need transactional state, referential integrity, constrained statuses,
precise decimal amounts, and aggregates. PostgreSQL provides those primitives.
Flyway V1-V6 versions the schema; Hibernate only validates it. H2 supports fast
tests, but Testcontainers PostgreSQL checks actual database migrations, numeric/
date behavior, unique keys, and constraints. H2 compatibility alone is not proof
of PostgreSQL correctness. Skipped Docker tests must be reported as skipped.

### How do you control the claim lifecycle?

The domain enum lists permitted edges: draft -> submitted -> under review ->
approved -> settled, or under review -> rejected. Claim/history writes share
one transaction. Invalid edges return 409; API rejection requires a reason.
There is also an hourly system job moving old submitted claims into review.
Settled means a recorded state, not a payment. History is stored, but the UI's
timeline currently infers progress from current status rather than retrieving
all recorded history events.

### What happens when two officers update the same claim?

The current general transition path does not have an optimistic version column
or an explicit row lock. Legal-edge validation alone cannot rule out concurrent
lost decisions. Creation/submission have targeted pessimistic locks for retry
handling; they should not be presented as complete concurrency control. A next
hardening decision would compare optimistic version conflicts versus targeted
write locks, add race tests, and map conflicts explicitly. That is not completed
functionality in this documentation phase.

### How does idempotency work?

Create and submit require separate `Idempotency-Key` values. Creation retains
its unique key and compares the retried body, locking/rechecking the owner row;
submission locks the claim and retains its own unique key. A matching retry
returns the current DTO without another transition/history/event. Conflicting
reuse returns 409. These are database-backed operation keys, not Redis locks.

Tradeoffs: same-owner creation serializes; keys do not expire; new keys can still
create equivalent business claims; the response is not a replay of the original
bytes. Cross-operation checks use two independently unique columns rather than
a globally unique operation ledger, so do not overstate concurrent cross-column
reuse protection. Officer actions are not idempotency-header endpoints.

### Why load roles from the database if JWT already contains a role?

JWT signature/issuer/expiry identify the subject, but backend permissions and
active status come from the current database user. This makes role changes and
deactivation effective on subsequent requests. It costs a database lookup per
authenticated request, intentionally. The role claim supports UI navigation;
client-side routes never replace server authorization. Claimant ownership is
checked even when a detail DTO came from Redis.

JWT uses an environment-provided HS256 secret and passwords use BCrypt. Frontend
localStorage is convenient but exposed to XSS; no refresh-token rotation, MFA,
or server-side token revocation list exists. Authentication design would need
review before a public deployment. There is no Google-login implementation to
claim just because legacy OAuth configuration placeholders remain in resources.

### Why cache-aside, and how consistent is it?

Detail/status reads fall back to PostgreSQL on misses and Redis failures. TTLs
bound retention; successful writes evict both keys after commit. Doing this
before commit could let another reader populate old database state or expose
rolled-back changes. Lists/reports stay live SQL queries.

After-commit eviction is still best-effort, not strict consistency: a failed
eviction or overlapping stale read/refill can leave old data until TTL expiry.
Redis timeouts can add latency during an outage. Versioned cache entries or
stronger coordination would need their own justification/testing, not tuning
solely to make a benchmark look better.

### Is the Kafka flow exactly-once?

No. Events are keyed by claim ID and sent only after database commit. The producer
uses idempotence/retries; the consumer group uses record acknowledgment, retries,
a DLT, and a unique processed-event marker. Already committed event IDs are
skipped, and notification failure does not roll back the original claim.

Two important windows remain: a crash after claim commit but before send can
lose an event because there is no outbox; SMTP can succeed before the marker
commits, allowing a duplicate email after a crash. Kafka producer idempotence
does not solve either cross-system problem. Single-node Kafka is also a deliberate
availability tradeoff. DLT replay and marker-retention cleanup are not automated.

### How does reporting avoid loading all claims into Java?

Repository native SQL projections compute counts, sums, and averages directly
in PostgreSQL. Pending means draft/submitted/under-review; daily reporting uses
claim creation date, with an inclusive date API converted to a half-open timestamp
range. The V6 B-tree index supports selective date predicates. Overall or broad
aggregations may still scan a lot of data; an index is not automatically faster
for every query. Reports read `claims`, not the old report snapshot tables.

### What did the performance measurements actually show?

The strongest result was the selective daily-report SQL query. In one local
before/after pgbench comparison, its average latency fell **58.302 -> 10.205 ms
(82.50%)** and throughput rose **68.608 -> 391.946 transactions/s (5.71x)**.
EXPLAIN corroborated the change with a sequential-to-bitmap-index scan and
**3,138 -> 170 shared-buffer block accesses (94.58% fewer)**. That is not physical
disk I/O saved or proof that all HTTP reporting became 5.71x faster.

Conditions: a 100K seeded database (100,105 rows after mixed writes), 959 matching
rows in the tested seven-day interval, PostgreSQL 16.15 on local tmpfs, four
pgbench clients, two threads, one 30-second measurement per mode after warmup.
The local Docker/WSL2 host had an i5-1035G1, 8 visible logical CPUs, and 7.64 GiB
Docker memory. These are not measured EC2/RDS results.

### Did Redis make the system faster?

Not conclusively in the completed experiment. Medians of three cache-off versus
cache-on trials were:

| Metric | Off | On | Interpretation |
| --- | ---: | ---: | --- |
| p50 | 188.292 ms | 190.551 ms | 1.20% worse |
| p95 | 388.325 ms | 306.694 ms | 21.02% lower, but inconsistent between pairs |
| p99 | 496.720 ms | 407.436 ms | 17.97% lower, but inconsistent between pairs |
| Throughput | 114.531 requests/s | 111.915 requests/s | 2.28% lower |

Twenty constant VUs drove a one-CPU-limited application; 96.49-98.21% of scheduling
periods experienced throttling. Two paired trials showed no p95 win. Do not
cherry-pick the fastest run or turn group medians into a universal optimization
claim. Median-of-trial percentiles is not the same as pooled request percentiles.

The cache worked: **19,946 hits / 329 misses = 98.38%** over 20,275 measured
cache-on reads, with no recorded Redis evictions/errors. But only 1,000 IDs were
selected and the deterministic selector can reach at most 280 keys. This is a
warmed small-working-set result against a 100K database, not 100K unique cached
claims. Static benchmark principals also remove production's auth query. The
mixed database-authenticated run had only a **27.89%** Redis hit rate, including
one setup read. Neither result quantifies client serialization overhead alone.

### What do the error and throughput numbers prove?

There were **42,958 measured HTTP requests with zero unexpected status codes**
across six cache-isolation trials and one mixed run, excluding warmups/setup.
The mixed run observed 30.819 requests/s and 1,194.505 ms p95, with no cache-off
mixed counterpart. This proves only the observed finite workload, not sustained
production capacity, all business semantics, an SLA, or performance of every
review/approve/reject/settle endpoint.

### What is ready for CI and AWS?

GitHub Actions is configured for Maven verify, frontend locked install/test/build,
and Docker build/inspect, with no publish/deploy steps. Do not claim a successful
remote CI run without its run record. The manual AWS runbook prepares a Caddy/API/
Redis/Kafka EC2 host and private RDS PostgreSQL, verified DB TLS, environment
secrets, health checks, backups, and security-group requirements. Frontend hosting
is not included. No actual deployment or cloud cost saving is established.

This keeps managed services limited initially, with explicit single points of
failure. Instance sizes are starting plans, not measured capacity guarantees;
region pricing/eligibility and production hardening need operator review.

## Demo checklist

1. Start the local backend stack and Vite client; confirm health. Use disposable
   local accounts/data, never production secrets.
2. Register a claimant. Have a trusted operator provision separate officer/admin
   roles if demonstrating those screens; there is no public role-grant endpoint.
3. Create a draft and retry the same request/key: same claim ID, no duplicate row.
   Retry changed content with the key: 409. Submit with a different stable key.
4. Show another claimant is denied; officer reviews, then approves or rejects.
   Rejection needs a reason. Settle only an approved claim; attempt an invalid
   edge and explain the 409.
5. Show admin live summaries. Explain the UI timeline's missing audit-history
   endpoint rather than pretending all displayed steps have recorded timestamps.
6. Show focused tests and inspect PostgreSQL Testcontainers skip status. Explain
   unit mocks versus live Redis/Kafka/SMTP verification.
7. Present the final report, all three cache pairs, and both SQL plans. Do not
   rerun a heavy load test or deploy AWS just for the interview unless intended.

## Resume wording

Use only statements you can explain and reproduce with the archived evidence:

- "Optimized a PostgreSQL daily-report aggregation with a date index, reducing
  average query-benchmark latency by 82.5% (58.3 to 10.2 ms) and increasing
  throughput 5.7x in a local benchmark seeded with 100K claims."
- "Built repeatable k6/pgbench benchmarks against a 100K-claim, 215K-history-row
  synthetic dataset; observed zero HTTP-status failures across 42,958 measured
  requests at 20 concurrent virtual users."
- Optional: "Validated Redis cache-aside reads with a 98.38% hit rate across
  20,275 measured reads in a warmed small-working-set benchmark."

Avoid "100K requests/second", "Redis improved p95 by 21%" without the conflicting
evidence, "exactly-once notifications", "100% reliability", "deployed on AWS",
or unsupported test-coverage/cost claims. Read the full
[performance report](../benchmarks/analysis/20260831T045941Z.md) for formulas,
artifact names, limits, and independent raw-data verification.
