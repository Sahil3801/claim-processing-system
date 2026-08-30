# Claims Processing System performance benchmarks

This is a local, repeatable k6 and PostgreSQL benchmark harness. It creates an
isolated `claims_benchmark` database, deterministic synthetic claims, a local
Redis cache, single-node Kafka, one application container, and a k6 load
generator. It does not connect to or modify the normal development or
production database.

No benchmark number is checked into this repository. Run results depend on the
host, Docker runtime, resource contention, dataset size, and selected load. The
harness records those inputs and saves only measurements it actually observes.

## Prerequisites and safety

- Docker Engine with Docker Compose v2
- Bash, `curl`, `git`, and `jq`
- At least 4 GiB free memory and 2 GiB free disk for the default dataset/results
- An idle host with CPU power-saving and background workloads held as constant
  as practical

The runner executes `docker compose down --volumes` only against the explicitly
named `claims-benchmark` Compose project. PostgreSQL also checks that its
database name is exactly `claims_benchmark` before synthetic data or index DDL
runs. Never point this Compose file at production.

## Exact run

From the repository root on Linux, macOS, WSL, or Git Bash:

```bash
bash benchmarks/scripts/run-benchmarks.sh
```

The default run uses:

- 100,000 claims and 1,000 claimants anchored at `2026-01-01T00:00:00`
- two constant virtual users for cache-isolation trials and ten for the mixed API
  workload
- 15-second unmeasured warm-ups and 60-second measured API trials
- three cache-disabled and three cache-enabled repetitions, alternating order
- an 80/20 access pattern where 80% of detail reads target the hottest 10% of
  the selected claim IDs
- one 60-second realistic mixed-API run with caching enabled
- 30-second pgbench trials with four clients and two worker threads

Override inputs explicitly when needed:

```bash
CLAIM_COUNT=250000 \
CLAIMANT_COUNT=2500 \
CACHE_BENCHMARK_VUS=4 \
MIX_BENCHMARK_VUS=20 \
READ_DURATION=2m \
MIX_DURATION=2m \
WARMUP_DURATION=30s \
BENCHMARK_REPETITIONS=5 \
DB_BENCHMARK_DURATION=60 \
bash benchmarks/scripts/run-benchmarks.sh
```

`BENCHMARK_VUS` remains a compatibility override that sets both workloads. The
more explicit cache and mixed-workload variables are preferred because the two
tests answer different questions.

For the prepared 10K validation run, use exactly:

```bash
bash benchmarks/scripts/run-10k-validation.sh
```

Its checked-in inputs are in `profiles/10k-validation.env`. Complete and review
that run before starting the final 100K benchmark.

Use the same Git commit, clean/dirty state, Docker versions, host class, dataset
size, VUs, durations, and resource limits for comparisons. Do not compare runs
from materially different machines as if they were an application-only change.

## Synthetic dataset

`db/generate-synthetic-data.sql` uses `generate_series` and a fixed timestamp,
so the same parameters produce the same users, amounts, dates, types, statuses,
descriptions, and status histories. The distribution includes draft, submitted,
under-review, approved, rejected, and settled claims across medical, auto, home,
travel, and life types. Amounts and timestamps vary deterministically over two
years. The first bounded set belongs to the benchmark claimant; all other claims
are spread across synthetic claimants.

Two users are created through the real registration API. One is promoted to
`ADMIN` directly in the disposable database so authenticated claimant and
administrative APIs can both be exercised. Fixed credentials are benchmark-only
and must never be reused elsewhere.

## API methodology

### PostgreSQL reads versus Redis cache

`k6/claim-read.js` measures `GET /api/claims/{id}` using an administrator token,
which avoids an additional claimant-ownership database lookup and isolates the
cache-aside detail read. Production JWT authentication intentionally reloads the
user from PostgreSQL on every request. For this cache-only comparison, the
`benchmark` profile supplies two fixed principals so that unrelated user query
does not hide the claim-read difference. This fixture cannot activate outside
the benchmark profile. For every repetition the runner:

1. Recreates the application with `CLAIMS_CACHE_ENABLED=false` or `true`.
2. Flushes Redis and performs an unmeasured warm-up.
3. Resets Redis and PostgreSQL statistics without removing warmed cache keys.
4. Runs the measured constant-VU trial.
5. Saves k6 results, Redis hit/miss/memory/command counters,
   `pg_stat_database` counters, Docker resource usage, and application cgroup
   CPU/throttling counters.

Disabled mode bypasses Redis completely instead of treating a broken Redis
connection timeout as a database baseline. PostgreSQL remains the source of
truth in both modes. Trial order alternates to reduce simple first-run bias.
Compare all repetitions, not only the fastest result.

### Key API mix

`k6/api-mix.js` uses a deterministic request mix:

- 45% claim detail reads
- 20% claimant pagination
- 15% officer/admin status-and-type filtering
- 15% summary, status, type, and daily reports
- 5% claim creation followed by submission

Creation and submission use unique idempotency keys. Setup/login traffic and the
warm-up summaries are labeled separately from measured traffic. The mixed API
run retains database-backed authentication. Its Kafka producer remains enabled,
but the benchmark disables the notification consumer so SMTP failures and retry
logging do not contaminate API latency; Kafka consumer behavior remains covered
by the focused Phase 9 tests.

Each measured summary contains `p(50)`, `p(95)`, and `p(99)` latency in
milliseconds, request-counter rate in requests/second, error-rate ratio, and
request count. `api-comparison.csv` extracts those values. The gzip JSON files
contain every raw k6 metric point for independent analysis.

## Database index methodology

The query comparison targets the bounded daily aggregation used by
`GET /api/reports/daily`. In the disposable benchmark database only, the script:

1. Drops `idx_claims_claim_date` and analyzes the table.
2. Saves `EXPLAIN (ANALYZE, BUFFERS, WAL, SETTINGS, FORMAT JSON)` and a warmed
   pgbench measurement without the index.
3. Recreates the exact Flyway V6 index, analyzes the table, and repeats the same
   plan and pgbench workload.
4. Restores the index on failure through a shell trap and records the final
   index definitions.

This test changes no application migration and proves only the impact for the
recorded data distribution and date selectivity. Review plan node types, rows,
buffers, planning/execution time, pgbench latency, and transactions per second;
do not infer an improvement merely because an index exists.

## Result files

Every run is stored under `benchmarks/results/<UTC-run-id>/`:

- `*-raw.json.gz`: raw k6 time-series points
- `*-summary.json`: full k6 aggregate summaries
- `api-comparison.csv`: p50/p95/p99, throughput, errors, and counts per trial
- `*-redis-stats.txt` and `*-postgres-stats.txt`: cache/database counters
- `*-docker-stats.json` and `*-app-cpu-stat.txt`: resource and CPU-throttling
  evidence before and after each workload
- `without-index-*` and `with-index-*`: raw plans, DDL timing, and pgbench output
- `methodology.env`: workload parameters and Git commit
- Docker, Compose, CPU, memory, image, status, and application log records

Warm-up summaries are retained for diagnostics but must not be reported as
measured trials. If a run is interrupted, treat the directory as incomplete and
do not compare it with successful runs.

To retain containers for inspection, set `KEEP_BENCHMARK_STACK=true`; otherwise
the isolated stack and volumes are removed after results and logs are saved.
