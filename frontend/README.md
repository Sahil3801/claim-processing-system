# Claims Processing Web

React and TypeScript client for the Claims Processing System API.

## Local development

```powershell
Copy-Item .env.example .env
npm install
npm run dev
```

The Vite development server runs at `http://localhost:5173` and proxies `/api`
to `http://localhost:8080` by default. Set `VITE_API_PROXY_TARGET` to use a
different backend during development.

## Verification

```powershell
npm test
npm run build
```

Set `VITE_API_BASE_URL` when the built frontend and API do not share an origin.
Only variables prefixed with `VITE_` are exposed to the browser; never put
credentials or JWT secrets in frontend environment files.
