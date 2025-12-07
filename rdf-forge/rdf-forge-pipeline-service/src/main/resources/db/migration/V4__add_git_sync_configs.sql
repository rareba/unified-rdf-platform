-- Git Sync Configuration table
CREATE TABLE IF NOT EXISTS git_sync_configs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    project_id UUID,
    provider VARCHAR(50) NOT NULL,
    repository_url VARCHAR(1000) NOT NULL,
    branch VARCHAR(255) NOT NULL DEFAULT 'main',
    access_token VARCHAR(500) NOT NULL,
    config_path VARCHAR(500) DEFAULT 'config',
    sync_pipelines BOOLEAN DEFAULT TRUE,
    sync_shapes BOOLEAN DEFAULT TRUE,
    sync_settings BOOLEAN DEFAULT TRUE,
    auto_sync BOOLEAN DEFAULT FALSE,
    sync_interval_minutes INTEGER,
    last_sync_at TIMESTAMP,
    last_commit_sha VARCHAR(100),
    created_by UUID,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

-- Index for faster lookup by project
CREATE INDEX IF NOT EXISTS idx_git_sync_configs_project ON git_sync_configs(project_id);
CREATE INDEX IF NOT EXISTS idx_git_sync_configs_name ON git_sync_configs(name);
