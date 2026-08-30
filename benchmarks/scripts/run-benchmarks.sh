#!/usr/bin/env bash
set -Eeuo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
benchmark_dir="$(cd -- "${script_dir}/.." && pwd)"
repo_dir="$(cd -- "${benchmark_dir}/.." && pwd)"
compose_file="${benchmark_dir}/docker-compose.benchmark.yml"
run_id="${BENCHMARK_RUN_ID:-$(date -u +%Y%m%dT%H%M%SZ)}"
result_dir="${benchmark_dir}/results/${run_id}"
container_result_dir="/results/${run_id}"
claim_count="${CLAIM_COUNT:-100000}"
claimant_count="${CLAIMANT_COUNT:-1000}"
anchor_timestamp="${ANCHOR_TIMESTAMP:-2026-01-01 00:00:00}"
vus="${BENCHMARK_VUS:-20}"
read_duration="${READ_DURATION:-60s}"
mix_duration="${MIX_DURATION:-60s}"
warmup_duration="${WARMUP_DURATION:-15s}"
repetitions="${BENCHMARK_REPETITIONS:-3}"
benchmark_password="${BENCHMARK_PASSWORD:-benchmark-password}"
app_port="${BENCHMARK_APP_PORT:-18080}"

for command_name in docker curl jq git; do
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "Required command is unavailable: ${command_name}" >&2
    exit 1
  fi
done
if ! docker info >/dev/null 2>&1; then
  echo "Docker Engine is not available." >&2
  exit 1
fi
if ! [[ "$run_id" =~ ^[A-Za-z0-9._-]+$ ]]; then
  echo "BENCHMARK_RUN_ID may contain only letters, numbers, dots, underscores, and dashes." >&2
  exit 1
fi
if ! [[ "$claim_count" =~ ^[1-9][0-9]*$ && "$claimant_count" =~ ^[1-9][0-9]*$ && "$vus" =~ ^[1-9][0-9]*$ && "$repetitions" =~ ^[1-9][0-9]*$ ]]; then
  echo "CLAIM_COUNT, CLAIMANT_COUNT, BENCHMARK_VUS, and BENCHMARK_REPETITIONS are invalid." >&2
  exit 1
fi

mkdir -p "$result_dir"
compose=(docker compose -f "$compose_file")

capture_logs_and_clean() {
  local exit_code=$?
  "${compose[@]}" ps >"${result_dir}/compose-ps-final.txt" 2>&1 || true
  "${compose[@]}" logs --no-color >"${result_dir}/compose.log" 2>&1 || true
  if [[ "${KEEP_BENCHMARK_STACK:-false}" != "true" ]]; then
    "${compose[@]}" down --volumes --remove-orphans >/dev/null 2>&1 || true
  fi
  exit "$exit_code"
}
trap capture_logs_and_clean EXIT

wait_for_health() {
  local service="$1"
  local deadline=$((SECONDS + 300))
  while (( SECONDS < deadline )); do
    local container_id health
    container_id="$("${compose[@]}" ps -q "$service")"
    if [[ -n "$container_id" ]]; then
      health="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container_id")"
      if [[ "$health" == "healthy" ]]; then
        return 0
      fi
    fi
    sleep 5
  done
  echo "Service did not become healthy: ${service}" >&2
  return 1
}

write_metadata() {
  {
    echo "run_id=${run_id}"
    echo "started_utc=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    echo "git_commit=$(git -C "$repo_dir" rev-parse HEAD)"
    echo "claim_count=${claim_count}"
    echo "claimant_count=${claimant_count}"
    echo "anchor_timestamp=${anchor_timestamp}"
    echo "virtual_users=${vus}"
    echo "read_duration=${read_duration}"
    echo "mix_duration=${mix_duration}"
    echo "warmup_duration=${warmup_duration}"
    echo "cache_repetitions=${repetitions}"
    echo "database_benchmark_duration=${DB_BENCHMARK_DURATION:-30}"
    echo "database_benchmark_clients=${DB_BENCHMARK_CLIENTS:-4}"
    echo "database_benchmark_threads=${DB_BENCHMARK_THREADS:-2}"
  } >"${result_dir}/methodology.env"
  git -C "$repo_dir" status --short >"${result_dir}/git-status.txt"
  docker version >"${result_dir}/docker-version.txt"
  docker compose version >"${result_dir}/compose-version.txt"
  docker info >"${result_dir}/docker-info.txt"
  uname -a >"${result_dir}/host-uname.txt" 2>&1 || true
  command -v lscpu >/dev/null 2>&1 && lscpu >"${result_dir}/host-cpu.txt" || true
  command -v free >/dev/null 2>&1 && free -b >"${result_dir}/host-memory.txt" || true
}

register_user() {
  local username="$1"
  local payload
  payload="$(jq -nc \
    --arg username "$username" \
    --arg password "$benchmark_password" \
    --arg email "${username}@benchmark.invalid" \
    '{username: $username, password: $password, email: $email}')"
  curl --fail --silent --show-error \
    -H 'Content-Type: application/json' \
    -d "$payload" \
    "http://127.0.0.1:${app_port}/api/auth/register"
}

restart_app() {
  export CLAIMS_CACHE_ENABLED="$1"
  "${compose[@]}" up --detach --no-deps --force-recreate app >/dev/null
  wait_for_health app
}

run_k6() {
  local script="$1"
  local label="$2"
  local duration="$3"
  local raw_output="${4:-false}"
  local output_args=()
  if [[ "$raw_output" == "true" ]]; then
    output_args=(--out "json=${container_result_dir}/${label}-raw.json.gz")
  fi
  "${compose[@]}" --profile tools run --rm \
    -e BASE_URL=http://app:8080 \
    -e BENCHMARK_USERNAME=bench-admin \
    -e BENCHMARK_PASSWORD="$benchmark_password" \
    -e CLAIM_IDS_FILE="${container_result_dir}/claim-ids.json" \
    -e RESULT_DIR="$container_result_dir" \
    -e RUN_LABEL="$label" \
    -e VUS="$vus" \
    -e DURATION="$duration" \
    -e HOTSET_PERCENT="${HOTSET_PERCENT:-10}" \
    k6 run "${output_args[@]}" "/scripts/${script}"
}

capture_runtime_counters() {
  local label="$1"
  "${compose[@]}" exec -T redis sh -c \
    'REDISCLI_AUTH=benchmark-only-redis-password redis-cli INFO stats' \
    >"${result_dir}/${label}-redis-stats.txt"
  "${compose[@]}" exec -T postgres psql -U claims_app -d claims_benchmark -X -P pager=off \
    -c "SELECT datname, xact_commit, blks_read, blks_hit, tup_returned, tup_fetched
        FROM pg_stat_database WHERE datname = 'claims_benchmark';" \
    >"${result_dir}/${label}-postgres-stats.txt"
}

cd "$repo_dir"
write_metadata
"${compose[@]}" down --volumes --remove-orphans >/dev/null 2>&1 || true
"${compose[@]}" up --detach --build postgres redis kafka app
for service in postgres redis kafka app; do
  wait_for_health "$service"
done
"${compose[@]}" ps >"${result_dir}/compose-ps-start.txt"
"${compose[@]}" images >"${result_dir}/compose-images.txt"

register_user bench-claimant >"${result_dir}/bench-claimant.json"
register_user bench-admin >"${result_dir}/bench-admin.json"
"${compose[@]}" exec -T postgres psql -U claims_app -d claims_benchmark -v ON_ERROR_STOP=1 \
  -c "UPDATE users SET role = 'ADMIN' WHERE username = 'bench-admin';" \
  >"${result_dir}/promote-admin.txt"

"${compose[@]}" exec -T postgres psql -U claims_app -d claims_benchmark \
  -v ON_ERROR_STOP=1 \
  -v claim_count="$claim_count" \
  -v claimant_count="$claimant_count" \
  -v anchor_timestamp="$anchor_timestamp" \
  -f /benchmark/db/generate-synthetic-data.sql \
  >"${result_dir}/synthetic-data-load.txt"

"${compose[@]}" exec -T postgres psql -U claims_app -d claims_benchmark -X -q -t -A \
  -c "SELECT COALESCE(json_agg(claim_id ORDER BY claim_id)::text, '[]')
      FROM (SELECT claim_id FROM claims
            WHERE user_id = (SELECT user_id FROM users WHERE username = 'bench-claimant')
              AND idempotency_key LIKE 'bench-create-%'
            ORDER BY claim_id LIMIT 1000) AS owned;" \
  >"${result_dir}/claim-ids.json"
if [[ "$(jq 'length' "${result_dir}/claim-ids.json")" -lt 100 ]]; then
  echo "Synthetic dataset produced fewer than 100 benchmark-readable claims." >&2
  exit 1
fi

for (( repetition=1; repetition<=repetitions; repetition++ )); do
  if (( repetition % 2 == 1 )); then
    variants=(off on)
  else
    variants=(on off)
  fi
  for variant in "${variants[@]}"; do
    enabled=false
    [[ "$variant" == "on" ]] && enabled=true
    label="cache-${variant}-r${repetition}"
    restart_app "$enabled"
    "${compose[@]}" exec -T redis sh -c \
      'export REDISCLI_AUTH=benchmark-only-redis-password; redis-cli FLUSHALL >/dev/null && redis-cli CONFIG RESETSTAT >/dev/null'
    run_k6 claim-read.js "${label}-warmup" "$warmup_duration" false
    "${compose[@]}" exec -T redis sh -c \
      'REDISCLI_AUTH=benchmark-only-redis-password redis-cli CONFIG RESETSTAT >/dev/null'
    "${compose[@]}" exec -T postgres psql -U claims_app -d claims_benchmark -q \
      -c 'SELECT pg_stat_reset();' >/dev/null
    run_k6 claim-read.js "$label" "$read_duration" true
    capture_runtime_counters "$label"
  done
done

restart_app true
"${compose[@]}" exec -T redis sh -c \
  'export REDISCLI_AUTH=benchmark-only-redis-password; redis-cli FLUSHALL >/dev/null && redis-cli CONFIG RESETSTAT >/dev/null'
run_k6 api-mix.js api-mix-cache-on "$mix_duration" true
capture_runtime_counters api-mix-cache-on

ALLOW_BENCHMARK_INDEX_DDL=true \
  DB_BENCHMARK_DURATION="${DB_BENCHMARK_DURATION:-30}" \
  DB_BENCHMARK_CLIENTS="${DB_BENCHMARK_CLIENTS:-4}" \
  DB_BENCHMARK_THREADS="${DB_BENCHMARK_THREADS:-2}" \
  bash "${script_dir}/query-index-comparison.sh" "$result_dir"

bash "${script_dir}/summarize-results.sh" "$result_dir"

echo "completed_utc=$(date -u +%Y-%m-%dT%H:%M:%SZ)" >>"${result_dir}/methodology.env"
echo "Benchmark run completed: ${result_dir}"
