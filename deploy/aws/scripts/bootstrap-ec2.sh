#!/usr/bin/env bash
set -Eeuo pipefail

if [[ ! -r /etc/os-release ]]; then
  echo "Cannot identify the operating system." >&2
  exit 1
fi

. /etc/os-release
if [[ "${ID:-}" != "ubuntu" ]]; then
  echo "This bootstrap script supports Ubuntu Server 24.04 LTS. Found: ${ID:-unknown}." >&2
  exit 1
fi

sudo apt-get update
sudo apt-get install -y ca-certificates curl git gnupg jq openssl
sudo install -m 0755 -d /etc/apt/keyrings

docker_key_temp="$(mktemp)"
trap 'rm -f "$docker_key_temp"' EXIT
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | gpg --dearmor >"$docker_key_temp"
sudo install -m 0644 "$docker_key_temp" /etc/apt/keyrings/docker.gpg

architecture="$(dpkg --print-architecture)"
codename="${VERSION_CODENAME:-noble}"
echo "deb [arch=${architecture} signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu ${codename} stable" \
  | sudo tee /etc/apt/sources.list.d/docker.list >/dev/null

sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

sudo install -d -m 0755 /etc/docker
if [[ ! -s /etc/docker/daemon.json ]]; then
  sudo tee /etc/docker/daemon.json >/dev/null <<'JSON'
{
  "live-restore": true,
  "log-driver": "json-file",
  "log-opts": {
    "max-size": "10m",
    "max-file": "3"
  }
}
JSON
else
  echo "Existing /etc/docker/daemon.json preserved; verify that Docker log rotation is configured." >&2
fi

sudo systemctl enable --now docker
sudo systemctl restart docker
sudo usermod -aG docker "$USER"

if [[ -z "$(swapon --show --noheadings)" && ! -e /swapfile ]]; then
  sudo fallocate -l 2G /swapfile
  sudo chmod 600 /swapfile
  sudo mkswap /swapfile >/dev/null
  sudo swapon /swapfile
  echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab >/dev/null
fi

sudo install -d -m 0750 -o "$USER" -g "$USER" /opt/claims-processing

docker --version
docker compose version
echo "Bootstrap complete. Sign out and reconnect before using Docker without sudo."
