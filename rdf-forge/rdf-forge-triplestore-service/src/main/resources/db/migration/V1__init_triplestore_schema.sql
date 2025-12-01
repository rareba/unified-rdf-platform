-- Triplestore Service Schema

CREATE TABLE IF NOT EXISTS triplestore_connections (
    id UUID PRIMARY KEY,
    project_id UUID,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    type VARCHAR(50) NOT NULL DEFAULT 'FUSEKI',
    url VARCHAR(500) NOT NULL,
    update_url VARCHAR(500),
    username VARCHAR(255),
    password VARCHAR(255),
    auth_type VARCHAR(50) DEFAULT 'NONE',
    default_graph VARCHAR(500),
    properties JSONB,
    is_active BOOLEAN DEFAULT TRUE,
    last_check TIMESTAMP,
    status VARCHAR(50),
    created_by UUID,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_triplestore_project ON triplestore_connections(project_id);
