-- RDF Forge Triplestore Service - Saved SPARQL Queries (Phase 7)
-- Version: 1.4.0
-- Description: Saved SPARQL queries for the Workbench 2.0 feature.

CREATE TABLE IF NOT EXISTS saved_queries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    type VARCHAR(32) NOT NULL,
    query_text TEXT NOT NULL,
    parameters JSONB,
    tags JSONB,
    created_by UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    run_count INTEGER NOT NULL DEFAULT 0,
    last_run TIMESTAMP WITH TIME ZONE,
    CONSTRAINT saved_queries_type_check CHECK (type IN ('ASK', 'SELECT', 'CONSTRUCT', 'DESCRIBE', 'UPDATE')),
    CONSTRAINT saved_queries_project_name_unique UNIQUE (project_id, name)
);

CREATE INDEX IF NOT EXISTS idx_saved_queries_project_id ON saved_queries(project_id);
CREATE INDEX IF NOT EXISTS idx_saved_queries_created_by ON saved_queries(created_by);
CREATE INDEX IF NOT EXISTS idx_saved_queries_type ON saved_queries(type);

CREATE TRIGGER trigger_saved_queries_updated_at
    BEFORE UPDATE ON saved_queries
    FOR EACH ROW
    EXECUTE FUNCTION update_triplestore_updated_at();

COMMENT ON TABLE saved_queries IS 'Saved SPARQL queries for the Phase 7 Workbench';
COMMENT ON COLUMN saved_queries.parameters IS 'JSON map: paramName -> { type, default }';
COMMENT ON COLUMN saved_queries.tags IS 'JSON array of tag strings';
