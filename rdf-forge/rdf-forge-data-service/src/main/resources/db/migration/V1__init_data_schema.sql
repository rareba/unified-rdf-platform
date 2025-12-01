-- Data Service Schema

CREATE TABLE IF NOT EXISTS data_sources (
    id UUID PRIMARY KEY,
    project_id UUID,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    file_name VARCHAR(255),
    file_path VARCHAR(500),
    file_size BIGINT,
    format VARCHAR(50),
    mime_type VARCHAR(100),
    encoding VARCHAR(50) DEFAULT 'UTF-8',
    delimiter VARCHAR(10),
    columns JSONB,
    sample_data JSONB,
    row_count BIGINT,
    created_by UUID,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_data_sources_project ON data_sources(project_id);
