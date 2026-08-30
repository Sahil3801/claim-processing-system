# Docker Compose development stack

The Compose stack runs the API with PostgreSQL as the persistent source of truth,
Redis as an ephemeral bounded cache, and a single combined KRaft Kafka node.
Only the API port is published to the host.

## Start

PowerShell:

```powershell
Copy-Item .env.example .env
# Replace the example passwords and JWT secret in .env.
docker compose up --build -d
docker compose ps
```

The API is available at `http://localhost:8080`; its unauthenticated container
health endpoint is `http://localhost:8080/actuator/health`.

Follow startup logs with:

```powershell
docker compose logs -f app
```

Stop containers while retaining PostgreSQL data:

```powershell
docker compose down
```

To also remove the local PostgreSQL volume, explicitly run
`docker compose down --volumes`. This permanently removes local database data.

## Deployment notes

The resource limits and single Kafka broker are intentionally small for local and
low-cost environments. A production AWS deployment should inject secrets from its
runtime secret store and replace the single-node data services with appropriately
managed or failure-tolerant services when availability requirements justify them.
