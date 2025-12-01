-- Dimension Service Schema

-- Dimensions table
CREATE TABLE IF NOT EXISTS dimensions (
    id UUID PRIMARY KEY,
    project_id UUID,
    uri VARCHAR(500) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    type VARCHAR(50) NOT NULL DEFAULT 'KEY',
    hierarchy_type VARCHAR(50) DEFAULT 'FLAT',
    content TEXT,
    base_uri VARCHAR(500),
    metadata JSONB,
    version INT NOT NULL DEFAULT 1,
    value_count BIGINT DEFAULT 0,
    is_shared BOOLEAN DEFAULT FALSE,
    created_by UUID,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,
    UNIQUE(project_id, uri)
);

CREATE INDEX IF NOT EXISTS idx_dimensions_project ON dimensions(project_id);
CREATE INDEX IF NOT EXISTS idx_dimensions_type ON dimensions(type);
CREATE INDEX IF NOT EXISTS idx_dimensions_shared ON dimensions(is_shared) WHERE is_shared = TRUE;

-- Dimension values table
CREATE TABLE IF NOT EXISTS dimension_values (
    id UUID PRIMARY KEY,
    dimension_id UUID NOT NULL REFERENCES dimensions(id) ON DELETE CASCADE,
    uri VARCHAR(500) NOT NULL,
    code VARCHAR(100) NOT NULL,
    label VARCHAR(500) NOT NULL,
    label_lang VARCHAR(10) DEFAULT 'en',
    description TEXT,
    parent_id UUID REFERENCES dimension_values(id),
    hierarchy_level INT DEFAULT 0,
    sort_order INT DEFAULT 0,
    metadata JSONB,
    alt_labels JSONB,
    skos_notation VARCHAR(100),
    is_deprecated BOOLEAN DEFAULT FALSE,
    replaced_by VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_dim_values_dimension ON dimension_values(dimension_id);
CREATE INDEX IF NOT EXISTS idx_dim_values_code ON dimension_values(code);
CREATE INDEX IF NOT EXISTS idx_dim_values_uri ON dimension_values(uri);
CREATE INDEX IF NOT EXISTS idx_dim_values_parent ON dimension_values(parent_id);
CREATE INDEX IF NOT EXISTS idx_dim_values_level ON dimension_values(dimension_id, hierarchy_level);

-- Hierarchies table
CREATE TABLE IF NOT EXISTS hierarchies (
    id UUID PRIMARY KEY,
    dimension_id UUID NOT NULL REFERENCES dimensions(id) ON DELETE CASCADE,
    uri VARCHAR(500) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    hierarchy_type VARCHAR(50) NOT NULL DEFAULT 'SKOS_CONCEPT_SCHEME',
    max_depth INT,
    root_concept_uri VARCHAR(500),
    skos_content TEXT,
    properties JSONB,
    is_default BOOLEAN DEFAULT FALSE,
    created_by UUID,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP,
    UNIQUE(dimension_id, uri)
);

CREATE INDEX IF NOT EXISTS idx_hierarchies_dimension ON hierarchies(dimension_id);
