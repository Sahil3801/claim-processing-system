# Claims Processing System

A backend-focused claims workflow project built with Java 17, Spring Boot 3.3,
PostgreSQL, Redis, and Kafka, with a React + TypeScript + Vite client.

The objective is to demonstrate controlled claim transitions, database-backed
authorization, retry-safe creation/submission, transactional history, optional
caching, asynchronous status notifications, and measurable SQL performance.
This is a portfolio/reference implementation, not a certified insurance platform.

Phase 17 finalizes documentation of the existing implementation. Docker and
GitHub Actions configuration and manual EC2/RDS deployment preparation are
included; **an AWS deployment, production SLA, and cloud performance have not
been established by the repository's benchmark evidence**.

## Documentation map

- [Architecture, transaction boundaries, and limitations](docs/architecture.md)
- [Interview notes and defensible resume statements](docs/interview-notes.md)
- [Local Docker stack](DOCKER.md)
- [Frontend development](frontend/README.md)
- [GitHub Actions and deployment permissions](.github/CI-CD.md)
- [Manual AWS deployment preparation/runbook](deploy/aws/README.md)
- [Benchmark methodology and commands](benchmarks/README.md)
- [Final measured 100K-claim performance report](benchmarks/analysis/20260831T045941Z.md)

## Architecture

```text
React client -- HTTP /api + Bearer JWT --> Spring Security
                                              |
                                    REST controllers + DTOs
                                              |
                                    Transactional services
                                      /       |        \
                           PostgreSQL       Redis     after commit
                           + Flyway         cache          |
                                                       Kafka status topic
                                                           |
                                                  notification consumer
                                                  /                 \
                                          PostgreSQL marker       SMTP
```

PostgreSQL owns users, claims, history, and consumer deduplication markers. Redis
is disposable. Kafka handles status notifications outside the claim transaction.
Reporting reads live SQL aggregates, not Redis or stale reporting snapshots.
This is one Spring Boot application with supporting infrastructure, not a set
of independently deployed domain microservices.

## Run locally

Prerequisites: Docker Engine/Desktop with Compose v2-compatible commands; Node.js
22 and npm for the frontend; Java 17 for Maven outside Docker. The Maven wrapper
downloads Maven/dependencies when needed. Commands below use PowerShell.

### Backend and infrastructure

```powershell
# First-time setup only; do not overwrite an existing .env.
Copy-Item .env.example .env
# Edit .env: replace PostgreSQL/Redis passwords and JWT_SECRET.
# For a local-only API, also set APP_BIND_ADDRESS=127.0.0.1.
docker compose up --build -d
docker compose ps
Invoke-RestMethod http://localhost:8080/actuator/health
```

Only the backend port is published. PostgreSQL data lives in a named volume;
Redis has no persistence. The backend image uses a multi-stage build and a
non-root runtime user. [DOCKER.md](DOCKER.md) covers logs, limits, and shutdown.

Without working SMTP, notification deliveries can fail/retry and go to the DLT;
leaving mail credentials blank does not disable the consumer. To opt out of
notifications locally, pass `SPRING_KAFKA_LISTENER_AUTO_STARTUP=false` into the
app container via an explicit Compose override. The supplied benchmark stack
already disables this listener; the normal stack does not.

### Frontend

In a second terminal, from the repository root:

```powershell
cd frontend
# First-time setup only.
Copy-Item .env.example .env
npm ci
npm run dev
```

Open `http://localhost:5173`. Vite proxies `/api` to `http://localhost:8080`.
The frontend is not packaged into the backend Docker image or either deployment
Compose stack. A deployed frontend needs hosting and same-origin API proxying
or an explicitly configured backend CORS policy; setting a URL alone does not
enable cross-origin requests.

Register through the UI to create a `CLAIMANT`. Officer/admin accounts must be
provisioned by a trusted database operator; public registration cannot choose
a role, and there is no role-management API or seeded default administrator.

### Minimal API example

Use a unique username/email on each registration; sample credentials are local
examples, not deployed accounts. Keep the returned user ID for creation.

```powershell
$apiBase = 'http://localhost:8080/api'
$demoUser = Invoke-RestMethod -Method Post -Uri "$apiBase/auth/register" `
  -ContentType 'application/json' `
  -Body '{"username":"demo-claimant","password":"local-demo-password","email":"demo-claimant@example.com"}'
$login = Invoke-RestMethod -Method Post -Uri "$apiBase/auth/login" `
  -ContentType 'application/json' `
  -Body '{"username":"demo-claimant","password":"local-demo-password"}'
$authHeaders = @{ Authorization = "Bearer $($login.token)" }
$createHeaders = $authHeaders.Clone()
$createHeaders['Idempotency-Key'] = [guid]::NewGuid().ToString()
$createBody = @{
  userId = $demoUser.userId
  claimAmount = 125.50
  claimType = 'MEDICAL'
  description = 'Local demonstration claim'
} | ConvertTo-Json
$claim = Invoke-RestMethod -Method Post -Uri "$apiBase/claims" `
  -Headers $createHeaders -ContentType 'application/json' -Body $createBody
$submitHeaders = $authHeaders.Clone()
$submitHeaders['Idempotency-Key'] = [guid]::NewGuid().ToString()
Invoke-RestMethod -Method Post -Uri "$apiBase/claims/$($claim.claimId)/submit" `
  -Headers $submitHeaders
Invoke-RestMethod -Uri "$apiBase/claims/my?page=0&size=20&sort=claimDate,desc" `
  -Headers $authHeaders
```

On a transport retry, reuse the same key and body for that operation. Do not
generate a new key for every retry, and do not reuse the creation key to submit.

## Claim lifecycle

```text
create -> DRAFT -> SUBMITTED -> UNDER_REVIEW -> APPROVED -> SETTLED
                                    |
                                    +------> REJECTED
```

Creation saves a draft; submission is a separate operation. `REJECTED` and
`SETTLED` are terminal. `ClaimStatus`/`Claim.transitionTo` reject invalid edges,
including repeated officer actions. Each actual transition saves the previous
status, new status, actor, optional reason, and timestamp in
`claim_status_history` in the same database transaction. Rejection requires a
nonblank reason of at most 500 characters at the API boundary.

An existing hourly batch task also moves submitted claims older than 24 hours
to review as `SYSTEM_BATCH`; review is not exclusively a manual action. Initial
draft creation does not create a transition-history entry. Settlement records
status only: there is no payment processor or transfer of funds.

The UI's status timeline is inferred from the current status plus creation and
latest-update timestamps. The stored history has no REST retrieval endpoint;
the UI does not display an audited timestamp for every intermediate step.

## REST API and access

JSON request/response DTOs are used, rather than returning JPA entities.
Protected calls use `Authorization: Bearer <token>`.

| Method and path | Access | Contract |
| --- | --- | --- |
| `POST /api/auth/register` | Public | Username, password, email; creates claimant; 201 |
| `POST /api/auth/login` | Public | Username/password; returns username + JWT; 200 |
| `POST /api/claims` | Claimant | Own user ID; required `Idempotency-Key`; draft DTO; 201 |
| `POST /api/claims/{id}/submit` | Owning claimant | Required distinct `Idempotency-Key`; 200 |
| `GET /api/claims/{id}` | Owner, officer, admin | Detail DTO including current status |
| `GET /api/claims/my` | Claimant | Own claims; paginated |
| `GET /api/claims` | Officer, admin | Paginated; optional `status`, `claimType`, `userId` |
| `POST /api/claims/{id}/review` | Officer, admin | Submitted to under review |
| `POST /api/claims/{id}/approve` | Officer, admin | Under review to approved |
| `POST /api/claims/{id}/reject` | Officer, admin | Under review to rejected; body `{"reason":"..."}` |
| `POST /api/claims/{id}/settle` | Officer, admin | Approved to settled |
| `GET /api/reports/summary` | Admin only | Total/count/amount/average and outcome summaries |
| `GET /api/reports/status` | Admin only | Counts, sums, averages by status |
| `GET /api/reports/claim-types` | Admin only | Counts, sums, averages by claim type |
| `GET /api/reports/daily?from=YYYY-MM-DD&to=YYYY-MM-DD` | Admin only | Inclusive date range; groups by creation date |
| `GET /actuator/health` and health subpaths | Public | Status/probe information without health details |

Review/approve/settle accept an optional `{"reason":"..."}` body (maximum 500
characters). There is no general public claim update/delete/status-history
endpoint. The service-level status read is not a separate REST endpoint.

Pagination is zero-based: `page`, `size` (default 20), and `sort` (default
`claimDate`, ascending). Response: `content`, `page`, `size`, `totalElements`,
`totalPages`, `last`. Use uppercase enum statuses; claim-type filtering is
trimmed/case-insensitive. Claim type is a required string, not a fixed backend
enum; UI examples include medical, auto, home, travel, and life.

Create requests require an existing positive `userId`, positive `claimAmount`
with up to 17 integer digits and 2 fractional digits, nonblank `claimType`
(maximum 100), and nonblank `description` (maximum 2,000). `emailId` is optional
but must be valid if supplied. The user ID must belong to the authenticated
claimant. Monetary storage uses `BigDecimal` / `NUMERIC(19,2)`; no currency or
exchange-rate model is implemented.

`@RestControllerAdvice` and security handlers use `timestamp`, `status`, `error`,
`message`, `path`, and `violations`. Common results: 400 validation/missing key,
401 authentication required, 403 role/ownership denial, 404 claim/user missing,
409 invalid transition, duplicate/data-integrity conflict, or idempotency-key
reuse. Failed login is an exception to the envelope: it currently returns an
empty 401 response. See the [architecture guide](docs/architecture.md) for
legacy notification routes and other API limitations.

## PostgreSQL and Flyway

PostgreSQL is the runtime source of truth; H2 is test-only. Flyway owns schema
changes and validates migrations at startup; Hibernate uses `ddl-auto=validate`,
not schema update. Open Session in View is disabled; JDBC timestamps use UTC.

| Migration | Purpose |
| --- | --- |
| V1 | Users, claims, status history, legacy notification/report tables; keys/checks/indexes |
| V2 | Required claim description with legacy-row backfill |
| V3 | Role normalization and allowed-role check |
| V4 | Unique submission idempotency key |
| V5 | Processed Kafka event ID table and processed-time index |
| V6 | Claim-date index for bounded daily reporting |

Live reports aggregate `claims` in PostgreSQL; the older `claim_reports` and
`claims_summaries` tables are not the current reports' data source. "Pending"
means draft + submitted + under review. Daily `to` is implemented as an
exclusive next-day boundary; only dates with matching rows are returned.
Do not edit applied migrations or blindly baseline an existing unmanaged schema.

## Security and retry semantics

- Spring Security uses a stateless `SecurityFilterChain`, BCrypt passwords,
  and HS256 JWT signature/issuer/expiry verification. `JWT_SECRET` is required
  and must contain at least 32 characters; default token lifetime is 15 minutes.
- Roles are `CLAIMANT`, `CLAIMS_OFFICER`, and `ADMIN`. Database user status and
  role are reloaded on each authenticated request, rather than trusting the
  token's role claim. Claimant ownership is checked even on Redis hits.
- The frontend stores the session in `localStorage`, adds the JWT with an Axios
  interceptor, and logs out on expiration/401. Role-based routes are UX guards,
  not the authorization boundary. No refresh-token, MFA, or revocation-list
  implementation exists; localStorage requires care about XSS.
- Creation/submission require a trimmed, nonblank `Idempotency-Key` header of
  at most 128 characters. Separate unique database columns retain each key.
  Creation locks the owner row and rechecks the request; submission locks the
  claim row. Reusing a key for incompatible input/operation returns 409.
- A valid retry returns the claim's **current DTO**, not a stored byte-for-byte
  original response, and does not repeat history/event creation. Create retries
  still return 201. This does not detect two equivalent claims sent with two
  different keys or provide exactly-once behavior for every endpoint.

## Redis cache-aside

Detail/status reads check `claims:detail:{id}` / `claims:status:{id}` first, then
read PostgreSQL and populate Redis on misses. Default TTLs are 10 minutes for
details and 5 minutes for status. String keys and JSON values are used.
Successful mutations register eviction of both keys **after database commit**;
rollbacks do not evict. Cache exceptions fall back to PostgreSQL or skip cache
writes/evictions. Timeouts can still add latency during an outage.

This is best-effort cache-aside, not strongly consistent caching: failed
eviction or a concurrent stale read/refill can leave stale data until expiry.
List/report queries remain live database queries. Production defaults differ
from the benchmark's 30-minute TTL and static authentication fixture.

## Kafka status notifications

Transitions publish `ClaimStatusEvent` (UUID event ID, claim ID, old/new status,
actor, user/email, time) to `claims.status.v1`, keyed by claim ID, only after a
successful database commit. Producer settings include `acks=all`, idempotence,
10 retries, and a 120-second delivery timeout. These settings do not create
replication when only one broker is configured.

The `claims-notifications-v1` group uses record acknowledgment and no consumer
auto-commit. Retryable failures default to two retries at one-second intervals,
then `claims.status.v1.dlt`; some nonretryable errors can go straight to recovery.
A unique `processed_kafka_events.event_id` marker prevents already-completed
events from repeating notification work. Email failure rolls back the consumer
marker for retry, not the successful claim transaction.

There is **no durable outbox**: a crash after PostgreSQL commit but before Kafka
delivery can lose an event. SMTP is external to the consumer transaction, so a
crash after sending mail can still cause duplicate delivery. DLT replay and
event-marker retention cleanup are not automated. See the architecture guide
for the failure windows and tradeoffs.

## Configuration and secrets

Use [.env.example](.env.example) for Compose and
[production.env.example](deploy/aws/production.env.example) for deployment prep.
Spring's native environment variables include `DB_JDBC_URL` (or `DB_HOST`,
`DB_PORT`, `DB_NAME`), `DB_USERNAME`, required `DB_PASSWORD`, required `JWT_SECRET`,
`REDIS_*`, `KAFKA_BOOTSTRAP_SERVERS`, and optional `MAIL_*` settings. TTLs use
`CLAIM_CACHE_DETAIL_TTL` / `CLAIM_CACHE_STATUS_TTL`; cache enablement uses
`CLAIMS_CACHE_ENABLED`.

Compose maps its `POSTGRES_*` variables to the app's `DB_*` values. A Compose
`.env` is not automatically loaded by a direct Maven/Spring process, and adding
a variable to `.env` does not pass it to a container unless Compose maps it.
Never put backend credentials in `VITE_*`, Git, images, or Docker build arguments.
Do not activate the benchmark profile/static-user fixture outside isolated tests.

## Tests and verification

```powershell
# Repository root; Java 17. Docker is needed for PostgreSQL integration tests.
.\mvnw.cmd -B -ntp verify
# Explicit PostgreSQL integration subset (inspect skipped-test count).
.\mvnw.cmd -B -ntp '-Dtest=ClaimsPostgresIntegrationTest' test

cd frontend
npm ci
npm test
npm run build
```

On Bash use `./mvnw` instead of `.\mvnw.cmd`. JUnit 5, Mockito, Spring Boot Test,
MockMvc/security tests, H2 repository tests, and Testcontainers PostgreSQL tests
cover transitions, validation, ownership, idempotency, reporting, migrations,
constraints, cache fallback/after-commit eviction, and Kafka publication/consumer
logic. Frontend Vitest/Testing Library tests cover authentication, route guards,
and API idempotency behavior.

Testcontainers is configured with `disabledWithoutDocker=true`: a green build
without Docker can skip all five PostgreSQL integration tests. Inspect
`target/surefire-reports/`, not just the exit code. `verify` generates JaCoCo
HTML/CSV/XML in `target/site/jacoco/`; no coverage threshold or fixed percentage
is claimed. Redis/Kafka unit mocks do not replace real broker/outage testing,
and the suite is not a complete browser-to-SMTP end-to-end test.

## Docker, CI/CD, and AWS preparation

[GitHub Actions](.github/workflows/ci.yml) runs on push, pull request, and manual
dispatch: Maven verify; Node 22 `npm ci`/test/build; and backend Docker image
build/inspect. It uses caches, timeouts, cancellation of superseded runs, and
read-only repository permissions. The image job has `push: false`; **no image
publishing or deployment job is enabled**. Configuration alone is not proof of
a successful remote workflow run.

The [AWS runbook](deploy/aws/README.md) prepares one EC2 host for Caddy HTTPS,
Spring Boot, Redis, and single-node Kafka, with a private RDS PostgreSQL database.
It includes manual bootstrap/deploy/health-check scripts, a production profile,
resource limits, RDS `sslmode=verify-full`, protected runtime environment files,
backup/rollback steps, and security-group rules. Only Caddy publishes ports;
RDS port 5432 is restricted to the EC2 security group. Frontend hosting is not
included. This is a single-host, Single-AZ starting topology, not high availability
or a measured guarantee that a selected instance size is sufficient. Resources
must be created and deployment authorized by an operator; no AWS deployment was
performed as part of documentation finalization.

## Measured performance: Phase 16

The [final report](benchmarks/analysis/20260831T045941Z.md) analyzes run
`20260831T045941Z`: **100,000 seeded claims and 215,000 history rows**, local Docker
Desktop/WSL2 on an i5-1035G1 host with 8 visible logical CPUs and 7.64 GiB Docker
memory. Cache and mixed trials used 20 constant VUs; the app had a one-CPU limit.
PostgreSQL data was on tmpfs, not RDS storage. Cache trials used three 60-second
measurements per mode after 15-second warmups and benchmark-only static JWT
principals. These are synthetic local measurements, not production/cloud results.

| Median of three detail-read trials | Cache off | Cache on | Change |
| --- | ---: | ---: | --- |
| p50 | 188.292 ms | 190.551 ms | 1.20% worse |
| p95 | 388.325 ms | 306.694 ms | 21.02% lower |
| p99 | 496.720 ms | 407.436 ms | 17.97% lower |
| Throughput | 114.531 requests/s | 111.915 requests/s | 2.28% lower |

The apparent cache tail improvement is **inconclusive**: paired trials disagree,
and 96.49-98.21% of application CPU scheduling periods experienced throttling.
Redis recorded 19,946 hits and 329 misses (**98.38%**) over 20,275 cache-on reads,
but the deterministic selector reaches at most **280 keys** from a 1,000-ID
sample. Do not claim a measured Redis speedup or caching of all 100K claims.

The strongest optimization result is the bounded daily-report SQL index test:
average pgbench latency **58.302 -> 10.205 ms (82.50% lower)** and throughput
**68.608 -> 391.946 transactions/s (5.71x)**, with 4 clients, 2 threads, and one
30-second measurement per mode. EXPLAIN confirms a sequential-to-bitmap-index
scan change. The table then held 100,105 claims after the mixed run; the query
matched 959 rows. This is one selective SQL query, not a 5.71x HTTP API speedup.

Across the six detail-read trials and one mixed API trial, **42,958 measured
requests had zero unexpected HTTP statuses**. That is not a production reliability
guarantee or validation of all response-body semantics. See the final report for
per-trial values, mixed API results, formulas, and resume-safe wording.

```bash
# Recalculate the existing final results; requires the archived raw directory.
node benchmarks/analysis/summarize-phase16.mjs benchmarks/results/20260831T045941Z
# Start a NEW isolated benchmark only when desired (deletes its own prior stack/data).
bash benchmarks/scripts/run-benchmarks.sh
```

Raw run directories are ignored by Git; retain/archive the full evidence before
sharing performance claims. The checked-in harness defaults are not the final
run's overrides. [Benchmark documentation](benchmarks/README.md) records that
distinction and the workload limitations.

## Current boundaries

Production rollout still needs a security/dependency review, frontend hosting
and cross-origin policy, operational monitoring/recovery exercises, and stronger
concurrency/delivery guarantees where required. There is no optimistic version
column for general officer transitions; idempotency row locking should not be
described as preventing every concurrent-update race. Legacy notification routes
have weaker authorization than claims and are not a complete subscription system.
These are documented limitations, not capabilities added in Phase 17.
