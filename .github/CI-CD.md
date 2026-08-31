# CI/CD configuration

The `ci.yml` workflow runs on every push and pull request, with an optional
manual trigger. It verifies the Spring Boot backend, tests and builds the React
frontend, and builds the backend Docker image without publishing it.

This describes the checked-in workflow, not evidence of a completed GitHub run.
See the [project README](../README.md) and the
[manual AWS runbook](../deploy/aws/README.md) for runtime instructions.

Backend verification uses Java 17 and `./mvnw -B -ntp verify`; frontend uses
Node.js 22, `npm ci`, `npm test`, and `npm run build`. Docker build/inspect runs
as a separate job with `push: false`; it does not start the application through
Compose. Jobs have timeouts, dependency/build caching, and concurrency cancellation
for superseded runs. Testcontainers PostgreSQL tests can skip without a usable
Docker daemon, so a green Maven exit alone is not proof those integrations ran.

## Current credentials

CI uses only the automatically provided `GITHUB_TOKEN`, referenced through
`${{ secrets.GITHUB_TOKEN }}`. Its permission is restricted to read-only
repository contents, and checkout does not persist the credential.

Application database, Redis, JWT, mail, and Kafka credentials are runtime
secrets. They must never be added to this workflow, committed environment
files, Docker build arguments, or frontend `VITE_` variables.

## AWS deployment preparation

Deployment is intentionally not enabled. When deployment work is authorized,
create a separate workflow protected by a GitHub `production` environment and
configure:

- GitHub environment secret `AWS_ROLE_ARN` for the least-privilege IAM role.
- GitHub environment variable `AWS_REGION`.
- GitHub environment variable `AWS_ECR_REPOSITORY`.
- Required reviewers and deployment branch restrictions on the `production`
  environment.

Prefer GitHub's OpenID Connect integration with AWS. The future deployment job
will need `id-token: write` only at job level and should pass
`${{ secrets.AWS_ROLE_ARN }}` to the AWS credentials action. Do not store
long-lived AWS access-key credentials in GitHub.

The current manual deployment path uses an operator-owned mode-600 environment
file and injects secrets at runtime. Central retrieval from Systems Manager
Parameter Store or Secrets Manager is an optional operator integration, not
implemented in CI; review access policy and service charges before choosing it.
The Docker image must remain environment-neutral.

The future deployment workflow can reuse the Docker build configuration, add
an ECR login, switch `push` to `true`, and deploy the immutable image digest.
None of those publishing or deployment steps exist in the current pipeline.
