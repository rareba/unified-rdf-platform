#!/usr/bin/env bash
# =============================================================================
# Keycloak post-import user/secret bootstrap
# =============================================================================
#
# Why: realm-export.json deliberately ships with NO user passwords and NO
# client secret. Keycloak's `--import-realm` does not substitute environment
# variables inside realm JSON, so we seed them here via kcadm.sh after the
# server is up.
#
# Intended use:
#   - Exec into the running `rdf-forge-keycloak` container, OR
#   - Mount this script under /opt/keycloak/bin and run it once post-boot.
#
# Required env (read from the .env file consumed by docker compose):
#   KEYCLOAK_ADMIN                   - master-realm admin username
#   KEYCLOAK_ADMIN_PASSWORD          - master-realm admin password
#   KEYCLOAK_DEMO_ADMIN_PASSWORD     - password for the realm 'admin' user
#   KEYCLOAK_DEMO_USER_PASSWORD      - password for the realm 'user' user
#   KEYCLOAK_CLIENT_SECRET           - secret to set on rdf-forge-gateway
#
# Production: DELETE the demo users entirely and create real accounts.
# =============================================================================
set -euo pipefail

REALM=${KC_REALM:-rdfforge}
SERVER=${KC_SERVER:-http://localhost:8080}
KCADM=${KCADM:-/opt/keycloak/bin/kcadm.sh}

: "${KEYCLOAK_ADMIN:?set KEYCLOAK_ADMIN}"
: "${KEYCLOAK_ADMIN_PASSWORD:?set KEYCLOAK_ADMIN_PASSWORD}"
: "${KEYCLOAK_DEMO_ADMIN_PASSWORD:?set KEYCLOAK_DEMO_ADMIN_PASSWORD}"
: "${KEYCLOAK_DEMO_USER_PASSWORD:?set KEYCLOAK_DEMO_USER_PASSWORD}"
: "${KEYCLOAK_CLIENT_SECRET:?set KEYCLOAK_CLIENT_SECRET}"

echo "[keycloak-users] Logging in as ${KEYCLOAK_ADMIN}..."
"$KCADM" config credentials \
    --server "$SERVER" \
    --realm master \
    --user "$KEYCLOAK_ADMIN" \
    --password "$KEYCLOAK_ADMIN_PASSWORD"

echo "[keycloak-users] Setting password for realm user 'admin'..."
"$KCADM" set-password -r "$REALM" --username admin \
    --new-password "$KEYCLOAK_DEMO_ADMIN_PASSWORD"

echo "[keycloak-users] Setting password for realm user 'user'..."
"$KCADM" set-password -r "$REALM" --username user \
    --new-password "$KEYCLOAK_DEMO_USER_PASSWORD"

echo "[keycloak-users] Rotating secret on client 'rdf-forge-gateway'..."
CLIENT_UUID=$("$KCADM" get clients -r "$REALM" -q clientId=rdf-forge-gateway --fields id --format csv --noquotes | tail -n1)
if [[ -z "$CLIENT_UUID" ]]; then
    echo "[keycloak-users] ERROR: client rdf-forge-gateway not found in realm $REALM" >&2
    exit 1
fi
"$KCADM" update "clients/$CLIENT_UUID" -r "$REALM" \
    -s "secret=$KEYCLOAK_CLIENT_SECRET"

echo "[keycloak-users] Done."
