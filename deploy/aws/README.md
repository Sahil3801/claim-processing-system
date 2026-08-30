# Low-cost AWS deployment: EC2 + RDS PostgreSQL

This deployment keeps the state of record in a private RDS PostgreSQL instance.
One EC2 instance runs the Spring Boot container, Redis, single-node Kafka, and
Caddy. Caddy is the only container with published ports. This is an initial
low-cost topology, not a highly available one.

No script in this directory creates, modifies, or deletes AWS resources. The
operator performs every AWS console step and explicitly runs each host script.

Frontend hosting is intentionally unchanged in this phase. When it is deployed
later, point its public API base URL at the HTTPS hostname configured here.

## Architecture

```text
Internet
  | TCP 80/443, UDP 443
  v
EC2 security group
  Caddy -> Spring Boot -> private RDS PostgreSQL
                       -> Redis cache (same Docker network)
                       -> Kafka KRaft (same Docker network)

RDS security group: TCP 5432 only from the EC2 security group
```

Redis ports 6379, Kafka ports 9092/9093, the Caddy admin port 2019, and the
application port 8080 are not published by Compose and must not be added to the
EC2 security group.

## Cost-conscious starting sizes

- EC2: Ubuntu Server 24.04 LTS, `t3a.small` or `t3.small`, 2 GiB RAM, one
  encrypted 16-20 GiB gp3 root volume. A 2 GiB swap file is created by the
  bootstrap script. Do not use a 1 GiB instance while Kafka runs locally.
- RDS: PostgreSQL 16, Single-AZ, `db.t4g.micro` where supported or
  `db.t3.micro`, 20 GiB gp3, storage encryption enabled.
- Disable RDS Performance Insights and Enhanced Monitoring initially. Basic
  CloudWatch metrics remain available. Do not add NAT Gateway, load balancer,
  ElastiCache, or managed Kafka for this first topology.
- Confirm current pricing and Free Tier eligibility in the selected Region
  before creation. Public IPv4 addresses, domain registration, snapshots beyond
  included backup storage, and data transfer can still incur charges.

The single EC2 instance, local Kafka broker, and Single-AZ RDS database are
single points of failure. Increase availability only when usage justifies the
cost.

## 1. Create networking and security groups

Use one VPC with DNS hostnames enabled:

1. Create or select two private subnets in different Availability Zones for an
   RDS DB subnet group. They do not require an internet route.
2. Place EC2 in a public subnet with a route to an internet gateway. Associate
   a stable public IPv4 address if DNS must survive instance restarts.
3. Create `claims-ec2-sg` with these inbound rules:

   | Protocol | Port | Source | Purpose |
   |---|---:|---|---|
   | TCP | 80 | `0.0.0.0/0`, `::/0` | ACME challenge and HTTP redirect |
   | TCP | 443 | `0.0.0.0/0`, `::/0` | HTTPS API |
   | UDP | 443 | `0.0.0.0/0`, `::/0` | Optional HTTP/3 |
   | TCP | 22 | One administrator `/32` or `/128` only | SSH fallback |

   Prefer Systems Manager Session Manager and omit port 22 entirely when the
   instance profile and SSM agent are configured. Keep the default outbound
   rule during initial setup so the host can reach package registries, Docker
   Hub, Let's Encrypt, the RDS endpoint, DNS, and optional SMTP.

4. Create `claims-rds-sg` with one inbound rule: PostgreSQL TCP 5432, source
   `claims-ec2-sg`. Never use a public CIDR as the database source.

## 2. Create RDS PostgreSQL

Create an RDS database with:

1. Engine PostgreSQL 16, standard create, Single-AZ.
2. Instance class `db.t4g.micro` or `db.t3.micro`.
3. Initial database name `claims_processing` and a unique master username.
4. 20 GiB encrypted gp3 storage. Disable storage autoscaling initially if a
   hard spending ceiling is more important than automatic growth.
5. The VPC and private DB subnet group from step 1.
6. Public access **No** and only `claims-rds-sg` attached.
7. Password authentication, automatic minor-version upgrades, deletion
   protection, and a 7-day automated-backup retention period.
8. Performance Insights and Enhanced Monitoring disabled for the initial
   low-cost deployment.

Record the exact RDS endpoint. Do not use an IP address because RDS addresses
can change.

## 3. Create and bootstrap EC2

1. Launch Ubuntu Server 24.04 LTS with the EC2 size and encrypted gp3 volume
   above. Attach `claims-ec2-sg`.
2. Use an IAM instance profile with `AmazonSSMManagedInstanceCore` only if using
   Session Manager. The application itself needs no AWS API permissions.
3. Connect through Session Manager or restricted SSH.
4. Clone the repository into the prepared location and run the bootstrap:

```bash
sudo mkdir -p /opt/claims-processing
sudo chown "$USER:$USER" /opt/claims-processing
git clone YOUR_REPOSITORY_URL /opt/claims-processing
cd /opt/claims-processing
bash deploy/aws/scripts/bootstrap-ec2.sh
```

Sign out and reconnect so Docker group membership is refreshed. Membership in
the Docker group is effectively root access; grant it only to operators.

### Create the application database role

The application role needs to run Flyway migrations but should not use the RDS
master credentials. After reconnecting to EC2, download the RDS CA bundle and
connect using the PostgreSQL image:

```bash
cd /opt/claims-processing
mkdir -p deploy/aws/certs
curl -fsSL https://truststore.pki.rds.amazonaws.com/global/global-bundle.pem \
  -o deploy/aws/certs/rds-global-bundle.pem
read -rsp 'RDS master password: ' RDS_MASTER_PASSWORD; echo
read -rsp 'New claims_app password: ' APP_DB_PASSWORD; echo

docker run --rm -i \
  -e PGPASSWORD="$RDS_MASTER_PASSWORD" \
  -v "$PWD/deploy/aws/certs/rds-global-bundle.pem:/rds.pem:ro" \
  postgres:16.15-alpine \
  psql "host=YOUR_RDS_ENDPOINT port=5432 dbname=claims_processing user=YOUR_MASTER_USER sslrootcert=/rds.pem sslmode=verify-full" \
  -v app_password="$APP_DB_PASSWORD" <<'SQL'
CREATE ROLE claims_app LOGIN PASSWORD :'app_password';
GRANT CONNECT, TEMPORARY ON DATABASE claims_processing TO claims_app;
\connect claims_processing
GRANT USAGE, CREATE ON SCHEMA public TO claims_app;
SQL

unset RDS_MASTER_PASSWORD APP_DB_PASSWORD
```

If the role already exists, review it instead of rerunning the statements.

## 4. Configure DNS and secrets

Create an `A` record such as `api.example.com` pointing to the instance's stable
public address. Ports 80 and 443 must reach Caddy for automatic HTTPS.

Prepare the operator-owned deployment environment:

```bash
cd /opt/claims-processing/deploy/aws
cp production.env.example .env.production
chmod 600 .env.production
openssl rand -base64 48 | tr '+/' '-_' | tr -d '=\n'
openssl rand -base64 16 | tr '+/' '-_' | tr -d '=\n'
editor .env.production
```

Use the first command independently for `DB_PASSWORD`, `REDIS_PASSWORD`, and
`JWT_SECRET`. Use the 22-character result from the second for
`KAFKA_CLUSTER_ID`. Set:

- `APP_DOMAIN` to the DNS name.
- `DB_JDBC_URL` to the private RDS endpoint, preserving `sslmode=verify-full`
  and the certificate path from the example.
- `DB_USERNAME=claims_app` and the application-role password.
- Optional mail credentials only when mail is enabled.

The deployment refuses example values or an environment file not set to mode
600. Never commit `.env.production`, copy it into an image, place secrets in
Docker build arguments, or put backend secrets in frontend `VITE_` variables.
Docker environment values are visible to root and Docker-group operators, so
keep that group restricted and encrypt the EC2 volume.

For stronger centralized handling without adding Secrets Manager charges, use
SSM Parameter Store Standard `SecureString` parameters and an instance role
limited to the exact production parameter path. Retrieve them into the same
mode-600 file immediately before deployment. Do not grant wildcard parameter or
KMS access.

## 5. Deploy manually

From the tested commit:

```bash
cd /opt/claims-processing
git status --short
git rev-parse HEAD
bash deploy/aws/scripts/deploy.sh
```

The deploy script:

1. Validates the environment file and permissions.
2. Downloads and validates the current AWS RDS root CA bundle.
3. validates the resolved Compose configuration.
4. Builds the Spring Boot image locally.
5. Starts Redis, Kafka, the API, and Caddy.
6. Waits up to four minutes for every health check.

Flyway applies the existing schema migrations when the API starts. PostgreSQL
remains the source of truth. Redis contains no persistent data. Kafka retains
one day of events by default and uses the `kafka-data` Docker volume.

## 6. Verify

```bash
cd /opt/claims-processing
bash deploy/aws/scripts/health-check.sh
curl --fail --silent --show-error https://api.example.com/actuator/health
docker compose --env-file deploy/aws/.env.production \
  -f deploy/aws/docker-compose.ec2.yml ps
docker compose --env-file deploy/aws/.env.production \
  -f deploy/aws/docker-compose.ec2.yml logs --tail=100 app
```

Expected health response:

```json
{"status":"UP"}
```

Also verify that ports 8080, 6379, 9092, 9093, and 2019 are unreachable from
outside the instance.

## Updates and rollback

Before an update, run CI, take an RDS manual snapshot when the release includes
schema changes, and record the current Git commit and image ID. Then:

```bash
cd /opt/claims-processing
git fetch --prune
git checkout EXACT_TESTED_COMMIT
bash deploy/aws/scripts/deploy.sh
```

For application-only rollback, check out the previously recorded commit and run
the deployment script again. Flyway migrations are forward-only; do not assume
that rolling back application code reverses a database migration. Restore a
tested RDS snapshot only as a deliberate recovery operation.

## Operations and backups

- Keep RDS automated backups enabled for at least seven days and test a restore
  before relying on them.
- Take a final snapshot before deleting RDS. Manual snapshots continue to incur
  storage charges until deleted.
- PostgreSQL is authoritative, so Redis does not need backup.
- Kafka is single-node and local; its volume protects against container
  recreation, not instance loss. Retention is intentionally short.
- Docker logs rotate at 10 MiB with three files when the bootstrap creates the
  daemon configuration.
- Apply Ubuntu security updates regularly and reboot during a maintenance
  window when required.
- Review EC2 CPU credit balance, memory/swap, disk use, RDS connections, free
  storage, and backup success before resizing or adding managed services.

Useful commands:

```bash
free -h
df -h
docker stats --no-stream
docker system df
docker compose --env-file deploy/aws/.env.production \
  -f deploy/aws/docker-compose.ec2.yml logs --since=30m
```
