-- RDF Forge SHACL Service - Initial Schema
-- Version: 1.0.0
-- Description: Creates the SHACL shapes and validation tables

-- SHACL Shapes table
CREATE TABLE IF NOT EXISTS shapes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID,
    uri VARCHAR(1000),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    target_class VARCHAR(1000),
    content_format VARCHAR(50) DEFAULT 'TURTLE',
    content TEXT NOT NULL,
    is_template BOOLEAN DEFAULT FALSE,
    category VARCHAR(100),
    tags TEXT[],
    version INTEGER DEFAULT 1,
    created_by UUID,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Content format check
ALTER TABLE shapes ADD CONSTRAINT shapes_content_format_check 
    CHECK (content_format IN ('TURTLE', 'JSON_LD', 'RDF_XML', 'N_TRIPLES'));

-- Validation results table
CREATE TABLE IF NOT EXISTS validation_results (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id UUID,
    shape_id UUID REFERENCES shapes(id),
    data_source_id UUID,
    conforms BOOLEAN NOT NULL,
    result_count INTEGER DEFAULT 0,
    error_count INTEGER DEFAULT 0,
    warning_count INTEGER DEFAULT 0,
    info_count INTEGER DEFAULT 0,
    report_content TEXT,
    report_format VARCHAR(50) DEFAULT 'TURTLE',
    execution_time_ms BIGINT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Individual validation violations
CREATE TABLE IF NOT EXISTS validation_violations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    validation_result_id UUID NOT NULL REFERENCES validation_results(id) ON DELETE CASCADE,
    severity VARCHAR(20) NOT NULL,
    focus_node VARCHAR(1000),
    result_path VARCHAR(1000),
    source_constraint VARCHAR(1000),
    source_shape VARCHAR(1000),
    message TEXT,
    value TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Severity check
ALTER TABLE validation_violations ADD CONSTRAINT validation_violations_severity_check 
    CHECK (severity IN ('VIOLATION', 'WARNING', 'INFO'));

-- Shape versions for auditing
CREATE TABLE IF NOT EXISTS shape_versions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    shape_id UUID NOT NULL REFERENCES shapes(id) ON DELETE CASCADE,
    version INTEGER NOT NULL,
    content TEXT NOT NULL,
    content_format VARCHAR(50) NOT NULL,
    changed_by UUID,
    change_reason TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_shapes_project_id ON shapes(project_id);
CREATE INDEX IF NOT EXISTS idx_shapes_name ON shapes(name);
CREATE INDEX IF NOT EXISTS idx_shapes_category ON shapes(category);
CREATE INDEX IF NOT EXISTS idx_shapes_is_template ON shapes(is_template) WHERE is_template = TRUE;
CREATE INDEX IF NOT EXISTS idx_shapes_target_class ON shapes(target_class);

CREATE INDEX IF NOT EXISTS idx_validation_results_job_id ON validation_results(job_id);
CREATE INDEX IF NOT EXISTS idx_validation_results_shape_id ON validation_results(shape_id);
CREATE INDEX IF NOT EXISTS idx_validation_results_conforms ON validation_results(conforms);
CREATE INDEX IF NOT EXISTS idx_validation_results_created_at ON validation_results(created_at DESC);

CREATE INDEX IF NOT EXISTS idx_validation_violations_result_id ON validation_violations(validation_result_id);
CREATE INDEX IF NOT EXISTS idx_validation_violations_severity ON validation_violations(severity);

CREATE INDEX IF NOT EXISTS idx_shape_versions_shape_id ON shape_versions(shape_id);

-- Full-text search index for shape names and descriptions
CREATE INDEX IF NOT EXISTS idx_shapes_name_search ON shapes USING gin(to_tsvector('english', name || ' ' || COALESCE(description, '')));

-- Update trigger for shapes
CREATE OR REPLACE FUNCTION update_shapes_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_shapes_updated_at
    BEFORE UPDATE ON shapes
    FOR EACH ROW
    EXECUTE FUNCTION update_shapes_updated_at();

-- Trigger to save shape versions on update
CREATE OR REPLACE FUNCTION save_shape_version()
RETURNS TRIGGER AS $$
BEGIN
    IF OLD.content IS DISTINCT FROM NEW.content THEN
        INSERT INTO shape_versions (shape_id, version, content, content_format, changed_by)
        VALUES (OLD.id, OLD.version, OLD.content, OLD.content_format, NEW.created_by);
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_save_shape_version
    BEFORE UPDATE ON shapes
    FOR EACH ROW
    EXECUTE FUNCTION save_shape_version();

-- Comments for documentation
COMMENT ON TABLE shapes IS 'Stores SHACL shape definitions for RDF validation';
COMMENT ON TABLE validation_results IS 'Stores validation execution results';
COMMENT ON TABLE validation_violations IS 'Stores individual violations from validation runs';
COMMENT ON TABLE shape_versions IS 'Audit trail for shape content changes';

COMMENT ON COLUMN shapes.uri IS 'The URI identifier for the shape in RDF';
COMMENT ON COLUMN shapes.target_class IS 'The target class URI this shape validates';
COMMENT ON COLUMN shapes.is_template IS 'If true, this shape is a reusable template';
COMMENT ON COLUMN validation_results.conforms IS 'True if data conforms to shape constraints';
