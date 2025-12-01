-- RDF Forge Job Service - Initial Schema
-- Version: 1.0.0
-- Description: Creates the job execution and logging tables

-- Jobs table
CREATE TABLE IF NOT EXISTS jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pipeline_id UUID NOT NULL,
    pipeline_version INTEGER,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    priority INTEGER DEFAULT 5 CHECK (priority >= 1 AND priority <= 10),
    dry_run BOOLEAN DEFAULT FALSE,
    variables JSONB,
    triggered_by VARCHAR(50) DEFAULT 'MANUAL',
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    error_message TEXT,
    error_details JSONB,
    metrics JSONB,
    created_by UUID,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Job status enum check
ALTER TABLE jobs ADD CONSTRAINT jobs_status_check 
    CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED'));

-- Trigger type check
ALTER TABLE jobs ADD CONSTRAINT jobs_triggered_by_check 
    CHECK (triggered_by IN ('MANUAL', 'SCHEDULE', 'API', 'WEBHOOK'));

-- Job logs table
CREATE TABLE IF NOT EXISTS job_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id UUID NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    step_id VARCHAR(255),
    level VARCHAR(20) NOT NULL DEFAULT 'INFO',
    message TEXT NOT NULL,
    metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Log level check
ALTER TABLE job_logs ADD CONSTRAINT job_logs_level_check 
    CHECK (level IN ('DEBUG', 'INFO', 'WARN', 'ERROR'));

-- Job step results table
CREATE TABLE IF NOT EXISTS job_step_results (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id UUID NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
    step_id VARCHAR(255) NOT NULL,
    step_name VARCHAR(255),
    status VARCHAR(50) NOT NULL,
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    input_records BIGINT,
    output_records BIGINT,
    error_message TEXT,
    metadata JSONB,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Step status check
ALTER TABLE job_step_results ADD CONSTRAINT job_step_results_status_check 
    CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED', 'SKIPPED'));

-- Job schedules table
CREATE TABLE IF NOT EXISTS job_schedules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    pipeline_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    cron_expression VARCHAR(100) NOT NULL,
    variables JSONB,
    enabled BOOLEAN DEFAULT TRUE,
    last_run_at TIMESTAMP WITH TIME ZONE,
    next_run_at TIMESTAMP WITH TIME ZONE,
    created_by UUID,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_jobs_pipeline_id ON jobs(pipeline_id);
CREATE INDEX IF NOT EXISTS idx_jobs_status ON jobs(status);
CREATE INDEX IF NOT EXISTS idx_jobs_created_at ON jobs(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_jobs_status_priority ON jobs(status, priority DESC) WHERE status = 'PENDING';

CREATE INDEX IF NOT EXISTS idx_job_logs_job_id ON job_logs(job_id);
CREATE INDEX IF NOT EXISTS idx_job_logs_created_at ON job_logs(created_at);
CREATE INDEX IF NOT EXISTS idx_job_logs_level ON job_logs(level);

CREATE INDEX IF NOT EXISTS idx_job_step_results_job_id ON job_step_results(job_id);

CREATE INDEX IF NOT EXISTS idx_job_schedules_pipeline_id ON job_schedules(pipeline_id);
CREATE INDEX IF NOT EXISTS idx_job_schedules_enabled_next_run ON job_schedules(enabled, next_run_at) WHERE enabled = TRUE;

-- Update trigger for jobs updated_at
CREATE OR REPLACE FUNCTION update_jobs_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_jobs_updated_at
    BEFORE UPDATE ON jobs
    FOR EACH ROW
    EXECUTE FUNCTION update_jobs_updated_at();

-- Update trigger for job_schedules updated_at
CREATE TRIGGER trigger_job_schedules_updated_at
    BEFORE UPDATE ON job_schedules
    FOR EACH ROW
    EXECUTE FUNCTION update_jobs_updated_at();

-- Comments for documentation
COMMENT ON TABLE jobs IS 'Stores job execution records for pipeline runs';
COMMENT ON TABLE job_logs IS 'Stores execution logs for each job';
COMMENT ON TABLE job_step_results IS 'Stores individual step results within a job';
COMMENT ON TABLE job_schedules IS 'Stores scheduled job configurations';

COMMENT ON COLUMN jobs.status IS 'Job status: PENDING, RUNNING, COMPLETED, FAILED, CANCELLED';
COMMENT ON COLUMN jobs.priority IS 'Job priority 1-10, higher values processed first';
COMMENT ON COLUMN jobs.dry_run IS 'If true, output operations are skipped';
COMMENT ON COLUMN jobs.metrics IS 'Job execution metrics as JSON';
