#!/usr/bin/env bash
set -Eeuo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
benchmark_dir="$(cd -- "${script_dir}/.." && pwd)"
compose_file="${benchmark_dir}/docker-compose.benchmark.yml"
result_dir="${1:?Usage: query-index-comparison.sh RESULT_DIRECTORY}"
duration="${DB_BENCHMARK_DURATION:-30}"
clients="${DB_BENCHMARK_CLIENTS:-4}"
threads="${DB_BENCHMARK_THREADS:-2}"

if [[ "${ALLOW_BENCHMARK_INDEX_DDL:-}" != "true" ]]; then
  echo "Set ALLOW_BENCHMARK_INDEX_DDL=true; index DDL is restricted to claims_benchmark." >&2
  exit 1
fi
if ! [[ "$duration" =~ ^[1-9][0-9]*$ && "$clients" =~ ^[1-9][0-9]*$ && "$threads" =~ ^[1-9][0-9]*$ ]]; then
  echo "DB benchmark duration, clients, and threads must be positive integers." >&2
  exit 1
fi

mkdir -p "$result_dir"
compose=(docker compose -f "$compose_file")
database="$("${compose[@]}" exec -T postgres psql -U claims_app -d claims_benchmark -Atqc 'SELECT current_database()')"
if [[ "$database" != "claims_benchmark" ]]; then
  echo "Refusing index DDL against database: ${database}" >&2
  exit 1
fi

restore_index() {
  "${compose[@]}" exec -T postgres psql -U claims_app -d claims_benchmark -v ON_ERROR_STOP=1 \
    -c 'CREATE INDEX IF NOT EXISTS idx_claims_claim_date ON claims (claim_date);' >/dev/null || true
}
trap restore_index EXIT

explain_query() {
  "${compose[@]}" exec -T postgres psql -U claims_app -d claims_benchmark -X -q -t -A \
    -c "EXPLAIN (ANALYZE, BUFFERS, WAL, SETTINGS, FORMAT JSON)
        SELECT CAST(claim_date AS DATE) AS report_date,
               COUNT(*) AS total_claims,
               COALESCE(SUM(claim_amount), 0) AS total_amount,
               COALESCE(AVG(claim_amount), 0) AS average_amount
        FROM claims
        WHERE claim_date >= TIMESTAMP '2025-12-01 00:00:00'
          AND claim_date < TIMESTAMP '2025-12-08 00:00:00'
        GROUP BY CAST(claim_date AS DATE)
        ORDER BY CAST(claim_date AS DATE);"
}

run_pgbench() {
  local label="$1"
  "${compose[@]}" exec -T postgres pgbench -U claims_app -d claims_benchmark \
    -n -c "$clients" -j "$threads" -T "$duration" \
    -f /benchmark/db/daily-report.pgbench.sql \
    | tee "${result_dir}/${label}-pgbench.txt"
}

"${compose[@]}" exec -T postgres psql -U claims_app -d claims_benchmark -v ON_ERROR_STOP=1 \
  -c 'DROP INDEX IF EXISTS idx_claims_claim_date;' \
  -c 'ANALYZE claims;' >"${result_dir}/without-index-ddl.txt"
explain_query >"${result_dir}/without-index-explain.json"
"${compose[@]}" exec -T postgres pgbench -U claims_app -d claims_benchmark \
  -n -c 1 -j 1 -t 10 -f /benchmark/db/daily-report.pgbench.sql >/dev/null
run_pgbench without-index

"${compose[@]}" exec -T postgres psql -U claims_app -d claims_benchmark -v ON_ERROR_STOP=1 \
  -c '\timing on' \
  -c 'CREATE INDEX idx_claims_claim_date ON claims (claim_date);' \
  -c 'ANALYZE claims;' >"${result_dir}/with-index-ddl.txt"
explain_query >"${result_dir}/with-index-explain.json"
"${compose[@]}" exec -T postgres pgbench -U claims_app -d claims_benchmark \
  -n -c 1 -j 1 -t 10 -f /benchmark/db/daily-report.pgbench.sql >/dev/null
run_pgbench with-index

"${compose[@]}" exec -T postgres psql -U claims_app -d claims_benchmark -X -P pager=off \
  -c "SELECT indexname, indexdef FROM pg_indexes WHERE tablename = 'claims' ORDER BY indexname;" \
  >"${result_dir}/claims-indexes-final.txt"

trap - EXIT
echo "Database index comparison saved in ${result_dir}."
