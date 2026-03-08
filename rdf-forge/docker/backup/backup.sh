#!/usr/bin/env bash
# =============================================================================
# RDF Forge - Production Backup Script
# =============================================================================
#
# Backs up PostgreSQL, MinIO data, and Keycloak realm configuration into a
# single timestamped archive. Supports retention-based cleanup of old backups.
#
# Environment variables:
#   BACKUP_DIR          - Directory to store backups (default: /backups)
#   RETENTION_DAYS      - Delete backups older than N days (default: 30)
#   DB_HOST             - PostgreSQL host (default: postgres)
#   DB_PORT             - PostgreSQL port (default: 5432)
#   DB_NAME             - PostgreSQL database name (default: rdfforge)
#   DB_USER             - PostgreSQL user (default: rdfforge)
#   PGPASSWORD          - PostgreSQL password (required)
#   MINIO_ENDPOINT      - MinIO endpoint URL (default: http://minio:9000)
#   MINIO_ACCESS_KEY    - MinIO access key (required)
#   MINIO_SECRET_KEY    - MinIO secret key (required)
#   MINIO_BUCKET        - MinIO bucket to back up (default: rdf-forge-data)
#   KEYCLOAK_URL        - Keycloak base URL (default: http://keycloak:8080)
#   KEYCLOAK_REALM      - Keycloak realm to export (default: rdfforge)
#   KEYCLOAK_ADMIN_USER - Keycloak admin username (default: admin)
#   KEYCLOAK_ADMIN_PASS - Keycloak admin password (required for realm export)
#
# Usage:
#   ./backup.sh
#   BACKUP_DIR=/mnt/backups RETENTION_DAYS=7 ./backup.sh
# =============================================================================

set -euo pipefail

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------
BACKUP_DIR="${BACKUP_DIR:-/backups}"
RETENTION_DAYS="${RETENTION_DAYS:-30}"
DB_HOST="${DB_HOST:-postgres}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-rdfforge}"
DB_USER="${DB_USER:-rdfforge}"
MINIO_ENDPOINT="${MINIO_ENDPOINT:-http://minio:9000}"
MINIO_ACCESS_KEY="${MINIO_ACCESS_KEY:-}"
MINIO_SECRET_KEY="${MINIO_SECRET_KEY:-}"
MINIO_BUCKET="${MINIO_BUCKET:-rdf-forge-data}"
KEYCLOAK_URL="${KEYCLOAK_URL:-http://keycloak:8080}"
KEYCLOAK_REALM="${KEYCLOAK_REALM:-rdfforge}"
KEYCLOAK_ADMIN_USER="${KEYCLOAK_ADMIN_USER:-admin}"
KEYCLOAK_ADMIN_PASS="${KEYCLOAK_ADMIN_PASS:-}"

TIMESTAMP="$(date +%Y%m%d_%H%M%S)"
WORK_DIR="${BACKUP_DIR}/work_${TIMESTAMP}"
ARCHIVE_NAME="rdf-forge-backup_${TIMESTAMP}.tar.gz"
LOG_FILE="${BACKUP_DIR}/backup_${TIMESTAMP}.log"

ERRORS=0

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------
log() {
    local msg="[$(date '+%Y-%m-%d %H:%M:%S')] $*"
    echo "$msg" | tee -a "$LOG_FILE"
}

log_error() {
    local msg="[$(date '+%Y-%m-%d %H:%M:%S')] ERROR: $*"
    echo "$msg" | tee -a "$LOG_FILE" >&2
    ERRORS=$((ERRORS + 1))
}

cleanup_work_dir() {
    if [ -d "$WORK_DIR" ]; then
        rm -rf "$WORK_DIR"
    fi
}

trap cleanup_work_dir EXIT

# ---------------------------------------------------------------------------
# Pre-flight checks
# ---------------------------------------------------------------------------
mkdir -p "$BACKUP_DIR"
mkdir -p "$WORK_DIR"

log "Starting RDF Forge backup"
log "Backup directory: ${BACKUP_DIR}"
log "Retention policy: ${RETENTION_DAYS} days"

# ---------------------------------------------------------------------------
# 1. PostgreSQL Backup
# ---------------------------------------------------------------------------
log "--- PostgreSQL backup ---"
PG_DUMP_FILE="${WORK_DIR}/postgres_${DB_NAME}_${TIMESTAMP}.sql.gz"

if [ -z "${PGPASSWORD:-}" ]; then
    log_error "PGPASSWORD is not set; skipping PostgreSQL backup"
else
    if pg_dump -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" \
        --no-owner --no-privileges --format=plain 2>>"$LOG_FILE" \
        | gzip > "$PG_DUMP_FILE"; then
        PG_SIZE=$(du -sh "$PG_DUMP_FILE" | cut -f1)
        log "PostgreSQL dump completed (${PG_SIZE}): ${PG_DUMP_FILE}"
    else
        log_error "PostgreSQL dump failed"
    fi
fi

# ---------------------------------------------------------------------------
# 2. MinIO / Object Storage Backup
# ---------------------------------------------------------------------------
log "--- MinIO backup ---"
MINIO_BACKUP_DIR="${WORK_DIR}/minio"
mkdir -p "$MINIO_BACKUP_DIR"

if [ -z "$MINIO_ACCESS_KEY" ] || [ -z "$MINIO_SECRET_KEY" ]; then
    log_error "MINIO_ACCESS_KEY or MINIO_SECRET_KEY not set; skipping MinIO backup"
else
    # Configure mc alias
    if mc alias set rdfforge-backup "$MINIO_ENDPOINT" "$MINIO_ACCESS_KEY" "$MINIO_SECRET_KEY" \
        --api S3v4 >>"$LOG_FILE" 2>&1; then
        log "MinIO alias configured"

        # Mirror the bucket to local directory
        if mc mirror "rdfforge-backup/${MINIO_BUCKET}" "$MINIO_BACKUP_DIR" \
            >>"$LOG_FILE" 2>&1; then
            MINIO_FILE_COUNT=$(find "$MINIO_BACKUP_DIR" -type f | wc -l)
            MINIO_SIZE=$(du -sh "$MINIO_BACKUP_DIR" | cut -f1)
            log "MinIO mirror completed: ${MINIO_FILE_COUNT} files (${MINIO_SIZE})"
        else
            log_error "MinIO mirror failed"
        fi
    else
        log_error "Failed to configure MinIO alias"
    fi
fi

# ---------------------------------------------------------------------------
# 3. Keycloak Realm Export
# ---------------------------------------------------------------------------
log "--- Keycloak realm export ---"
KEYCLOAK_EXPORT_FILE="${WORK_DIR}/keycloak_${KEYCLOAK_REALM}_${TIMESTAMP}.json"

if [ -z "$KEYCLOAK_ADMIN_PASS" ]; then
    log_error "KEYCLOAK_ADMIN_PASS not set; skipping Keycloak export"
else
    # Obtain admin token
    TOKEN_RESPONSE=$(curl -sf -X POST \
        "${KEYCLOAK_URL}/realms/master/protocol/openid-connect/token" \
        -H "Content-Type: application/x-www-form-urlencoded" \
        -d "username=${KEYCLOAK_ADMIN_USER}" \
        -d "password=${KEYCLOAK_ADMIN_PASS}" \
        -d "grant_type=password" \
        -d "client_id=admin-cli" 2>>"$LOG_FILE") || true

    if [ -z "$TOKEN_RESPONSE" ]; then
        log_error "Failed to obtain Keycloak admin token"
    else
        ADMIN_TOKEN=$(echo "$TOKEN_RESPONSE" | python3 -c \
            "import sys,json; print(json.load(sys.stdin).get('access_token',''))" 2>/dev/null || true)

        if [ -z "$ADMIN_TOKEN" ]; then
            # Fallback: try with jq
            ADMIN_TOKEN=$(echo "$TOKEN_RESPONSE" | jq -r '.access_token' 2>/dev/null || true)
        fi

        if [ -z "$ADMIN_TOKEN" ]; then
            log_error "Failed to parse Keycloak admin token"
        else
            # Export realm
            HTTP_CODE=$(curl -s -o "$KEYCLOAK_EXPORT_FILE" -w "%{http_code}" \
                "${KEYCLOAK_URL}/admin/realms/${KEYCLOAK_REALM}" \
                -H "Authorization: Bearer ${ADMIN_TOKEN}" \
                -H "Accept: application/json" 2>>"$LOG_FILE")

            if [ "$HTTP_CODE" = "200" ] && [ -s "$KEYCLOAK_EXPORT_FILE" ]; then
                KC_SIZE=$(du -sh "$KEYCLOAK_EXPORT_FILE" | cut -f1)
                log "Keycloak realm export completed (${KC_SIZE})"
            else
                log_error "Keycloak realm export failed (HTTP ${HTTP_CODE})"
                rm -f "$KEYCLOAK_EXPORT_FILE"
            fi
        fi
    fi
fi

# ---------------------------------------------------------------------------
# 4. Create Archive
# ---------------------------------------------------------------------------
log "--- Creating archive ---"
ARCHIVE_PATH="${BACKUP_DIR}/${ARCHIVE_NAME}"

# Write manifest
cat > "${WORK_DIR}/manifest.json" <<EOF
{
    "version": "1.0",
    "timestamp": "${TIMESTAMP}",
    "date": "$(date -Iseconds)",
    "components": {
        "postgres": {
            "host": "${DB_HOST}",
            "database": "${DB_NAME}",
            "user": "${DB_USER}"
        },
        "minio": {
            "endpoint": "${MINIO_ENDPOINT}",
            "bucket": "${MINIO_BUCKET}"
        },
        "keycloak": {
            "url": "${KEYCLOAK_URL}",
            "realm": "${KEYCLOAK_REALM}"
        }
    },
    "errors": ${ERRORS}
}
EOF

if tar -czf "$ARCHIVE_PATH" -C "$WORK_DIR" . 2>>"$LOG_FILE"; then
    ARCHIVE_SIZE=$(du -sh "$ARCHIVE_PATH" | cut -f1)
    log "Archive created: ${ARCHIVE_PATH} (${ARCHIVE_SIZE})"
else
    log_error "Failed to create archive"
fi

# ---------------------------------------------------------------------------
# 5. Retention Cleanup
# ---------------------------------------------------------------------------
log "--- Retention cleanup ---"
DELETED_COUNT=0
while IFS= read -r old_backup; do
    if [ -f "$old_backup" ]; then
        rm -f "$old_backup"
        DELETED_COUNT=$((DELETED_COUNT + 1))
        log "Deleted old backup: ${old_backup}"
    fi
done < <(find "$BACKUP_DIR" -maxdepth 1 -name "rdf-forge-backup_*.tar.gz" \
    -mtime "+${RETENTION_DAYS}" -type f 2>/dev/null || true)

# Also clean old log files
find "$BACKUP_DIR" -maxdepth 1 -name "backup_*.log" \
    -mtime "+${RETENTION_DAYS}" -type f -delete 2>/dev/null || true

log "Retention cleanup: deleted ${DELETED_COUNT} old backup(s)"

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------
log "=== Backup Summary ==="
log "Archive: ${ARCHIVE_PATH}"
if [ -f "$ARCHIVE_PATH" ]; then
    log "Size: $(du -sh "$ARCHIVE_PATH" | cut -f1)"
fi
log "Errors: ${ERRORS}"
log "Log: ${LOG_FILE}"

if [ "$ERRORS" -gt 0 ]; then
    log_error "Backup completed with ${ERRORS} error(s)"
    exit 1
fi

log "Backup completed successfully"
exit 0
