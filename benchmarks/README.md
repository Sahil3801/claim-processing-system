# Claims Processing System performance benchmarks

This is a local, repeatable k6 and PostgreSQL benchmark harness. It creates an
isolated `claims_benchmark` database, deterministic synthetic claims, a local
Redis cache, single-node Kafka, one application container, and a k6 load
generator. It does not connect to or modify the normal development or
production database.

Measured results are documented in the
[final Phase 16 report](analysis/20260831T045941Z.md), with the earlier
[10K diagnosis](analysis/20260830T191258Z.md) retained for context. Raw result
directories remain ignored by Git and must be archived separately. Results
depend on the host, Docker runtime, resource contention, dataset size, and
selected load. These are local synthetic tests, not deployed AWS measurements.

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
- an intended 80/20 hot/cold pattern over selected claim IDs; the current
  selector has the working-set limitation documented below
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

Its checked-in inputs are in `profiles/10k-validation.env`. It remains a smaller
validation option before new large runs. The completed final 100K run is
`20260831T045941Z`; its measured parameters, not the harness defaults, govern
interpretation of that result.

### Completed final run and its overrides

Run `20260831T045941Z` used 100,000 seeded claims, 1,000 claimants, **20 cache VUs
and 20 mixed VUs**, three 60-second cache trials per mode, 15-second cache
warmups, and one 60-second mixed trial. PostgreSQL used tmpfs on local
Docker Desktop/WSL2, and the application was capped at one CPU. The final report
records the exact commit, host, images, CPU counters, and formulas.

To start a new experiment with those recorded workload settings (not reproduce
identical timings or overwrite the original run):

```bash
CLAIM_COUNT=100000 CLAIMANT_COUNT=1000 \
CACHE_BENCHMARK_VUS=20 MIX_BENCHMARK_VUS=20 \
READ_DURATION=60s MIX_DURATION=60s WARMUP_DURATION=15s \
BENCHMARK_REPETITIONS=3 HOTSET_PERCENT=10 \
DB_BENCHMARK_DURATION=30 DB_BENCHMARK_CLIENTS=4 DB_BENCHMARK_THREADS=2 \
bash benchmarks/scripts/run-benchmarks.sh
```

The recorded cache median p95/p99 reductions were 21.02%/17.97%, but p50 worsened
1.20% and throughput fell 2.28%; paired trials and CPU throttling make a Redis
speedup inconclusive. The selective date-index SQL test showed 82.50% lower
average pgbench latency and 5.71x throughput in one local before/after pair.
See the full report before using any metric, and do not extrapolate to cloud
storage, all API endpoints, or production traffic.

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

The runner samples at most 1,000 claimant-owned IDs, not every seeded claim.
With 1,000 IDs and the default 10% hotset, the same selector drives both branch
and index choice, limiting reachable detail IDs to 280. The final run recorded
278-280 Redis keys and a 98.38% aggregate warmed hit rate. This is a small
working-set cache test against a 100K database, not a 100K-key cache workload. Normalized
raw URL tags do not permit reconstruction of per-ID access frequencies. The
limitation is documented; the completed workload was not changed retroactively.

### Key API mix

`k6/api-mix.js` uses intended deterministic iteration branches (actual request
shares differ, especially because create/submit makes two requests):

- 45% claim detail reads
- 20% claimant pagination
- 15% officer/admin status-and-type filtering
- 15% summary, status, type, and daily reports
- 5% claim creation followed by submission

Creation and submission use unique idempotency keys. Custom workload metrics
exclude setup/login traffic; general `http_*` metrics include setup. Cache warmup
summaries are labeled separately. The mixed API run starts with a fresh cache
and has no equivalent workload warmup. It retains database-backed authentication.
Its Kafka producer remains enabled,
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

## Verify the saved final measurements without rerunning load

With the full raw directory restored locally and Node.js available:

```bash
node benchmarks/analysis/summarize-phase16.mjs benchmarks/results/20260831T045941Z
```

The read-only analyzer verifies all seven measured gzip streams against summary
request counts/errors/percentiles and CSV values, then emits full-precision
derived metrics. It does not mutate the application or results. The
[final report](analysis/20260831T045941Z.md) separates resume-safe SQL/verification
metrics from noisy cache comparisons. Return to the [project README](../README.md)
for implementation and deployment boundaries.
