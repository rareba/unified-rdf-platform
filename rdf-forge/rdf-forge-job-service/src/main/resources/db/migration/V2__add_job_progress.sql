-- Migration: Add progress and output_graph columns to jobs table
-- Description: Adds progress tracking and output graph URI for job execution

ALTER TABLE jobs ADD COLUMN IF NOT EXISTS progress INTEGER DEFAULT 0 CHECK (progress >= 0 AND progress <= 100);
ALTER TABLE jobs ADD COLUMN IF NOT EXISTS output_graph VARCHAR(500);

COMMENT ON COLUMN jobs.progress IS 'Job execution progress percentage (0-100)';
COMMENT ON COLUMN jobs.output_graph IS 'URI of the output graph where results are stored';
