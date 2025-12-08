-- RDF Forge Dimension Service - Add cubes table
-- Version: 4.0.0
-- Description: Creates the cubes table for RDF Data Cube management

-- Drop the incorrectly created table if it exists
DROP TABLE IF EXISTS cubes;

-- Cubes table
CREATE TABLE IF NOT EXISTS cubes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID,
    uri VARCHAR(500) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    source_data_id UUID,
    pipeline_id UUID,
    shape_id UUID,
    triplestore_id UUID,
    graph_uri VARCHAR(500),
    observation_count BIGINT,
    metadata JSONB,
    last_published TIMESTAMP WITH TIME ZONE,
    created_by UUID,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE
);

-- Indexes for cubes
CREATE INDEX IF NOT EXISTS idx_cubes_project_id ON cubes(project_id);
CREATE INDEX IF NOT EXISTS idx_cubes_name ON cubes(name);
CREATE INDEX IF NOT EXISTS idx_cubes_uri ON cubes(uri);
CREATE INDEX IF NOT EXISTS idx_cubes_pipeline_id ON cubes(pipeline_id);
CREATE INDEX IF NOT EXISTS idx_cubes_source_data_id ON cubes(source_data_id);

-- Comments for documentation
COMMENT ON TABLE cubes IS 'Stores RDF Data Cube configurations';
COMMENT ON COLUMN cubes.uri IS 'URI identifier for the cube';
COMMENT ON COLUMN cubes.observation_count IS 'Number of observations in the cube';
COMMENT ON COLUMN cubes.last_published IS 'Timestamp of last publication to triplestore';
