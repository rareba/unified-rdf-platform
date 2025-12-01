-- RDF Forge Triplestore Service - Initial Schema
-- Version: 1.0.0
-- Description: Creates the triplestore connection and graph management tables

-- Triplestore connections table
CREATE TABLE IF NOT EXISTS triplestore_connections (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    endpoint_type VARCHAR(50) NOT NULL,
    query_endpoint VARCHAR(1000) NOT NULL,
    update_endpoint VARCHAR(1000),
    graph_store_endpoint VARCHAR(1000),
    username VARCHAR(255),
    password_encrypted TEXT,
    auth_type VARCHAR(50) DEFAULT 'BASIC',
    default_graph_uri VARCHAR(1000),
    max_connections INTEGER DEFAULT 5,
    connection_timeout_ms INTEGER DEFAULT 30000,
    read_timeout_ms INTEGER DEFAULT 60000,
    is_active BOOLEAN DEFAULT TRUE,
    last_health_check TIMESTAMP WITH TIME ZONE,
    health_status VARCHAR(50) DEFAULT 'UNKNOWN',
    metadata JSONB,
    created_by UUID,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Endpoint type check
ALTER TABLE triplestore_connections ADD CONSTRAINT triplestore_connections_endpoint_type_check 
    CHECK (endpoint_type IN ('FUSEKI', 'GRAPHDB', 'STARDOG', 'VIRTUOSO', 'BLAZEGRAPH', 'GENERIC'));

-- Auth type check
ALTER TABLE triplestore_connections ADD CONSTRAINT triplestore_connections_auth_type_check 
    CHECK (auth_type IN ('NONE', 'BASIC', 'BEARER', 'API_KEY'));

-- Health status check
ALTER TABLE triplestore_connections ADD CONSTRAINT triplestore_connections_health_status_check 
    CHECK (health_status IN ('UNKNOWN', 'HEALTHY', 'UNHEALTHY', 'DEGRADED'));

-- Named graphs table
CREATE TABLE IF NOT EXISTS named_graphs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    connection_id UUID NOT NULL REFERENCES triplestore_connections(id) ON DELETE CASCADE,
    graph_uri VARCHAR(1000) NOT NULL,
    name VARCHAR(255),
    description TEXT,
    triple_count BIGINT,
    last_modified TIMESTAMP WITH TIME ZONE,
    source_type VARCHAR(50),
    source_reference VARCHAR(1000),
    is_protected BOOLEAN DEFAULT FALSE,
    metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Source type check
ALTER TABLE named_graphs ADD CONSTRAINT named_graphs_source_type_check 
    CHECK (source_type IS NULL OR source_type IN ('PIPELINE', 'UPLOAD', 'IMPORT', 'EXTERNAL'));

-- Unique constraint for graph URI per connection
ALTER TABLE named_graphs ADD CONSTRAINT named_graphs_unique_uri 
    UNIQUE (connection_id, graph_uri);

-- SPARQL query history
CREATE TABLE IF NOT EXISTS sparql_query_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    connection_id UUID NOT NULL REFERENCES triplestore_connections(id) ON DELETE CASCADE,
    query_type VARCHAR(50) NOT NULL,
    query_text TEXT NOT NULL,
    target_graph_uri VARCHAR(1000),
    execution_time_ms BIGINT,
    result_count BIGINT,
    status VARCHAR(50) NOT NULL,
    error_message TEXT,
    user_id UUID,
    ip_address VARCHAR(45),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Query type check
ALTER TABLE sparql_query_history ADD CONSTRAINT sparql_query_history_query_type_check 
    CHECK (query_type IN ('SELECT', 'CONSTRUCT', 'ASK', 'DESCRIBE', 'INSERT', 'DELETE', 'UPDATE'));

-- Status check
ALTER TABLE sparql_query_history ADD CONSTRAINT sparql_query_history_status_check 
    CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED', 'TIMEOUT'));

-- Graph publish operations
CREATE TABLE IF NOT EXISTS graph_publish_operations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id UUID,
    connection_id UUID NOT NULL REFERENCES triplestore_connections(id) ON DELETE CASCADE,
    target_graph_uri VARCHAR(1000) NOT NULL,
    operation_type VARCHAR(50) NOT NULL,
    triple_count BIGINT,
    status VARCHAR(50) NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    error_message TEXT,
    metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Operation type check
ALTER TABLE graph_publish_operations ADD CONSTRAINT graph_publish_operations_type_check 
    CHECK (operation_type IN ('CREATE', 'REPLACE', 'APPEND', 'DELETE'));

-- Status check
ALTER TABLE graph_publish_operations ADD CONSTRAINT graph_publish_operations_status_check 
    CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED'));

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_triplestore_connections_project_id ON triplestore_connections(project_id);
CREATE INDEX IF NOT EXISTS idx_triplestore_connections_name ON triplestore_connections(name);
CREATE INDEX IF NOT EXISTS idx_triplestore_connections_active ON triplestore_connections(is_active) WHERE is_active = TRUE;
CREATE INDEX IF NOT EXISTS idx_triplestore_connections_health ON triplestore_connections(health_status);

CREATE INDEX IF NOT EXISTS idx_named_graphs_connection_id ON named_graphs(connection_id);
CREATE INDEX IF NOT EXISTS idx_named_graphs_uri ON named_graphs(graph_uri);

CREATE INDEX IF NOT EXISTS idx_sparql_query_history_connection_id ON sparql_query_history(connection_id);
CREATE INDEX IF NOT EXISTS idx_sparql_query_history_created_at ON sparql_query_history(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_sparql_query_history_user_id ON sparql_query_history(user_id);

CREATE INDEX IF NOT EXISTS idx_graph_publish_operations_job_id ON graph_publish_operations(job_id);
CREATE INDEX IF NOT EXISTS idx_graph_publish_operations_connection_id ON graph_publish_operations(connection_id);
CREATE INDEX IF NOT EXISTS idx_graph_publish_operations_status ON graph_publish_operations(status);

-- Update triggers
CREATE OR REPLACE FUNCTION update_triplestore_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_triplestore_connections_updated_at
    BEFORE UPDATE ON triplestore_connections
    FOR EACH ROW
    EXECUTE FUNCTION update_triplestore_updated_at();

CREATE TRIGGER trigger_named_graphs_updated_at
    BEFORE UPDATE ON named_graphs
    FOR EACH ROW
    EXECUTE FUNCTION update_triplestore_updated_at();

-- Comments for documentation
COMMENT ON TABLE triplestore_connections IS 'Stores SPARQL endpoint connection configurations';
COMMENT ON TABLE named_graphs IS 'Tracks named graphs in connected triplestores';
COMMENT ON TABLE sparql_query_history IS 'Audit log of SPARQL queries executed';
COMMENT ON TABLE graph_publish_operations IS 'Tracks graph publish/update operations';

COMMENT ON COLUMN triplestore_connections.password_encrypted IS 'AES-encrypted password for authentication';
COMMENT ON COLUMN triplestore_connections.graph_store_endpoint IS 'SPARQL Graph Store Protocol endpoint';
COMMENT ON COLUMN named_graphs.is_protected IS 'If true, prevents accidental deletion';
COMMENT ON COLUMN sparql_query_history.execution_time_ms IS 'Query execution time in milliseconds';
