#!/usr/bin/env bash
set -Eeuo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
aws_dir="$(cd -- "${script_dir}/.." && pwd)"
repo_dir="$(cd -- "${aws_dir}/../.." && pwd)"
compose_file="${aws_dir}/docker-compose.ec2.yml"
environment_file="${aws_dir}/.env.production"
certificate_file="${aws_dir}/certs/rds-global-bundle.pem"

if [[ ! -f "$environment_file" ]]; then
  echo "Missing ${environment_file}. Copy production.env.example and replace its placeholders." >&2
  exit 1
fi

if grep -Eq 'replace-with|xxxxxxxxxxxx|example\.com' "$environment_file"; then
  echo "The production environment file still contains example values." >&2
  exit 1
fi

environment_mode="$(stat -c '%a' "$environment_file")"
if [[ "$environment_mode" != "600" ]]; then
  echo "${environment_file} must have mode 600; current mode is ${environment_mode}." >&2
  exit 1
fi

mkdir -p "$(dirname "$certificate_file")"
certificate_temp="$(mktemp "${certificate_file}.XXXXXX")"
trap 'rm -f "$certificate_temp"' EXIT
curl -fsSL https://truststore.pki.rds.amazonaws.com/global/global-bundle.pem -o "$certificate_temp"
openssl crl2pkcs7 -nocrl -certfile "$certificate_temp" 2>/dev/null \
  | openssl pkcs7 -print_certs -noout >/dev/null
chmod 0644 "$certificate_temp"
mv -f "$certificate_temp" "$certificate_file"

cd "$repo_dir"
compose=(docker compose --env-file "$environment_file" -f "$compose_file")
"${compose[@]}" config --quiet
"${compose[@]}" build --pull app
"${compose[@]}" up --detach --remove-orphans
bash "${script_dir}/health-check.sh"

echo "Deployment completed. No images were published and no AWS resources were changed."
