-- RDF Forge Triplestore Service - Link Discovery / Reconciliation (Phase 8)
-- Version: 1.5.0
-- Description: Match candidate entities surfaced by matchers for review/approval.

CREATE TABLE IF NOT EXISTS match_candidates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL,
    source_uri VARCHAR(2000) NOT NULL,
    target_uri VARCHAR(2000) NOT NULL,
    predicate VARCHAR(32) NOT NULL,
    confidence DOUBLE PRECISION NOT NULL DEFAULT 0.0,
    source VARCHAR(32) NOT NULL,
    matcher_name VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    evidence JSONB,
    created_by UUID,
    approved_by UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    decided_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT match_candidates_predicate_check CHECK (
        predicate IN ('SAME_AS','EXACT_MATCH','CLOSE_MATCH','RELATED_MATCH','BROADER','NARROWER')
    ),
    CONSTRAINT match_candidates_source_check CHECK (
        source IN ('LOCAL_DUPLICATE','MANUAL','EXTERNAL_AUTHORITY')
    ),
    CONSTRAINT match_candidates_status_check CHECK (
        status IN ('PENDING','APPROVED','REJECTED','ARCHIVED')
    ),
    CONSTRAINT match_candidates_dedupe_unique UNIQUE (project_id, source_uri, target_uri, predicate)
);

CREATE INDEX IF NOT EXISTS idx_match_candidates_project_id ON match_candidates(project_id);
CREATE INDEX IF NOT EXISTS idx_match_candidates_status ON match_candidates(status);
CREATE INDEX IF NOT EXISTS idx_match_candidates_matcher ON match_candidates(matcher_name);
CREATE INDEX IF NOT EXISTS idx_match_candidates_source_uri ON match_candidates(source_uri);

CREATE TRIGGER trigger_match_candidates_updated_at
    BEFORE UPDATE ON match_candidates
    FOR EACH ROW
    EXECUTE FUNCTION update_triplestore_updated_at();

COMMENT ON TABLE match_candidates IS 'Match/link candidates for reconciliation (Phase 8)';
COMMENT ON COLUMN match_candidates.evidence IS 'Matcher-specific JSON evidence: scores, matched labels, provenance';
