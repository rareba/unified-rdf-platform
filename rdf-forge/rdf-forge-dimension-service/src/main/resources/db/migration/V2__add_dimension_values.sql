-- RDF Forge Dimension Service - Add dimension_values table
-- Version: 2.0.0
-- Description: Creates the dimension_values table for simple dimension values

-- Dimension values table (simplified from code_list_values)
CREATE TABLE IF NOT EXISTS dimension_values (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    dimension_id UUID NOT NULL REFERENCES dimensions(id) ON DELETE CASCADE,
    uri VARCHAR(500) NOT NULL,
    code VARCHAR(100) NOT NULL,
    label VARCHAR(500) NOT NULL,
    label_lang VARCHAR(10) DEFAULT 'en',
    description TEXT,
    parent_id UUID REFERENCES dimension_values(id),
    hierarchy_level INTEGER DEFAULT 0,
    sort_order INTEGER DEFAULT 0,
    metadata JSONB,
    alt_labels JSONB,
    skos_notation VARCHAR(100),
    is_deprecated BOOLEAN DEFAULT FALSE,
    replaced_by VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE
);

-- Indexes for dimension_values
CREATE INDEX IF NOT EXISTS idx_dim_values_dimension ON dimension_values(dimension_id);
CREATE INDEX IF NOT EXISTS idx_dim_values_code ON dimension_values(code);
CREATE INDEX IF NOT EXISTS idx_dim_values_uri ON dimension_values(uri);
CREATE INDEX IF NOT EXISTS idx_dim_values_parent ON dimension_values(parent_id);
