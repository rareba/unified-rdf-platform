-- Audit log table for tracking CRUD operations and security events
CREATE TABLE audit_log (
    id UUID PRIMARY KEY,
    user_id VARCHAR(255) NOT NULL,
    user_name VARCHAR(255),
    action VARCHAR(50) NOT NULL,
    entity_type VARCHAR(100) NOT NULL,
    entity_id VARCHAR(255),
    description VARCHAR(1000),
    before_values TEXT,
    after_values TEXT,
    changes TEXT,
    ip_address VARCHAR(45),
    user_agent VARCHAR(500),
    correlation_id VARCHAR(64),
    service_name VARCHAR(100),
    success BOOLEAN NOT NULL DEFAULT TRUE,
    error_message VARCHAR(2000),
    timestamp TIMESTAMP WITH TIME ZONE NOT NULL
);

-- Indexes for common query patterns
CREATE INDEX idx_audit_user_id ON audit_log(user_id);
CREATE INDEX idx_audit_entity ON audit_log(entity_type, entity_id);
CREATE INDEX idx_audit_timestamp ON audit_log(timestamp);
CREATE INDEX idx_audit_action ON audit_log(action);
CREATE INDEX idx_audit_correlation_id ON audit_log(correlation_id);
CREATE INDEX idx_audit_service ON audit_log(service_name);

-- Composite index for time-range queries with filters
CREATE INDEX idx_audit_time_action ON audit_log(timestamp, action);

-- Table for audit log metadata (key-value pairs)
CREATE TABLE audit_log_metadata (
    audit_log_id UUID NOT NULL REFERENCES audit_log(id) ON DELETE CASCADE,
    meta_key VARCHAR(100) NOT NULL,
    meta_value VARCHAR(500),
    PRIMARY KEY (audit_log_id, meta_key)
);

-- Index for metadata lookups
CREATE INDEX idx_audit_metadata_key ON audit_log_metadata(meta_key);

-- Comment on table and columns for documentation
COMMENT ON TABLE audit_log IS 'Stores audit trail of all CRUD operations and security-relevant events';
COMMENT ON COLUMN audit_log.action IS 'Type of action: CREATE, READ, UPDATE, DELETE, LOGIN, LOGOUT, etc.';
COMMENT ON COLUMN audit_log.entity_type IS 'Type of entity affected (e.g., Pipeline, Job, User)';
COMMENT ON COLUMN audit_log.before_values IS 'Entity state before action (JSON, sensitive data masked)';
COMMENT ON COLUMN audit_log.after_values IS 'Entity state after action (JSON, sensitive data masked)';
COMMENT ON COLUMN audit_log.correlation_id IS 'Request correlation ID for distributed tracing';
COMMENT ON COLUMN audit_log.success IS 'Whether the action completed successfully';
