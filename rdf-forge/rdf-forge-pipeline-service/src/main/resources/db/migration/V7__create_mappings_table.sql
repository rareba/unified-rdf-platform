-- V7: Create mappings table for the Universal Mapping Studio feature.
--
-- Notes:
--   * Default schema is pipeline (see application.yml).
--   * (project_id, name) is UNIQUE — within a project, mapping names are
--     distinct so the UI can address them by name.
--   * source_config, target_ontologies, and mapping_rules are all jsonb so
--     the shape can evolve without further migrations.
--   * No cross-schema FK — project_id is an opaque UUID because
--     pipeline-service owns projects/mappings but references data-service
--     sources via plain UUID as well.

CREATE TABLE IF NOT EXISTS pipeline.mappings (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    source_type VARCHAR(16) NOT NULL,
    source_config JSONB,
    target_namespace VARCHAR(1000),
    target_ontologies JSONB,
    mapping_rules JSONB,
    mapping_type VARCHAR(16) NOT NULL DEFAULT 'GENERIC',
    version INT NOT NULL DEFAULT 1,
    created_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_mappings_project_name UNIQUE (project_id, name)
);

CREATE INDEX IF NOT EXISTS idx_mappings_project_id ON pipeline.mappings(project_id);
CREATE INDEX IF NOT EXISTS idx_mappings_type ON pipeline.mappings(mapping_type);
