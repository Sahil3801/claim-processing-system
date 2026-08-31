# Architecture and implementation boundaries

This guide describes the current source, not a future target architecture.
See the [README](../README.md) for setup/API examples and the
[Phase 16 report](../benchmarks/analysis/20260831T045941Z.md) for measured evidence.
The prepared AWS topology is not evidence of an actual cloud deployment.

## Components and responsibilities

| Component | Responsibility | Source |
| --- | --- | --- |
| React/TypeScript client | Login/register, claimant/officer screens, admin reports, loading/errors and navigation | [frontend/src](../frontend/src) |
| Security filter chain | Verify JWT; load active user and current role; gate routes | [SecurityConfig](../src/main/java/com/claim/demo/config/SecurityConfig.java), [JwtAuthenticationFilter](../src/main/java/com/claim/demo/filter/JwtAuthenticationFilter.java) |
| Controllers/DTOs | JSON boundaries, Bean Validation, pagination, error mapping | [controller](../src/main/java/com/claim/demo/controller), [dto](../src/main/java/com/claim/demo/dto) |
| Claim service/domain | Ownership, idempotent create/submit, lifecycle, history, after-commit callbacks | [ClaimService](../src/main/java/com/claim/demo/service/ClaimService.java), [ClaimStatus](../src/main/java/com/claim/demo/domain/ClaimStatus.java) |
| JPA/Flyway/PostgreSQL | Authoritative records, constraints, row locks, native aggregate projections | [ClaimRepository](../src/main/java/com/claim/demo/repository/ClaimRepository.java), [migrations](../src/main/resources/db/migration) |
| Redis facade | Best-effort detail/status cache, TTLs, deferred eviction | [ClaimCacheService](../src/main/java/com/claim/demo/service/ClaimCacheService.java) |
| Kafka producer/consumer | Post-commit status notification, retries/DLT, processed-event markers | [publisher](../src/main/java/com/claim/demo/service/ClaimStatusEventPublisher.java), [consumer](../src/main/java/com/claim/demo/service/ClaimStatusEventConsumer.java) |
| Report service | Live overall/status/type/date summaries, DTO mapping | [ReportService](../src/main/java/com/claim/demo/service/ReportService.java) |
| Hourly batch | Move submitted claims older than 24 hours to review as SYSTEM_BATCH | [ClaimBatchService](../src/main/java/com/claim/demo/service/ClaimBatchService.java) |

The backend is a layered application, not microservices. Cache, notification,
and reporting code share its process, while Redis, Kafka, and PostgreSQL are
separate infrastructure processes. PostgreSQL is the only source of truth for
claim state. Claim reporting does not consume Kafka to build read models.

## Data model

```text
users 1 ------ * claims 1 ------ * claim_status_history
  |
  +---------- * notifications                 (legacy storage)

processed_kafka_events                        (event_id primary key)
claim_reports / claims_summaries               (legacy report storage)
```

`claims.user_id` and `claim_status_history.claim_id` are foreign keys. The
processed-event table records a claim ID but does not declare a claim foreign
key. A claim has `NUMERIC(19,2)` amount, string type/description, enum-as-string
status, timestamps, and separate nullable unique creation/submission keys.
History stores old/new status, actor, reason, and a time-zone-aware timestamp.
The actor is a string, allowing the `SYSTEM_BATCH` identity; it is not a user FK.

Flyway V1-V6 define/extend the schema. Hibernate validates mappings rather than
owning DDL. Positive amount, allowed statuses/roles, identity/foreign keys, and
unique keys are database-enforced. The allowed transition **edges**, rejection
reason requirement, and ownership are application/API rules, not SQL triggers.
There is no `@Version` field on a claim. Financial values have no currency code.

The original reporting tables and their repositories remain, but current
`/api/reports/**` endpoints execute aggregate queries over `claims`. V6 indexes
`claim_date` for bounded date selection. Existing user/status/type/history
indexes serve other lookups; the benchmark only isolates the V6 date index.
Case-insensitive filtering applies `lower(claimType)`; the plain claim-type
index is not proof every filtered query uses an index.

## Write transaction and lifecycle

```text
request -> authentication / route role -> DTO validation
                -> service ownership + lifecycle checks
                -> PostgreSQL transaction:
                     claim update + history insert
                -> COMMIT
                     |-- evict detail/status Redis keys
                     +-- enqueue Kafka send keyed by claim ID
```

Creation saves `DRAFT`. `DRAFT -> SUBMITTED -> UNDER_REVIEW -> APPROVED -> SETTLED`
is the success path; `UNDER_REVIEW -> REJECTED` is the other terminal path.
There is no direct submitted-to-approved transition, reopen operation, or
payment integration. Every actual transition records history; a create or valid
idempotent retry does not add a transition entry. Domain validation rejects
repeated officer actions rather than treating them as idempotent requests.

Claim and history changes share one Spring transaction. Redis eviction and
Kafka publication register `TransactionSynchronization.afterCommit` callbacks;
rollback does neither. Callbacks catch cache/publication exceptions so those
failures do not undo a committed claim. A broker send can still spend time
obtaining metadata/buffer space before returning its future: "asynchronous"
does not imply zero response-path overhead. The hourly batch goes through the
same transition service with its system actor.

### Idempotency boundary

Creation accepts a required header, trims it, looks up an existing creation key,
compares the owner/amount/type/description/email, then locks the owner row and
rechecks before inserting. Amount equality uses `BigDecimal.compareTo`; text
fields use exact equality. Different descriptions/case/whitespace can therefore
be conflicting request content, even if they appear similar to a human.

Submission locks the claim row, verifies ownership, checks its retained
submission key, and either returns the current claim or performs the transition.
Creation and submission use **different keys**; the creation key survives later
transitions. Each column has a unique database constraint, and the service
checks reuse across operations. Keys have no expiry/cleanup mechanism and are
not separate user-scoped key records. A valid retry returns the current DTO,
not a stored original HTTP payload; creation returns 201 on retries too.

Limitations:

- A new key is a new operation; there is no business-content duplicate detector.
- Owner-row locking serializes creation for the same owner, a deliberate
  correctness/concurrency tradeoff.
- Cross-operation checks span two columns without a combined database-wide
  uniqueness constraint. Do not promise that simultaneous creation/submission
  on different locked rows cannot race on key reuse.
- Review/approve/reject/settle read without an explicit row lock or optimistic
  version check. Their legal-edge checks do not prevent every lost update or
  conflicting concurrent decision. Submission locks do not solve that gap.

## Read and authorization flow

```text
JWT signature + issuer + expiry verification
  -> database user lookup (current role, active status)
  -> route authorization
  -> detail cache hit OR PostgreSQL read + Redis population
  -> claimant ownership check on returned DTO
  -> response
```

Officers/admins can view any claim; claimants can view/create/submit only their
own. Claimant detail reads additionally resolve the actor's user ID, including
when the DTO came from Redis. Cache keys are not an authorization boundary.
Public registration always sets `CLAIMANT`; provisioning staff is an operator
action. The JWT role claim helps client navigation but is not the backend's
authority source. Deactivation/role changes affect subsequent backend requests
without waiting for token expiry; the client may keep an outdated role display
until login/refresh of its local session.

Passwords are BCrypt-hashed. JWT uses HS256 with an environment-provided secret
of at least 32 characters, issuer check, and default 15-minute expiry. Logout
removes browser storage, not an issued token from the server. No refresh-token
rotation, MFA, API rate limiting, or token revocation list is implemented.
The client stores JWTs in localStorage, which is exposed to injected scripts.
CSRF is disabled for the stateless bearer flow; that choice must be revisited
if authentication moves to automatically sent cookies. Backend CORS is not
configured; local development uses the Vite proxy.

Error DTOs are shared between controller advice and security handlers. Login's
catch-all credential failure returns an empty 401, and there is no general
catch-all advice promising that every unexpected exception follows the DTO.
The frontend handles an empty unauthorized response as well as structured errors.

## Cache consistency and failure behavior

`claims:detail:{id}` holds a `ClaimDTO` serialized with
`GenericJackson2JsonRedisSerializer`; `claims:status:{id}` holds a status string.
Defaults are 10-minute/5-minute TTLs, respectively. Cache misses read PostgreSQL
and set the value. There is no negative cache, cache stampede lock, distributed
version token, or list/report caching.

Eviction happens after commit for both keys. A rollback leaves the previous
valid cached state intact. Redis exceptions are swallowed/logged at the facade,
allowing database reads and successful writes to proceed; connection/command
timeouts default to two seconds and can still delay a degraded request.

After-commit invalidation avoids exposing rolled-back mutations but is not
strict consistency. A reader can fetch an older database value, an updater can
commit/evict, and the reader can then refill the stale value. Failed eviction
also leaves cached data until expiry. These windows remain in the current
implementation. Read/write correctness tests should not be described as proof
against every distributed race. The production profile excludes Redis from
health to avoid treating an optional cache as readiness-critical.

## Kafka delivery semantics

The status event includes a UUID event ID, claim ID, previous/new status, actor,
user ID/email, and occurrence time. `claims.status.v1` is the default topic;
`claims.status.v1.dlt` is its dead-letter topic. Both topic names are configurable.
The claim ID is the record key for partition routing. Kafka partition order does
not independently enforce database transition order under concurrent updates.

The producer enables idempotence, all-replica acknowledgments, 10 retries, and
a 120-second delivery timeout. Default provisioning uses one partition and one
replica. With a single broker, "acks=all" is not multi-node durability.

The `claims-notifications-v1` group consumes with auto-commit disabled and record
acknowledgment. Retryable listener failures get two retries, one second apart,
then dead-letter recovery; nonretryable failures may bypass retry. The DLT uses
the original partition, so changing topic partition counts requires matching
DLT capacity. No automatic DLT replay tool is implemented.

The transactional consumer checks `processed_kafka_events`, flushes a unique
event-ID marker, and calls SMTP. Concurrent duplicates compete on the unique
primary key; already committed markers cause an early return. A send failure
rolls back the marker so redelivery can retry. This protects completed work
against ordinary duplicate deliveries, not all external-side-effect failures.

| Failure point | Current behavior / limit |
| --- | --- |
| Claim transaction rolls back | No status event is published by its callback |
| Process dies after DB commit, before Kafka send | Event can be lost; no durable outbox/reconciler |
| Producer exhausts delivery attempts | Logs failure; committed claim remains successful |
| SMTP throws | Consumer transaction rolls back; listener retry/DLT applies |
| SMTP succeeds, process dies before marker commit | Redelivery may send duplicate email |
| Committed event ID is redelivered | Consumer skips its side effect |
| Kafka host/volume is lost | Single-node deployment cannot guarantee event recovery |

Thus the system is not end-to-end exactly-once and not guaranteed at-least-once
from PostgreSQL commit to email. A future outbox could close the first gap;
provider-side idempotency/durable notification delivery would address the second.
Neither is implemented here. Event-marker cleanup/retention is also not automated.

### Legacy notification boundary

`POST /notifications/subscribe` and `/notifications/unsubscribe` still exist
outside `/api`. They accept user IDs (subscribe also accepts a message), require
authentication through the fallback rule, and emit to `user-notifications` /
`unsubscribe-notifications`. They do **not** apply the claims ownership/role
policy, persist a complete subscription preference, or have matching consumers
and topic provisioning in the new status flow. Status emails do not consult
these routes. Treat them as legacy functionality requiring review, not a secure,
finished notification-subscription feature.

## Reporting

Native projection queries compute count/sum/average by status, by type, overall,
and by creation date. Overall pending includes draft/submitted/under-review;
approved, rejected, settled are current-status buckets, not historical totals
of every approval or financial disbursement. Empty overall values map to zero;
grouped reports contain only present groups/dates. The daily endpoint converts
inclusive `from`/`to` dates to a half-open timestamp interval, keeping its WHERE
predicate on the indexed `claim_date` column. It is not a transition-date report.

The Phase 16 index evidence supports this selective date query only: 82.50%
lower average pgbench latency and 5.71x transactions/s in the recorded local
experiment. It does not show all reporting HTTP requests became that much faster.

## Runtime and deployment topology

- Local Compose: app + PostgreSQL + Redis + Kafka. Only the app port is published;
  PostgreSQL has a named volume. Redis is disposable; the local Kafka service
  does not declare a durable data volume. The frontend runs separately in Vite.
- Prepared AWS Compose: Caddy + app + Redis + Kafka on one EC2 host, private RDS
  outside Docker, persistent `kafka-data`/Caddy volumes. Only Caddy publishes
  HTTP/HTTPS ports. RDS uses verified TLS and an operator-provisioned migration
  role, not the database master account.
- CI: Maven verification, frontend test/build, and Docker build/inspect. No image
  publishing, deployment job, paid managed cache/broker provisioning, or automated
  AWS resource creation is enabled.

Health endpoints expose status without details. The production profile adds
graceful shutdown, bounded database pool/Tomcat settings, and proxy-header
handling. A health check is not proof of mail delivery, business correctness,
or durable backups. Use the [AWS runbook](../deploy/aws/README.md) for manual
networking, secrets, backups, TLS, update/rollback, and host security steps.
Instance sizes there are initial planning choices, not measured AWS capacity.

## Verification and remaining boundaries

Unit/slice tests cover domain/service behavior, DTO validation, JWT/security,
idempotency, cache fallback/after-commit behavior, Kafka retries/configuration
and deduplication logic. Testcontainers exercises real PostgreSQL migrations,
constraints, aggregates, and secured lifecycle calls, with Redis/Kafka mocked.
Those five tests can skip without Docker. JaCoCo is generated on Maven verify;
the build has no coverage gate. Frontend tests do not constitute full browser E2E.

The final benchmark's cache tests use static principals and a small warmed ID
working set, while the mixed trial uses database authentication. Redis hit rate
and CPU pressure are measured; separate serialization/JVM/network overhead is
not. See the report rather than extrapolating local measurements to EC2/RDS.

Before any public rollout, review legacy endpoints/configuration/dependencies,
concurrent transition behavior, outbox/SMTP recovery, authentication storage and
rate limiting, CORS/frontend hosting, backup restoration, and observability.
These are explicit gaps, not Phase 17 implementation work or deployment claims.
