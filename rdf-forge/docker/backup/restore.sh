#!/usr/bin/env bash
# =============================================================================
# RDF Forge - Production Restore Script
# =============================================================================
#
# Restores a backup archive produced by backup.sh. Supports PostgreSQL,
# MinIO, and Keycloak realm data restoration.
#
# Environment variables:
#   DB_HOST             - PostgreSQL host (default: postgres)
#   DB_PORT             - PostgreSQL port (default: 5432)
#   DB_NAME             - PostgreSQL database name (default: rdfforge)
#   DB_USER             - PostgreSQL user (default: rdfforge)
#   PGPASSWORD          - PostgreSQL password (required)
#   MINIO_ENDPOINT      - MinIO endpoint URL (default: http://minio:9000)
#   MINIO_ACCESS_KEY    - MinIO access key (required)
#   MINIO_SECRET_KEY    - MinIO secret key (required)
#   MINIO_BUCKET        - MinIO bucket to restore into (default: rdf-forge-data)
#
# Usage:
#   ./restore.sh /backups/rdf-forge-backup_20260308_120000.tar.gz
#   ./restore.sh --dry-run /backups/rdf-forge-backup_20260308_120000.tar.gz
# =============================================================================

set -euo pipefail

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------
DB_HOST="${DB_HOST:-postgres}"
DB_PORT="${DB_PORT:-5432}"
DB_NAME="${DB_NAME:-rdfforge}"
DB_USER="${DB_USER:-rdfforge}"
MINIO_ENDPOINT="${MINIO_ENDPOINT:-http://minio:9000}"
MINIO_ACCESS_KEY="${MINIO_ACCESS_KEY:-}"
MINIO_SECRET_KEY="${MINIO_SECRET_KEY:-}"
MINIO_BUCKET="${MINIO_BUCKET:-rdf-forge-data}"

DRY_RUN=false
ARCHIVE_PATH=""
ERRORS=0

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------
log() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*"
}

log_error() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] ERROR: $*" >&2
    ERRORS=$((ERRORS + 1))
}

log_dry() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] [DRY-RUN] $*"
}

usage() {
    echo "Usage: $0 [--dry-run] <backup-archive.tar.gz>"
    echo ""
    echo "Options:"
    echo "  --dry-run    Show what would be restored without making changes"
    echo ""
    echo "Example:"
    echo "  $0 /backups/rdf-forge-backup_20260308_120000.tar.gz"
    echo "  $0 --dry-run /backups/rdf-forge-backup_20260308_120000.tar.gz"
    exit 1
}

# ---------------------------------------------------------------------------
# Parse Arguments
# ---------------------------------------------------------------------------
while [ $# -gt 0 ]; do
    case "$1" in
        --dry-run)
            DRY_RUN=true
            shift
            ;;
        --help|-h)
            usage
            ;;
        *)
            ARCHIVE_PATH="$1"
            shift
            ;;
    esac
done

if [ -z "$ARCHIVE_PATH" ]; then
    log_error "No archive path provided"
    usage
fi

if [ ! -f "$ARCHIVE_PATH" ]; then
    log_error "Archive not found: ${ARCHIVE_PATH}"
    exit 1
fi

# ---------------------------------------------------------------------------
# Extract Archive
# ---------------------------------------------------------------------------
WORK_DIR=$(mktemp -d)
cleanup() {
    rm -rf "$WORK_DIR"
}
trap cleanup EXIT

log "Extracting archive: ${ARCHIVE_PATH}"
tar -xzf "$ARCHIVE_PATH" -C "$WORK_DIR"

# ---------------------------------------------------------------------------
# Read Manifest
# ---------------------------------------------------------------------------
if [ -f "${WORK_DIR}/manifest.json" ]; then
    log "Backup manifest found"
    if command -v python3 &>/dev/null; then
        BACKUP_TIMESTAMP=$(python3 -c \
            "import json; m=json.load(open('${WORK_DIR}/manifest.json')); print(m.get('date','unknown'))" 2>/dev/null || echo "unknown")
    elif command -v jq &>/dev/null; then
        BACKUP_TIMESTAMP=$(jq -r '.date // "unknown"' "${WORK_DIR}/manifest.json" 2>/dev/null || echo "unknown")
    else
        BACKUP_TIMESTAMP="unknown"
    fi
    log "Backup date: ${BACKUP_TIMESTAMP}"
else
    log "No manifest found; proceeding with best-effort restore"
fi

# ---------------------------------------------------------------------------
# Discover contents
# ---------------------------------------------------------------------------
PG_DUMP=$(find "$WORK_DIR" -maxdepth 1 -name "postgres_*.sql.gz" -type f | head -1)
MINIO_DATA_DIR=$(find "$WORK_DIR" -maxdepth 1 -name "minio" -type d | head -1)
KC_EXPORT=$(find "$WORK_DIR" -maxdepth 1 -name "keycloak_*.json" -type f | head -1)

log "=== Archive Contents ==="
if [ -n "$PG_DUMP" ]; then
    PG_SIZE=$(du -sh "$PG_DUMP" | cut -f1)
    log "  PostgreSQL dump: $(basename "$PG_DUMP") (${PG_SIZE})"
else
    log "  PostgreSQL dump: NOT FOUND"
fi

if [ -n "$MINIO_DATA_DIR" ]; then
    MINIO_FILE_COUNT=$(find "$MINIO_DATA_DIR" -type f | wc -l)
    MINIO_SIZE=$(du -sh "$MINIO_DATA_DIR" | cut -f1)
    log "  MinIO data: ${MINIO_FILE_COUNT} files (${MINIO_SIZE})"
else
    log "  MinIO data: NOT FOUND"
fi

if [ -n "$KC_EXPORT" ]; then
    KC_SIZE=$(du -sh "$KC_EXPORT" | cut -f1)
    log "  Keycloak realm: $(basename "$KC_EXPORT") (${KC_SIZE})"
else
    log "  Keycloak realm: NOT FOUND"
fi

# ---------------------------------------------------------------------------
# Dry-run mode: stop here
# ---------------------------------------------------------------------------
if [ "$DRY_RUN" = true ]; then
    log_dry "=== Dry-run Summary ==="
    if [ -n "$PG_DUMP" ]; then
        log_dry "Would restore PostgreSQL database '${DB_NAME}' on ${DB_HOST}:${DB_PORT}"
    fi
    if [ -n "$MINIO_DATA_DIR" ]; then
        log_dry "Would restore ${MINIO_FILE_COUNT} files to MinIO bucket '${MINIO_BUCKET}'"
    fi
    if [ -n "$KC_EXPORT" ]; then
        log_dry "Would display Keycloak realm export (manual import recommended)"
    fi
    log_dry "No changes were made"
    exit 0
fi

# ---------------------------------------------------------------------------
# Confirmation prompt (skip if non-interactive)
# ---------------------------------------------------------------------------
if [ -t 0 ]; then
    echo ""
    echo "WARNING: This will overwrite current data in:"
    [ -n "$PG_DUMP" ] && echo "  - PostgreSQL database '${DB_NAME}'"
    [ -n "$MINIO_DATA_DIR" ] && echo "  - MinIO bucket '${MINIO_BUCKET}'"
    echo ""
    read -r -p "Continue? (yes/no): " CONFIRM
    if [ "$CONFIRM" != "yes" ]; then
        log "Restore cancelled by user"
        exit 0
    fi
fi

# ---------------------------------------------------------------------------
# 1. Restore PostgreSQL
# ---------------------------------------------------------------------------
if [ -n "$PG_DUMP" ]; then
    log "--- Restoring PostgreSQL ---"

    if [ -z "${PGPASSWORD:-}" ]; then
        log_error "PGPASSWORD is not set; skipping PostgreSQL restore"
    else
        # Drop and recreate the database to get a clean state
        log "Dropping existing connections and recreating database..."
        psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d postgres \
            -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname='${DB_NAME}' AND pid <> pg_backend_pid();" \
            2>/dev/null || true

        psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d postgres \
            -c "DROP DATABASE IF EXISTS ${DB_NAME};" 2>/dev/null || true

        psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d postgres \
            -c "CREATE DATABASE ${DB_NAME} OWNER ${DB_USER};" 2>/dev/null || true

        # Restore the dump
        if gunzip -c "$PG_DUMP" | psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" \
            --quiet --single-transaction 2>/dev/null; then
            log "PostgreSQL restore completed"
        else
            log_error "PostgreSQL restore encountered errors"
        fi

        # Validate by checking row counts in key tables
        log "Validating PostgreSQL restore..."
        for TABLE in pipelines jobs triplestore_connections; do
            ROW_COUNT=$(psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" \
                -t -c "SELECT COUNT(*) FROM ${TABLE};" 2>/dev/null | tr -d ' ' || echo "N/A")
            log "  Table '${TABLE}': ${ROW_COUNT} rows"
        done
    fi
fi

# ---------------------------------------------------------------------------
# 2. Restore MinIO
# ---------------------------------------------------------------------------
if [ -n "$MINIO_DATA_DIR" ]; then
    log "--- Restoring MinIO ---"

    if [ -z "$MINIO_ACCESS_KEY" ] || [ -z "$MINIO_SECRET_KEY" ]; then
        log_error "MINIO_ACCESS_KEY or MINIO_SECRET_KEY not set; skipping MinIO restore"
    else
        if mc alias set rdfforge-restore "$MINIO_ENDPOINT" "$MINIO_ACCESS_KEY" "$MINIO_SECRET_KEY" \
            --api S3v4 2>/dev/null; then

            # Ensure bucket exists
            mc mb "rdfforge-restore/${MINIO_BUCKET}" 2>/dev/null || true

            # Mirror from backup to MinIO
            if mc mirror --overwrite "$MINIO_DATA_DIR" "rdfforge-restore/${MINIO_BUCKET}" 2>/dev/null; then
                RESTORED_COUNT=$(find "$MINIO_DATA_DIR" -type f | wc -l)
                log "MinIO restore completed: ${RESTORED_COUNT} files restored"
            else
                log_error "MinIO restore failed"
            fi
        else
            log_error "Failed to configure MinIO alias for restore"
        fi
    fi
fi

# ---------------------------------------------------------------------------
# 3. Keycloak Realm (informational)
# ---------------------------------------------------------------------------
if [ -n "$KC_EXPORT" ]; then
    log "--- Keycloak Realm ---"
    log "Keycloak realm export found: $(basename "$KC_EXPORT")"
    log "Keycloak realm import should be done manually or via the Keycloak admin console."
    log "Realm export file has been extracted to: ${KC_EXPORT}"
    log "To import, use: kcadm.sh create partialImport -r ${KEYCLOAK_REALM} -f <file>"
fi

# ---------------------------------------------------------------------------
# Summary
# ---------------------------------------------------------------------------
log "=== Restore Summary ==="
log "Archive: ${ARCHIVE_PATH}"
log "Errors: ${ERRORS}"

if [ "$ERRORS" -gt 0 ]; then
    log_error "Restore completed with ${ERRORS} error(s)"
    exit 1
fi

log "Restore completed successfully"
exit 0
