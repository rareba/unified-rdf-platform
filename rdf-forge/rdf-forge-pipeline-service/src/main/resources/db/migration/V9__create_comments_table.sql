-- V9: Create comments table for inline discussion on semantic assets.
--
-- Scope:
--   * Single-instance (pipeline-service) hosting — comments here cover
--     every asset kind across services (ontology, shape, mapping, cube, …).
--     The decision to host in ONE service avoids duplicate controller
--     endpoints that would arise from auto-configuring the controller
--     via common.
--   * project_id is kept denormalised on every row so we can enforce
--     project-scoped authorisation without a cross-service join.
--   * parent_comment_id supports threaded replies. Deleting a parent
--     does NOT cascade — we prefer soft orphaning so history survives.
--   * Body is TEXT so lengthy review comments are allowed.

CREATE TABLE IF NOT EXISTS pipeline.comments (
    id UUID PRIMARY KEY,
    project_id UUID NOT NULL,
    asset_kind VARCHAR(32) NOT NULL,
    asset_id UUID NOT NULL,
    body TEXT NOT NULL,
    author_id UUID NOT NULL,
    author_email VARCHAR(255),
    parent_comment_id UUID,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE,
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_comments_asset
    ON pipeline.comments(asset_kind, asset_id)
    WHERE deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_comments_project
    ON pipeline.comments(project_id);

CREATE INDEX IF NOT EXISTS idx_comments_parent
    ON pipeline.comments(parent_comment_id)
    WHERE parent_comment_id IS NOT NULL;
