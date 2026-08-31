# Docker Compose development stack

The Compose stack runs the API with PostgreSQL as the persistent source of truth,
Redis as an ephemeral bounded cache, and a single combined KRaft Kafka node.
Only the API port is published to the host.

See the [project README](README.md) for the frontend, API examples, and security
boundaries; [AWS preparation](deploy/aws/README.md) uses a separate Compose file
with external RDS, not this development database.

## Start

PowerShell:

```powershell
# First-time copy only; preserve any existing .env.
Copy-Item .env.example .env
# Replace the example passwords and JWT secret in .env.
# Set APP_BIND_ADDRESS=127.0.0.1 for a local-only API listener.
docker compose up --build -d
docker compose ps
```

The API is available at `http://localhost:8080`; its unauthenticated container
health endpoint is `http://localhost:8080/actuator/health`.

Compose's `.env` supplies substitutions, not an automatic pass-through of every
variable. Use service `environment` mappings/overrides for additional settings.
The frontend runs separately with Vite. PostgreSQL has a persistent named volume;
Redis is ephemeral, and the local Kafka service declares no durable data volume.
The AWS Compose file separately configures a Kafka volume.

The normal stack starts the status-notification consumer. Missing/invalid SMTP
credentials cause notification retries/DLT traffic, not rollback of successful
claims. Blank mail fields do not disable notification sending; when not testing
mail, pass `SPRING_KAFKA_LISTENER_AUTO_STARTUP=false` to the app through an explicit
Compose override. The benchmark stack already does this for workload isolation.

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
