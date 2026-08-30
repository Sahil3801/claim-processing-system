#!/usr/bin/env bash
set -Eeuo pipefail

result_dir="${1:?Usage: summarize-results.sh RESULT_DIRECTORY}"
if ! command -v jq >/dev/null 2>&1; then
  echo "jq is required." >&2
  exit 1
fi

output_file="${result_dir}/api-comparison.csv"
echo 'label,p50_ms,p95_ms,p99_ms,throughput_requests_per_second,error_rate,total_requests' >"$output_file"

shopt -s nullglob
summary_files=("${result_dir}"/cache-*-r*-summary.json "${result_dir}"/api-mix-cache-on-summary.json)
if (( ${#summary_files[@]} == 0 )); then
  echo "No measured k6 summary files found in ${result_dir}." >&2
  exit 1
fi

for summary_file in "${summary_files[@]}"; do
  label="$(jq -r '.label' "$summary_file")"
  if [[ "$label" == *-warmup ]]; then
    continue
  fi
  if [[ "$label" == api-mix-* ]]; then
    latency_metric=api_latency
    request_metric=api_requests
    error_metric=api_errors
  else
    latency_metric=claim_detail_latency
    request_metric=claim_detail_requests
    error_metric=claim_detail_errors
  fi
  jq -r \
    --arg label "$label" \
    --arg latency "$latency_metric" \
    --arg requests "$request_metric" \
    --arg errors "$error_metric" \
    '[
      $label,
      .metrics[$latency].values["p(50)"],
      .metrics[$latency].values["p(95)"],
      .metrics[$latency].values["p(99)"],
      .metrics[$requests].values.rate,
      .metrics[$errors].values.rate,
      .metrics[$requests].values.count
    ] | @csv' "$summary_file" >>"$output_file"
done

echo "API comparison saved to ${output_file}."
