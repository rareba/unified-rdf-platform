-- RDF Forge Database Initialization Script

CREATE DATABASE IF NOT EXISTS keycloak;

-- Users table (cached from Keycloak)
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    external_id VARCHAR(255) UNIQUE NOT NULL,
    email VARCHAR(255),
    name VARCHAR(255),
    roles JSONB DEFAULT '[]'::jsonb,
    created_at TIMESTAMP DEFAULT NOW(),
    last_login TIMESTAMP
);

-- Projects
CREATE TABLE IF NOT EXISTS projects (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    owner_id UUID REFERENCES users(id),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP
);

-- Pipelines
CREATE TABLE IF NOT EXISTS pipelines (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID REFERENCES projects(id),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    definition_format VARCHAR(20) DEFAULT 'yaml',
    definition TEXT NOT NULL,
    variables JSONB DEFAULT '{}'::jsonb,
    tags VARCHAR[] DEFAULT '{}',
    version INT DEFAULT 1,
    is_template BOOLEAN DEFAULT FALSE,
    created_by UUID REFERENCES users(id),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP
);

CREATE INDEX idx_pipelines_project ON pipelines(project_id);
CREATE INDEX idx_pipelines_name ON pipelines(name);

-- Pipeline versions
CREATE TABLE IF NOT EXISTS pipeline_versions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pipeline_id UUID REFERENCES pipelines(id) ON DELETE CASCADE,
    version INT NOT NULL,
    definition TEXT NOT NULL,
    change_message VARCHAR(500),
    created_by UUID REFERENCES users(id),
    created_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(pipeline_id, version)
);

-- SHACL Shapes
CREATE TABLE IF NOT EXISTS shapes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID REFERENCES projects(id),
    uri VARCHAR(500) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    target_class VARCHAR(500),
    content_format VARCHAR(20) DEFAULT 'turtle',
    content TEXT NOT NULL,
    is_template BOOLEAN DEFAULT FALSE,
    category VARCHAR(100),
    tags VARCHAR[] DEFAULT '{}',
    version INT DEFAULT 1,
    created_by UUID REFERENCES users(id),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP,
    UNIQUE(project_id, uri)
);

CREATE INDEX idx_shapes_project ON shapes(project_id);
CREATE INDEX idx_shapes_target_class ON shapes(target_class);

-- Shape versions
CREATE TABLE IF NOT EXISTS shape_versions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    shape_id UUID REFERENCES shapes(id) ON DELETE CASCADE,
    version INT NOT NULL,
    content TEXT NOT NULL,
    created_by UUID REFERENCES users(id),
    created_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(shape_id, version)
);

-- Data Sources
CREATE TABLE IF NOT EXISTS data_sources (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID REFERENCES projects(id),
    name VARCHAR(255) NOT NULL,
    original_filename VARCHAR(500),
    format VARCHAR(50),
    size_bytes BIGINT,
    storage_type VARCHAR(50) DEFAULT 's3',
    storage_path VARCHAR(1000),
    metadata JSONB,
    uploaded_by UUID REFERENCES users(id),
    uploaded_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_data_sources_project ON data_sources(project_id);

-- Jobs
CREATE TABLE IF NOT EXISTS jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pipeline_id UUID REFERENCES pipelines(id),
    pipeline_version INT,
    status VARCHAR(50) DEFAULT 'pending',
    priority INT DEFAULT 5,
    variables JSONB DEFAULT '{}'::jsonb,
    triggered_by VARCHAR(50) DEFAULT 'manual',
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    error_message TEXT,
    error_details JSONB,
    metrics JSONB,
    created_by UUID REFERENCES users(id),
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_jobs_status ON jobs(status);
CREATE INDEX idx_jobs_pipeline ON jobs(pipeline_id);
CREATE INDEX idx_jobs_created_at ON jobs(created_at DESC);

-- Job Logs
CREATE TABLE IF NOT EXISTS job_logs (
    id BIGSERIAL PRIMARY KEY,
    job_id UUID REFERENCES jobs(id) ON DELETE CASCADE,
    timestamp TIMESTAMP DEFAULT NOW(),
    level VARCHAR(20),
    step VARCHAR(255),
    message TEXT,
    details JSONB
);

CREATE INDEX idx_job_logs_job ON job_logs(job_id);
CREATE INDEX idx_job_logs_timestamp ON job_logs(job_id, timestamp);

-- Dimensions
CREATE TABLE IF NOT EXISTS dimensions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID REFERENCES projects(id),
    uri VARCHAR(500) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    type VARCHAR(50),
    hierarchy_type VARCHAR(50),
    content TEXT,
    version INT DEFAULT 1,
    created_by UUID REFERENCES users(id),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP,
    UNIQUE(project_id, uri)
);

CREATE INDEX idx_dimensions_project ON dimensions(project_id);

-- Triplestore Connections
CREATE TABLE IF NOT EXISTS triplestore_connections (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID REFERENCES projects(id),
    name VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL,
    url VARCHAR(1000) NOT NULL,
    default_graph VARCHAR(500),
    auth_type VARCHAR(50),
    auth_config JSONB,
    is_default BOOLEAN DEFAULT FALSE,
    health_status VARCHAR(50) DEFAULT 'unknown',
    last_health_check TIMESTAMP,
    created_by UUID REFERENCES users(id),
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_triplestore_connections_project ON triplestore_connections(project_id);

-- Cubes
CREATE TABLE IF NOT EXISTS cubes (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID REFERENCES projects(id),
    uri VARCHAR(500) NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    source_data_id UUID REFERENCES data_sources(id),
    pipeline_id UUID REFERENCES pipelines(id),
    shape_id UUID REFERENCES shapes(id),
    metadata JSONB,
    triplestore_id UUID REFERENCES triplestore_connections(id),
    graph_uri VARCHAR(500),
    observation_count BIGINT,
    last_published TIMESTAMP,
    created_by UUID REFERENCES users(id),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP
);

CREATE INDEX idx_cubes_project ON cubes(project_id);

-- Job Schedules
CREATE TABLE IF NOT EXISTS job_schedules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pipeline_id UUID REFERENCES pipelines(id) ON DELETE CASCADE,
    cron_expression VARCHAR(100) NOT NULL,
    variables JSONB DEFAULT '{}'::jsonb,
    is_active BOOLEAN DEFAULT TRUE,
    last_run TIMESTAMP,
    next_run TIMESTAMP,
    created_by UUID REFERENCES users(id),
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_job_schedules_pipeline ON job_schedules(pipeline_id);
CREATE INDEX idx_job_schedules_next_run ON job_schedules(next_run) WHERE is_active = TRUE;
