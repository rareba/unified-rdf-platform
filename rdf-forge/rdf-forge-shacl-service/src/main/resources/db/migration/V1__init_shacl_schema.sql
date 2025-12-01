-- SHACL Service Schema

CREATE TABLE IF NOT EXISTS shapes (
    id UUID PRIMARY KEY,
    project_id UUID,
    uri VARCHAR(500) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    content TEXT NOT NULL,
    format VARCHAR(50) DEFAULT 'TURTLE',
    category VARCHAR(100),
    is_template BOOLEAN DEFAULT FALSE,
    version INT NOT NULL DEFAULT 1,
    created_by UUID,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_shapes_project ON shapes(project_id);
CREATE INDEX IF NOT EXISTS idx_shapes_category ON shapes(category);
