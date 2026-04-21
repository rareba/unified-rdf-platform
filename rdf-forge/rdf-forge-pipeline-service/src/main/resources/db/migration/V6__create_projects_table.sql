-- V6: Create projects table for the Project Workspace feature.
--
-- Notes:
--   * default-schema is already pipeline (see application.yml), so the
--     qualified "pipeline.projects" name is explicit but equivalent to
--     bare "projects" under the pipeline schema.
--   * No cross-schema FK from other services' tables (pipelines.project_id,
--     cubes.project_id, etc.) — each service owns its own schema and we
--     treat projectId as an opaque UUID. Cascading cleanup is Phase 7.

CREATE TABLE IF NOT EXISTS pipeline.projects (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    base_uri VARCHAR(1000) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    metadata JSONB,
    CONSTRAINT uq_projects_created_by_name UNIQUE (created_by, name)
);

CREATE INDEX IF NOT EXISTS idx_projects_created_by ON pipeline.projects(created_by);
CREATE INDEX IF NOT EXISTS idx_projects_status ON pipeline.projects(status);
