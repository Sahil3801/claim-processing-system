# Railway demo deployment

A low-cost public demo: one URL serves the React app and proxies `/api` to the
Spring Boot API over Railway's private network. PostgreSQL and Redis are Railway
databases. **Kafka is not deployed**: status events are still written to the
transactional outbox table, but the relay and email consumer are off. Use the
[AWS runbook](../aws/README.md) or local Docker stack to demonstrate Kafka.

```text
browser --HTTPS--> frontend (Caddy: React build + /api proxy)
                        |  private network
                        v
                   backend (Spring Boot, profiles production,railway)
                     /            \
               Postgres          Redis
```

Railway is not free: new accounts get a one-time trial credit, then the Hobby
plan applies. Check current pricing, and set a usage limit in the workspace's
billing settings before deploying.

## 1. Create the project

1. Sign in to Railway with GitHub and create an empty project.
2. **+ Create → Database → PostgreSQL**. Keep the service name `Postgres`.
3. **+ Create → Database → Redis**. Keep the service name `Redis`.

## 2. Backend service

1. **+ Create → GitHub Repo →** this repository. Rename the service to `backend`
   (the frontend refers to it by this name).
2. Settings: leave **Root Directory** empty. Railway picks up `railway.json`
   (Dockerfile build, `/actuator/health` health check). Do **not** generate a
   public domain; only the frontend should be public.
3. Variables (use **Raw Editor**; `${{...}}` are Railway references):

   ```text
   SPRING_PROFILES_ACTIVE=production,railway
   PORT=8080
   DB_HOST=${{Postgres.PGHOST}}
   DB_PORT=${{Postgres.PGPORT}}
   DB_NAME=${{Postgres.PGDATABASE}}
   DB_USERNAME=${{Postgres.PGUSER}}
   DB_PASSWORD=${{Postgres.PGPASSWORD}}
   REDIS_HOST=${{Redis.REDISHOST}}
   REDIS_PORT=${{Redis.REDISPORT}}
   REDIS_PASSWORD=${{Redis.REDISPASSWORD}}
   JWT_SECRET=<output of: openssl rand -base64 48>
   JAVA_TOOL_OPTIONS=-Xmx384m -XX:+UseSerialGC
   ```

   To save more, skip the Redis service and set `CLAIMS_CACHE_ENABLED=false`
   instead of the three `REDIS_*` lines. Do not leave the cache enabled without
   Redis: every read would wait for a connection timeout.
4. Deploy. The log should show Flyway reaching `v8` and `Started ProcessingApplication`.

## 3. Frontend service

1. **+ Create → GitHub Repo →** the same repository. Rename it `frontend`.
2. Settings: **Root Directory** `/frontend`, so Railway uses `frontend/railway.json`
   and `frontend/Dockerfile`.
3. Variables:

   ```text
   BACKEND_URL=http://${{backend.RAILWAY_PRIVATE_DOMAIN}}:8080
   ```
4. **Settings → Networking → Generate Domain**. This is the demo URL.

## 4. Verify

```bash
curl -s https://<your-domain>/api/auth/login -H 'Content-Type: application/json' \
  -d '{"username":"nobody","password":"wrong"}' -o /dev/null -w '%{http_code}\n'   # 401
```

Then open the URL, register a claimant, create a claim, and submit it.

## 5. Officer and admin demo accounts

Public registration only creates claimants. Register two more users in the UI,
then promote them from the Postgres service's **Data → Query** tab:

```sql
UPDATE users SET role = 'CLAIMS_OFFICER' WHERE username = 'demo-officer';
UPDATE users SET role = 'ADMIN' WHERE username = 'demo-admin';
```

Use demo-only passwords. Anyone with the URL can register as a claimant.

## Notes

- Pushes to `main` redeploy both services. Backend redeploys run Flyway
  migrations on startup; migrations must stay backward compatible.
- The outbox grows by one row per status change while the relay is off, which is
  harmless at demo volume. To enable Kafka later, add a broker, set
  `KAFKA_BOOTSTRAP_SERVERS`, and drop `railway` from `SPRING_PROFILES_ACTIVE`
  (or override `CLAIMS_OUTBOX_RELAY_ENABLED=true`).
- Locally verified with the same profiles and variables, no Kafka running,
  Postgres/Redis containers, and the Caddy image in front: health `UP`, SPA deep
  links served, and register → login → create → submit through `/api`.
