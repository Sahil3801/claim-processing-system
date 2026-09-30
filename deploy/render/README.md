# Free demo deployment: Render + Neon + Vercel

A zero-cost public demo. Vercel serves the React app and forwards `/api` to the
Spring Boot API on Render, so the browser sees one origin (no CORS). Neon hosts
PostgreSQL. **Kafka and Redis are not deployed**: status events are still written
to the transactional outbox table, and reads go straight to PostgreSQL. Use the
local Docker stack to demonstrate Kafka and caching.

```text
browser --HTTPS--> Vercel (React build, /api rewrite) --HTTPS--> Render (Spring Boot, production,demo)
                                                                          |
                                                                  Neon PostgreSQL
```

Free-tier limits change; check each provider's current terms. Known trade-offs:
the Render free instance sleeps after about 15 idle minutes and has a small CPU
share, so a cold start can take a minute or more (step 5 avoids this).

## 1. Neon database

1. Create a Neon project in AWS `us-east-2` (Ohio), next to the Render region set
   in `render.yaml`. If you pick another Neon region, change `region` there too.
2. Open **Connect**, turn **Connection pooling off** (Flyway and Hikari want a
   direct connection), and copy the connection string:
   `postgresql://USER:PASSWORD@HOST/DBNAME?sslmode=require&channel_binding=require`
3. Rewrite it for Java. Keep only `sslmode=require`:

   ```text
   DB_JDBC_URL=jdbc:postgresql://HOST/DBNAME?sslmode=require
   DB_USERNAME=USER
   DB_PASSWORD=PASSWORD
   ```

## 2. Render API

1. Sign in to Render with GitHub. **New → Blueprint**, select this repository.
   Render reads `render.yaml` and proposes the free `claims-api` web service.
2. When prompted, paste `DB_JDBC_URL`, `DB_USERNAME`, and `DB_PASSWORD`.
   `JWT_SECRET` is generated for you; the profile and JVM settings are preset.
3. Deploy. The log should show Flyway reaching `v8` and `Started ProcessingApplication`.
4. Note the service URL, for example `https://claims-api.onrender.com`. If the
   name was taken, Render adds a suffix: use the real URL in step 3.
5. Check it: `https://<render-url>/actuator/health` returns `{"status":"UP",...}`.

## 3. Vercel frontend

1. If your Render URL is not `https://claims-api.onrender.com`, edit
   `frontend/vercel.json` and commit. Vercel rewrites cannot read environment
   variables, so the URL lives in that file.
2. In Vercel: **Add New → Project**, import this repository, set **Root Directory**
   to `frontend`. Vercel detects Vite (`npm run build`, output `dist`). No
   environment variables are needed; the app calls `/api` on its own origin.
3. Deploy. The Vercel URL is the demo link.

## 4. Demo accounts

Public registration only creates claimants. Register an officer and an admin
in the UI, then promote them in Neon's **SQL Editor**:

```sql
UPDATE users SET role = 'CLAIMS_OFFICER' WHERE username = 'demo-officer';
UPDATE users SET role = 'ADMIN' WHERE username = 'demo-admin';
```

Use demo-only passwords. Anyone with the link can register as a claimant.

## 5. Avoid cold starts

A sleeping Render instance can take longer to wake than Vercel waits for a
proxied response, so the first click after a quiet period may fail. Add a free
uptime monitor (for example UptimeRobot or cron-job.org) that requests
`https://<render-url>/actuator/health` every 10 minutes. One always-awake
service stays within Render's monthly free instance hours; confirm the current
allowance in Render's docs.

## Notes

- Pushes to `main` redeploy the API when backend files change (`buildFilter` in
  `render.yaml`) and the frontend on every push. API redeploys run Flyway
  migrations on startup, so migrations must stay backward compatible.
- The outbox gains one row per status change while the relay is off, which is
  harmless at demo volume.
- To enable caching later, add a Redis-compatible instance, set `REDIS_HOST`,
  `REDIS_PORT`, `REDIS_PASSWORD`, and `CLAIMS_CACHE_ENABLED=true`.
- Locally verified in a 512 MB container with `PORT=10000`, the same JVM flags,
  the `production,demo` profiles, PostgreSQL only (no Redis or Kafka): started
  in about 10 s using about 230 MB, health `UP`, and register → login → create →
  submit → read all succeeded. Render's free CPU share is smaller than the test
  machine's, so expect slower startup there.
