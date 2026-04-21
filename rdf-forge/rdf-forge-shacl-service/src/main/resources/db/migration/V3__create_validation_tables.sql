-- RDF Forge SHACL Service - Validation Cockpit Tables (Phase 5)
-- Version: 2.0.0
-- Description: Adds project-scoped validation suites plus run/issue history
--              for the Validation Cockpit UI.

-- A named bundle of rules scoped to a single project.
CREATE TABLE IF NOT EXISTS validation_suites (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    rules JSONB NOT NULL DEFAULT '[]'::jsonb,
    release_gate VARCHAR(32) NOT NULL DEFAULT 'FAIL_ON_ERROR',
    created_by UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_validation_suites_project_name UNIQUE (project_id, name),
    CONSTRAINT ck_validation_suites_gate CHECK (
        release_gate IN ('DISABLED','WARN_ONLY','FAIL_ON_WARNING','FAIL_ON_ERROR','FAIL_ON_FATAL')
    )
);

CREATE INDEX IF NOT EXISTS idx_validation_suites_project
    ON validation_suites(project_id);

-- One row per suite execution.
CREATE TABLE IF NOT EXISTS validation_runs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    suite_id UUID NOT NULL,
    project_id UUID NOT NULL,
    ran_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    duration_ms BIGINT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL,
    issue_count INTEGER NOT NULL DEFAULT 0,
    error_count INTEGER NOT NULL DEFAULT 0,
    warning_count INTEGER NOT NULL DEFAULT 0,
    info_count INTEGER NOT NULL DEFAULT 0,
    fatal_count INTEGER NOT NULL DEFAULT 0,
    summary TEXT,
    context JSONB NOT NULL DEFAULT '{}'::jsonb,
    ran_by UUID,
    CONSTRAINT ck_validation_runs_status CHECK (
        status IN ('RUNNING','PASSED','FAILED','ERRORED')
    )
);

CREATE INDEX IF NOT EXISTS idx_validation_runs_project_ran_at
    ON validation_runs(project_id, ran_at DESC);
CREATE INDEX IF NOT EXISTS idx_validation_runs_suite_ran_at
    ON validation_runs(suite_id, ran_at DESC);

-- One row per finding in a run.
CREATE TABLE IF NOT EXISTS validation_issues (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id UUID NOT NULL,
    rule_id VARCHAR(255),
    severity VARCHAR(32) NOT NULL,
    resource_uri VARCHAR(2000),
    message TEXT,
    source_path VARCHAR(2000),
    details JSONB NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT ck_validation_issues_severity CHECK (
        severity IN ('INFO','WARNING','ERROR','FATAL')
    )
);

CREATE INDEX IF NOT EXISTS idx_validation_issues_run
    ON validation_issues(run_id);
CREATE INDEX IF NOT EXISTS idx_validation_issues_run_severity
    ON validation_issues(run_id, severity);

COMMENT ON TABLE validation_suites  IS 'Project-scoped validation suites (SHACL + SPARQL + cube profiles)';
COMMENT ON TABLE validation_runs    IS 'Execution history of validation suites';
COMMENT ON TABLE validation_issues  IS 'Per-finding rows produced by a run';
