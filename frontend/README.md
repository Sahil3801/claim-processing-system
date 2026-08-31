# Claims Processing Web

React and TypeScript client for the Claims Processing System API.

See the [project README](../README.md) for backend setup and the API/role matrix.

## Local development

```powershell
Copy-Item .env.example .env
npm ci
npm run dev
```

The Vite development server runs at `http://localhost:5173` and proxies `/api`
to `http://localhost:8080` by default. Set `VITE_API_PROXY_TARGET` to use a
different backend during development.

Run these commands from `frontend/`. Copy the environment file only for initial
setup; retain an existing customized file. Node.js 22 matches CI. The backend
and PostgreSQL must be available separately; the frontend is not part of the
Spring Boot Docker image or the EC2 Compose stack.

## Implemented screens and access

- Public login/register; registration always creates a claimant.
- Claimant dashboard, create draft, my claims, details and submission.
- Officer/admin dashboard and paginated/filterable work queue, review, approve,
  reject with a reason, and mark approved claims settled.
- Admin-only overall/status/type/daily reporting.
- Loading/error/retry states, role-based routes, and Axios bearer-token handling.

The session is stored in localStorage, cleared on expiry or protected-call 401,
and decoded client-side for navigation. Backend JWT verification and database
roles/ownership remain authoritative; no refresh-token flow is implemented.
The status timeline infers completed steps from the current status, created time,
and latest-update time. The backend does not expose historical transition
records to this client, so intermediate timestamps/actors/reasons are not shown.

There is no public role-management endpoint. A trusted database operator must
provision separate officer/admin accounts for a demonstration. UI type choices
do not imply the backend uses an enum for claim types. Backend rejection reasons
are limited to 500 characters, even though the current UI input allows more.

## Verification

```powershell
npm test
npm run build
```

Set `VITE_API_BASE_URL` when the built frontend and API do not share an origin.
Only variables prefixed with `VITE_` are exposed to the browser; never put
credentials or JWT secrets in frontend environment files.

Setting the base URL does not configure CORS: the backend currently has no
cross-origin policy. Prefer same-origin `/api` proxying or explicitly configure
and verify the intended origins before hosting separately. The Vite development
proxy is not included in `dist/`. Build output requires a static host with SPA
fallback routing; frontend hosting is preparation still to be completed, not
an existing AWS deployment. Vitest/Testing Library tests cover auth, route guards,
and API idempotency behavior, not a full deployed browser-to-database test.
