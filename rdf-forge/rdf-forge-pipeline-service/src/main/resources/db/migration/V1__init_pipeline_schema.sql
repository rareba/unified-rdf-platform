-- Pipeline Service Schema

CREATE TABLE IF NOT EXISTS pipelines (
    id UUID PRIMARY KEY,
    project_id UUID,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    definition_format VARCHAR(50),
    definition TEXT NOT NULL,
    variables JSONB,
    tags JSONB,
    version INT NOT NULL DEFAULT 1,
    is_template BOOLEAN DEFAULT FALSE,
    created_by UUID,
    updated_by UUID,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_pipelines_project ON pipelines(project_id);
