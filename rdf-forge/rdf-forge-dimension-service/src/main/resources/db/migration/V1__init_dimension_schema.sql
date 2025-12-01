-- RDF Forge Dimension Service - Initial Schema
-- Version: 1.0.0
-- Description: Creates the dimension and hierarchy management tables

-- Dimensions table
CREATE TABLE IF NOT EXISTS dimensions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID,
    uri VARCHAR(1000) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    dimension_type VARCHAR(50) NOT NULL,
    scale_type VARCHAR(50),
    unit_uri VARCHAR(1000),
    unit_label VARCHAR(255),
    is_key_dimension BOOLEAN DEFAULT FALSE,
    is_measure BOOLEAN DEFAULT FALSE,
    order_position INTEGER,
    metadata JSONB,
    created_by UUID,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Dimension type check
ALTER TABLE dimensions ADD CONSTRAINT dimensions_type_check 
    CHECK (dimension_type IN ('TEMPORAL', 'SPATIAL', 'CATEGORICAL', 'NUMERIC', 'MEASURE', 'ATTRIBUTE'));

-- Scale type check
ALTER TABLE dimensions ADD CONSTRAINT dimensions_scale_type_check 
    CHECK (scale_type IS NULL OR scale_type IN ('NOMINAL', 'ORDINAL', 'INTERVAL', 'RATIO'));

-- Code lists table
CREATE TABLE IF NOT EXISTS code_lists (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dimension_id UUID NOT NULL REFERENCES dimensions(id) ON DELETE CASCADE,
    uri VARCHAR(1000) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    version VARCHAR(50),
    source_url VARCHAR(1000),
    is_hierarchical BOOLEAN DEFAULT FALSE,
    metadata JSONB,
    created_by UUID,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Code list values
CREATE TABLE IF NOT EXISTS code_list_values (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code_list_id UUID NOT NULL REFERENCES code_lists(id) ON DELETE CASCADE,
    uri VARCHAR(1000) NOT NULL,
    notation VARCHAR(255),
    label_en VARCHAR(500),
    label_de VARCHAR(500),
    label_fr VARCHAR(500),
    label_it VARCHAR(500),
    definition TEXT,
    parent_id UUID REFERENCES code_list_values(id),
    order_position INTEGER,
    deprecated BOOLEAN DEFAULT FALSE,
    valid_from DATE,
    valid_to DATE,
    metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Hierarchies table
CREATE TABLE IF NOT EXISTS hierarchies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dimension_id UUID NOT NULL REFERENCES dimensions(id) ON DELETE CASCADE,
    uri VARCHAR(1000) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    hierarchy_type VARCHAR(50) NOT NULL,
    max_depth INTEGER,
    metadata JSONB,
    created_by UUID,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Hierarchy type check
ALTER TABLE hierarchies ADD CONSTRAINT hierarchies_type_check 
    CHECK (hierarchy_type IN ('SIMPLE', 'RAGGED', 'BALANCED', 'PARALLEL'));

-- Hierarchy levels
CREATE TABLE IF NOT EXISTS hierarchy_levels (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    hierarchy_id UUID NOT NULL REFERENCES hierarchies(id) ON DELETE CASCADE,
    level_number INTEGER NOT NULL,
    name VARCHAR(255) NOT NULL,
    uri VARCHAR(1000),
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Dimension mappings (for cube creation)
CREATE TABLE IF NOT EXISTS dimension_mappings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dimension_id UUID NOT NULL REFERENCES dimensions(id) ON DELETE CASCADE,
    data_source_id UUID,
    pipeline_id UUID,
    source_column VARCHAR(255) NOT NULL,
    mapping_type VARCHAR(50) NOT NULL,
    transform_expression TEXT,
    default_value TEXT,
    created_by UUID,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Mapping type check
ALTER TABLE dimension_mappings ADD CONSTRAINT dimension_mappings_type_check 
    CHECK (mapping_type IN ('DIRECT', 'LOOKUP', 'EXPRESSION', 'CONSTANT'));

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_dimensions_project_id ON dimensions(project_id);
CREATE INDEX IF NOT EXISTS idx_dimensions_uri ON dimensions(uri);
CREATE INDEX IF NOT EXISTS idx_dimensions_type ON dimensions(dimension_type);
CREATE INDEX IF NOT EXISTS idx_dimensions_name ON dimensions(name);

CREATE INDEX IF NOT EXISTS idx_code_lists_dimension_id ON code_lists(dimension_id);
CREATE INDEX IF NOT EXISTS idx_code_lists_uri ON code_lists(uri);

CREATE INDEX IF NOT EXISTS idx_code_list_values_code_list_id ON code_list_values(code_list_id);
CREATE INDEX IF NOT EXISTS idx_code_list_values_uri ON code_list_values(uri);
CREATE INDEX IF NOT EXISTS idx_code_list_values_notation ON code_list_values(notation);
CREATE INDEX IF NOT EXISTS idx_code_list_values_parent_id ON code_list_values(parent_id);

CREATE INDEX IF NOT EXISTS idx_hierarchies_dimension_id ON hierarchies(dimension_id);

CREATE INDEX IF NOT EXISTS idx_hierarchy_levels_hierarchy_id ON hierarchy_levels(hierarchy_id);

CREATE INDEX IF NOT EXISTS idx_dimension_mappings_dimension_id ON dimension_mappings(dimension_id);
CREATE INDEX IF NOT EXISTS idx_dimension_mappings_data_source_id ON dimension_mappings(data_source_id);

-- Full-text search for dimensions
CREATE INDEX IF NOT EXISTS idx_dimensions_search 
    ON dimensions USING gin(to_tsvector('english', name || ' ' || COALESCE(description, '')));

-- Full-text search for code list values
CREATE INDEX IF NOT EXISTS idx_code_list_values_search 
    ON code_list_values USING gin(to_tsvector('english', 
        COALESCE(label_en, '') || ' ' || COALESCE(label_de, '') || ' ' || 
        COALESCE(label_fr, '') || ' ' || COALESCE(notation, '')));

-- Update triggers
CREATE OR REPLACE FUNCTION update_dimensions_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_dimensions_updated_at
    BEFORE UPDATE ON dimensions
    FOR EACH ROW
    EXECUTE FUNCTION update_dimensions_updated_at();

CREATE TRIGGER trigger_code_lists_updated_at
    BEFORE UPDATE ON code_lists
    FOR EACH ROW
    EXECUTE FUNCTION update_dimensions_updated_at();

CREATE TRIGGER trigger_hierarchies_updated_at
    BEFORE UPDATE ON hierarchies
    FOR EACH ROW
    EXECUTE FUNCTION update_dimensions_updated_at();

-- Comments for documentation
COMMENT ON TABLE dimensions IS 'Stores statistical dimensions for data cubes';
COMMENT ON TABLE code_lists IS 'Stores code lists (controlled vocabularies) for dimensions';
COMMENT ON TABLE code_list_values IS 'Individual values in a code list';
COMMENT ON TABLE hierarchies IS 'Hierarchical relationships within dimensions';
COMMENT ON TABLE hierarchy_levels IS 'Levels within a hierarchy';
COMMENT ON TABLE dimension_mappings IS 'Mappings from data sources to dimensions';

COMMENT ON COLUMN dimensions.is_key_dimension IS 'If true, this dimension is part of the observation key';
COMMENT ON COLUMN dimensions.is_measure IS 'If true, this dimension contains measured values';
COMMENT ON COLUMN code_list_values.notation IS 'Short code for the value';
