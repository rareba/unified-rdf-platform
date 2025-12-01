-- RDF Forge Data Service - Initial Schema
-- Version: 1.0.0
-- Description: Creates the data source management tables

-- Data sources table
CREATE TABLE IF NOT EXISTS data_sources (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    original_filename VARCHAR(500) NOT NULL,
    storage_path VARCHAR(1000) NOT NULL,
    format VARCHAR(50) NOT NULL,
    encoding VARCHAR(50) DEFAULT 'UTF-8',
    size_bytes BIGINT,
    row_count BIGINT,
    column_count INTEGER,
    schema_info JSONB,
    sample_data JSONB,
    analysis_status VARCHAR(50) DEFAULT 'PENDING',
    checksum VARCHAR(64),
    metadata JSONB,
    created_by UUID,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Format check
ALTER TABLE data_sources ADD CONSTRAINT data_sources_format_check 
    CHECK (format IN ('CSV', 'TSV', 'JSON', 'JSONL', 'XLSX', 'PARQUET', 'XML', 'RDF'));

-- Analysis status check
ALTER TABLE data_sources ADD CONSTRAINT data_sources_analysis_status_check 
    CHECK (analysis_status IN ('PENDING', 'ANALYZING', 'COMPLETED', 'FAILED'));

-- Data source columns table
CREATE TABLE IF NOT EXISTS data_source_columns (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    data_source_id UUID NOT NULL REFERENCES data_sources(id) ON DELETE CASCADE,
    column_index INTEGER NOT NULL,
    column_name VARCHAR(255) NOT NULL,
    original_name VARCHAR(255),
    data_type VARCHAR(100),
    inferred_type VARCHAR(100),
    nullable BOOLEAN DEFAULT TRUE,
    unique_values BIGINT,
    null_count BIGINT,
    min_value TEXT,
    max_value TEXT,
    sample_values JSONB,
    statistics JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Column mappings table (for RDF mapping)
CREATE TABLE IF NOT EXISTS column_mappings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    data_source_id UUID NOT NULL REFERENCES data_sources(id) ON DELETE CASCADE,
    pipeline_id UUID,
    column_name VARCHAR(255) NOT NULL,
    predicate_uri VARCHAR(1000) NOT NULL,
    datatype_uri VARCHAR(1000),
    language_tag VARCHAR(10),
    is_identifier BOOLEAN DEFAULT FALSE,
    transform_expression TEXT,
    created_by UUID,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Data source access logs
CREATE TABLE IF NOT EXISTS data_source_access_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    data_source_id UUID NOT NULL REFERENCES data_sources(id) ON DELETE CASCADE,
    access_type VARCHAR(50) NOT NULL,
    user_id UUID,
    ip_address VARCHAR(45),
    user_agent TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Access type check
ALTER TABLE data_source_access_logs ADD CONSTRAINT data_source_access_logs_type_check 
    CHECK (access_type IN ('VIEW', 'DOWNLOAD', 'PREVIEW', 'ANALYZE', 'DELETE'));

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_data_sources_project_id ON data_sources(project_id);
CREATE INDEX IF NOT EXISTS idx_data_sources_format ON data_sources(format);
CREATE INDEX IF NOT EXISTS idx_data_sources_created_at ON data_sources(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_data_sources_name ON data_sources(name);

CREATE INDEX IF NOT EXISTS idx_data_source_columns_source_id ON data_source_columns(data_source_id);
CREATE INDEX IF NOT EXISTS idx_data_source_columns_name ON data_source_columns(column_name);

CREATE INDEX IF NOT EXISTS idx_column_mappings_source_id ON column_mappings(data_source_id);
CREATE INDEX IF NOT EXISTS idx_column_mappings_pipeline_id ON column_mappings(pipeline_id);

CREATE INDEX IF NOT EXISTS idx_data_source_access_logs_source_id ON data_source_access_logs(data_source_id);
CREATE INDEX IF NOT EXISTS idx_data_source_access_logs_created_at ON data_source_access_logs(created_at DESC);

-- Full-text search index
CREATE INDEX IF NOT EXISTS idx_data_sources_name_search 
    ON data_sources USING gin(to_tsvector('english', name || ' ' || COALESCE(description, '')));

-- Update trigger for data_sources
CREATE OR REPLACE FUNCTION update_data_sources_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_data_sources_updated_at
    BEFORE UPDATE ON data_sources
    FOR EACH ROW
    EXECUTE FUNCTION update_data_sources_updated_at();

-- Comments for documentation
COMMENT ON TABLE data_sources IS 'Stores uploaded data source files and their metadata';
COMMENT ON TABLE data_source_columns IS 'Stores column-level metadata for data sources';
COMMENT ON TABLE column_mappings IS 'Stores RDF predicate mappings for columns';
COMMENT ON TABLE data_source_access_logs IS 'Audit trail for data source access';

COMMENT ON COLUMN data_sources.storage_path IS 'Path in MinIO/S3 where the file is stored';
COMMENT ON COLUMN data_sources.schema_info IS 'JSON schema information extracted from the file';
COMMENT ON COLUMN data_sources.sample_data IS 'Sample rows for preview';
COMMENT ON COLUMN data_source_columns.inferred_type IS 'Automatically detected data type';
