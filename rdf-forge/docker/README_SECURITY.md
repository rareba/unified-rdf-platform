# Docker / Keycloak Security Notes

## TL;DR

- `realm-export.json` ships with **no passwords and no client secret**. This is intentional.
- After `keycloak` container is healthy, run `docker/keycloak-users.sh` inside the container to seed credentials from `.env`.
- **Never** check plaintext passwords or the `rdf-forge-gateway` client secret into this repository.
- For production, **do not use this realm-export.** Use `KC_BOOTSTRAP_ADMIN_USERNAME` / `KC_BOOTSTRAP_ADMIN_PASSWORD` plus a production-hardened realm import from a managed secret store.

## Why not just use `${env.VAR}` in the JSON?

Keycloak's `--import-realm` flow does **not** perform environment-variable substitution on `credentials[].value` or `clients[].secret`. Stripping the fields and setting them post-import via `kcadm.sh` is the supported path.

## Required env vars (see `../.env.example`)

| Variable | Purpose |
|---|---|
| `KEYCLOAK_ADMIN` | Master-realm bootstrap admin username |
| `KEYCLOAK_ADMIN_PASSWORD` | Master-realm bootstrap admin password |
| `KEYCLOAK_DEMO_ADMIN_PASSWORD` | Password applied to the demo `admin` user |
| `KEYCLOAK_DEMO_USER_PASSWORD` | Password applied to the demo `user` user |
| `KEYCLOAK_CLIENT_SECRET` | Secret applied to the `rdf-forge-gateway` confidential client |

## One-shot bootstrap after `docker compose up`

```bash
# From repo root, with .env already populated:
docker compose exec --env-file ../.env keycloak \
    /opt/keycloak/bin/keycloak-users.sh
```

(mount `docker/keycloak-users.sh` into `/opt/keycloak/bin/` in your override if not already)

## Production checklist

1. Do **not** commit a `.env` file. Use your orchestrator's secret store (Kubernetes Secret, Docker Swarm secret, HashiCorp Vault, AWS SSM, etc.).
2. Remove the demo `admin` / `user` accounts from realm-export.json before shipping. Provision real accounts out-of-band.
3. Rotate `KEYCLOAK_CLIENT_SECRET` on every deploy.
4. Run Keycloak with `start --optimized` (not `start-dev`) plus TLS. See Keycloak docs: <https://www.keycloak.org/server/configuration>.
5. Pin `quay.io/keycloak/keycloak` to a specific digest, not `:24.0`.
6. Audit logs should be forwarded to SIEM.

## Follow-up for the current repository

Because plaintext `admin/admin`, `user/user`, and `client-secret=client-secret` were previously committed in git history, **the user must rotate all three post-merge** and invalidate any tokens issued by the old secret.
