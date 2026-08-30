#!/usr/bin/env bash
set -Eeuo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
aws_dir="$(cd -- "${script_dir}/.." && pwd)"
repo_dir="$(cd -- "${aws_dir}/../.." && pwd)"
compose_file="${aws_dir}/docker-compose.ec2.yml"
environment_file="${aws_dir}/.env.production"
services=(redis kafka app proxy)
deadline=$((SECONDS + 240))

cd "$repo_dir"
compose=(docker compose --env-file "$environment_file" -f "$compose_file")

while (( SECONDS < deadline )); do
  all_healthy=true
  for service in "${services[@]}"; do
    container_id="$("${compose[@]}" ps -q "$service")"
    if [[ -z "$container_id" ]]; then
      all_healthy=false
      continue
    fi
    health="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$container_id")"
    if [[ "$health" != "healthy" ]]; then
      all_healthy=false
    fi
  done

  if [[ "$all_healthy" == "true" ]]; then
    "${compose[@]}" exec -T app sh -c \
      "wget -q -O - http://localhost:8080/actuator/health | grep -q '\"status\":\"UP\"'"
    "${compose[@]}" ps
    echo "All production containers are healthy."
    exit 0
  fi
  sleep 5
done

"${compose[@]}" ps >&2
"${compose[@]}" logs --tail=100 app redis kafka proxy >&2
echo "Production stack did not become healthy within 240 seconds." >&2
exit 1
