# Deployment runbook

This runbook covers provisioning, startup order, migrations, health checks,
and rollback for the RDF Forge stack. It assumes Docker Desktop + Docker
Compose v2.x (or any OCI-compatible runtime) and a target database /
object store you control.

## Environment variables

Copy `rdf-forge/.env.example` to `.env` and override every secret:

| Variable | Purpose | Dev default | Notes |
|---|---|---|---|
| `POSTGRES_USER` / `POSTGRES_PASSWORD` / `POSTGRES_DB` | Primary database | `rdfforge` / `rdfforge_dev_only` / `rdfforge` | Use managed Postgres in prod |
| `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD` | Object storage | `rdfforge` / `rdfforge_dev_only` | Swap for S3 / Azure Blob endpoint + IAM in prod |
| `KEYCLOAK_ADMIN` / `KEYCLOAK_ADMIN_PASSWORD` | Keycloak bootstrap admin | `admin` / `admin_dev_only` | Rotate on first login |
| `KEYCLOAK_DEMO_ADMIN_PASSWORD` / `KEYCLOAK_DEMO_USER_PASSWORD` | Demo realm users created by `docker/keycloak-users.sh` | `-` | Required if importing the demo realm |
| `KEYCLOAK_CLIENT_SECRET` | Gateway <-> Keycloak client secret | `-` | Required when `SPRING_PROFILES_ACTIVE=keycloak` |
| `CORS_ALLOWED_ORIGINS` | Allowed origins in the gateway | `http://localhost:4200,http://localhost:8000` | Comma-separated |
| `SPRING_PROFILES_ACTIVE` | Backend profile | service-specific | **Production must NOT include `docker` or `noauth`** — both activate the no-auth security profile |
| `RDF_FORGE_RELEASE_ARTIFACT_DIR` | Release bundle directory | `/tmp/rdf-forge-releases` | Mount a persistent volume |

The complete inventory is in `rdf-forge/.env.example`. Missing
`KEYCLOAK_CLIENT_SECRET` causes the gateway auth filter to reject every
request once it's active, so set it before the first prod boot.

## Compose file to pick

| File | Use case |
|---|---|
| `docker-compose.standalone.yml` | All services on one host with the `offline`/`standalone` UI build. Good for demos, single-operator deployments. |
| `docker-compose.yml` | Non-Windows full stack (Windows Hyper-V often reserves ports 3000/8000-8006). |
| `docker-compose.production.yml` | Production baseline: resource limits, healthchecks, non-root users, read-only rootfs, Docker secrets mounts. Start here in real deployments. |
| `docker-compose.keycloak.yml` | Only Keycloak + Postgres when running the rest natively. |
| `docker-compose.development.yml` | Local dev with grafana / prometheus observability. |

## Startup order

The backend services depend on each other at runtime (each reads from the
same Postgres instance but in its own schema). Compose dependencies only
wait for the datastores and the gateway; in production bring services up
in this order so Flyway migrations don't fight over a cold database:

1. **Infrastructure**: `postgres`, `redis`, `minio`, (`graphdb`|`fuseki`),
   `keycloak`.
2. **Stateful prep**:
   - Postgres: create the `keycloak` database (the entrypoint honours
     `POSTGRES_MULTIPLE_DATABASES`) and the per-service schemas
     (`pipeline`, `data`, `dimension`, `job`, `shacl`, `triplestore`,
     `auth`). Each service creates its own schema via
     `spring.flyway.create-schemas=true`; pre-create them with the owner
     role if your DB policy forbids runtime schema creation.
   - MinIO: create the `rdf-forge` bucket or whatever
     `rdf-forge.minio.bucket` points at.
   - Keycloak: import the realm (`rdf-forge/docker/realm-export.json`
     with the credentials stripped in commit df6d9649) then run
     `rdf-forge/docker/keycloak-users.sh` to seed the demo users and
     gateway client secret from env.
3. **Application services** in any order (each is idempotent and will
   wait on its own Flyway migration):
   - `rdf-forge-common` migrations run in whichever service's context
     first reaches `public.audit_log`. There is no separate common
     deployment target.
   - `rdf-forge-auth-service` (port 8086)
   - `rdf-forge-pipeline-service` (8001), `shacl-service` (8002),
     `job-service` (8003), `data-service` (8004),
     `dimension-service` (8005), `triplestore-service` (8006).
4. **Gateway** last (port 8000 in native dev, 3000 behind the UI proxy).
   The gateway's reactive routes assume backends are already listening;
   a cold gateway fronting a not-yet-ready service returns 503.
5. **UI** (port 4200 dev, 3000 production nginx) — points at the
   gateway, not individual services.

## Flyway migration notes

### Current migration state

| Module | Migrations | Notes |
|---|---|---|
| `rdf-forge-common` | `V1__init_audit_log.sql` | V100 file present in older checkouts was a dup; restored to V1 in this wave. |
| `rdf-forge-auth-service` | V1 | OK |
| `rdf-forge-data-service` | V1 | OK |
| `rdf-forge-job-service` | V1, V2 | OK |
| `rdf-forge-shacl-service` | V1, V2, V3 | OK |
| `rdf-forge-dimension-service` | V1, V2, V3, V4, V5, V6 | OK |
| `rdf-forge-triplestore-service` | V1, V2, V3, V4, V5 | OK |
| `rdf-forge-pipeline-service` | V1, **V4, V5**, V6, V7, V8, V9, V10 | V2/V3 intentionally skipped. See below. |

### Pipeline-service V2/V3 gap

V2 and V3 were never authored; the project jumped from V1 to V4 during
a real refactor. With Flyway's default `outOfOrder=false` this is
**safe for fresh installs** (Flyway applies V1, V4, V5, ... in order),
and safe for rolling upgrades (no ordering change). It is **NOT safe**
to later add a V2 or V3 — do not do it. Start new migrations from V11
going forward and keep the gap as a historical artifact.

### If an older deployment applied `V100__init_audit_log`

The current code ships `V1__init_audit_log.sql` with identical content.
Two safe paths:

```
# Option A — fresh rollout, no prior audit_log history.
mvn flyway:migrate -pl rdf-forge-common

# Option B — existing cluster where V100 was applied.
# Run repair first so the checksum history reconciles with V1:
docker run --rm --network rdf-forge-network \
  flyway/flyway:10.10.0 \
  -url=jdbc:postgresql://postgres:5432/rdfforge \
  -user=$POSTGRES_USER -password=$POSTGRES_PASSWORD \
  -schemas=public \
  repair
# Then start services as usual.
```

The audit_log table contents survive either path because both versions
define the same schema.

### Bootstrapping postgres with demo data

`docker/demo-data.sql` runs via the
`/docker-entrypoint-initdb.d/02-demo-data.sql` hook, which executes
**before** Flyway runs. The UPDATE statements against
`triplestore.triplestore_connections` therefore reference a table
Flyway will create only after boot.

Two honest choices for ops:

- **Recommended**: start postgres with the init scripts disabled, let
  every service run its Flyway, then run `docker/demo-data.sql`
  manually against the DB once schemas exist. Example:
  ```
  docker compose up -d postgres && docker compose up -d gateway pipeline-service \
    shacl-service triplestore-service data-service dimension-service job-service
  # wait for services healthy, then:
  docker exec -i rdf-forge-postgres psql -U $POSTGRES_USER -d $POSTGRES_DB \
    < rdf-forge/docker/demo-data.sql
  ```
- Remove the 01-init-databases and 02-demo-data hooks from the
  compose volume mount until the script is reordered to run after
  Flyway.

## Health checks

Each service exposes Spring Boot Actuator at `/actuator/health` on its
own port. The gateway aggregates them at `/actuator/health`:

```
curl -sf http://localhost:8000/actuator/health
```

Postgres: `pg_isready -h <host> -p 5432 -U $POSTGRES_USER`.
MinIO: `curl -sf http://<host>:9000/minio/health/live`.
Redis: `docker exec -it rdf-forge-redis redis-cli ping`.
GraphDB / Fuseki: `curl -sf http://<host>:7200/rest/repositories` (GraphDB)
or `curl -sf http://<host>:3030/$/ping` (Fuseki).

Keycloak: `curl -sf http://<host>:8080/realms/master`.

## Rollback

1. **UI**: previous production UI image (`rdf-forge-ui:<previous>`); the
   UI is stateless — re-point nginx or your CDN back to the older tag.
2. **Application services**: keep the previous container image tags in
   your registry. Rolling back is a tag swap + restart. Flyway
   migrations are additive in the current set; none are destructive, so
   a newer binary running against an older schema fails loud on startup
   (Flyway validation) rather than silently eating data.
3. **Database**: snapshot before migrations (`pg_dump`). If a
   migration creates data-loss risk, test rollback on a staging copy
   first. For the current V1-V10 set none delete rows.
4. **Release artifacts**: ReleaseService writes bundles to
   `RDF_FORGE_RELEASE_ARTIFACT_DIR`. A FAILED release deletes its
   partial zip atomically. If you see orphan `.zip.tmp` files, the
   process was killed mid-write and they can be removed safely.

## Known pre-existing issues

- `docker-compose.*` references healthcheck tools that a minimal image
  may not include — check each service's individual pom `resources/`.
- `docker-compose.yml` (non-prod) declares plaintext credentials as
  `${VAR:-default_dev_only}`; prod MUST provide real values via `.env`
  or Docker secrets. Log retention and backup frequency are operator
  decisions.
- Every service uses Hibernate `spring.jpa.open-in-view=false` and
  Flyway default `spring.flyway.locations=classpath:db/migration`.
  Overriding the locations is unsupported and will break the
  rdf-forge-common audit log wire-up.
