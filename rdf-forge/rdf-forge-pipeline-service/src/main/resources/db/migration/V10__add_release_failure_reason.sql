-- V10: Add failure_reason column to releases table.
--
-- Rationale: ReleaseService must transition a release to FAILED when any
-- downstream asset fetch cannot be satisfied (401/403/404/5xx). The old
-- behaviour of stamping NOT_YET_FETCHED placeholders into the bundle while
-- still marking the release PUBLISHED has been removed — PUBLISHED now
-- means the bundle on disk is complete and honest. To communicate *why*
-- a build failed we persist a short human-readable reason here. It is
-- nullable: only populated on FAILED transitions.

ALTER TABLE pipeline.releases ADD COLUMN IF NOT EXISTS failure_reason TEXT;
