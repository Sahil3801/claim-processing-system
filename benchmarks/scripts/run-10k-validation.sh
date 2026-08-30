#!/usr/bin/env bash
set -Eeuo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
benchmark_dir="$(cd -- "${script_dir}/.." && pwd)"

set -a
. "${benchmark_dir}/profiles/10k-validation.env"
set +a

export BENCHMARK_RUN_ID="${BENCHMARK_RUN_ID:-$(date -u +%Y%m%dT%H%M%SZ)-10k-validation}"
exec bash "${script_dir}/run-benchmarks.sh"
