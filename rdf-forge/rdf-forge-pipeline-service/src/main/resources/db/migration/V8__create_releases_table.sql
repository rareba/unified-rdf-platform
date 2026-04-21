-- V8: Create releases table for the Release Factory feature (Phase 6).
--
-- Notes:
--   * Default schema is pipeline (see application.yml).
--   * (project_id, version) is UNIQUE — a SemVer string cannot be reused
--     within a project once consumed.
--   * manifest is jsonb so the payload can evolve freely (it records the
--     included asset ids, validation gate result, build errors, etc.) without
--     needing further migrations.
--   * No cross-schema FK — project_id is an opaque UUID because other
--     services manage their own schemas and the platform uses ownership
--     checks at the service layer rather than DB constraints.

CREATE TABLE IF NOT EXISTS pipeline.releases (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    version VARCHAR(64) NOT NULL,
    name VARCHAR(255) NOT NULL,
    notes TEXT,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    created_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    published_at TIMESTAMP WITH TIME ZONE,
    artifact_uri VARCHAR(2000),
    artifact_size_bytes BIGINT NOT NULL DEFAULT 0,
    manifest JSONB,
    CONSTRAINT uq_releases_project_version UNIQUE (project_id, version)
);

CREATE INDEX IF NOT EXISTS idx_releases_project_id ON pipeline.releases(project_id);
CREATE INDEX IF NOT EXISTS idx_releases_status ON pipeline.releases(status);
CREATE INDEX IF NOT EXISTS idx_releases_published_at ON pipeline.releases(published_at);
