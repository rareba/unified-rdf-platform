-- Personal Access Tokens table
CREATE TABLE IF NOT EXISTS personal_access_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(500),
    token_hash VARCHAR(64) NOT NULL UNIQUE,
    token_prefix VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE,
    last_used_at TIMESTAMP WITH TIME ZONE,
    last_used_ip VARCHAR(45),
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    revoked_at TIMESTAMP WITH TIME ZONE
);

-- Scopes table for token permissions
CREATE TABLE IF NOT EXISTS personal_access_token_scopes (
    token_id UUID NOT NULL REFERENCES personal_access_tokens(id) ON DELETE CASCADE,
    scope VARCHAR(100) NOT NULL,
    PRIMARY KEY (token_id, scope)
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_pat_user_id ON personal_access_tokens(user_id);
CREATE INDEX IF NOT EXISTS idx_pat_token_hash ON personal_access_tokens(token_hash);
CREATE INDEX IF NOT EXISTS idx_pat_user_revoked ON personal_access_tokens(user_id, revoked);
CREATE INDEX IF NOT EXISTS idx_pat_expires_at ON personal_access_tokens(expires_at) WHERE expires_at IS NOT NULL;

-- Comments
COMMENT ON TABLE personal_access_tokens IS 'Personal Access Tokens for API authentication';
COMMENT ON COLUMN personal_access_tokens.token_hash IS 'SHA-256 hash of the token';
COMMENT ON COLUMN personal_access_tokens.token_prefix IS 'Visible prefix for token identification (e.g., ccx_abc123...)';
COMMENT ON COLUMN personal_access_tokens.expires_at IS 'NULL means never expires';
