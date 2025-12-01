-- Job Service Schema

CREATE TABLE IF NOT EXISTS jobs (
    id UUID PRIMARY KEY,
    pipeline_id UUID NOT NULL,
    pipeline_version INT,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    priority INT DEFAULT 5,
    variables JSONB,
    triggered_by VARCHAR(50) DEFAULT 'MANUAL',
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    error_message TEXT,
    error_details JSONB,
    metrics JSONB,
    output_graph VARCHAR(500),
    created_by UUID,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS job_logs (
    id UUID PRIMARY KEY,
    job_id UUID NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    level VARCHAR(20) NOT NULL,
    message TEXT,
    details JSONB,
    timestamp TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_jobs_pipeline ON jobs(pipeline_id);
CREATE INDEX IF NOT EXISTS idx_jobs_status ON jobs(status);
CREATE INDEX IF NOT EXISTS idx_job_logs_job ON job_logs(job_id);
