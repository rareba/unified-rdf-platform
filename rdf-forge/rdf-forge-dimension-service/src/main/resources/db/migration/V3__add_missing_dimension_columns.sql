-- RDF Forge Dimension Service - Add missing dimension columns
-- Version: 3.0.0
-- Description: Adds columns required by DimensionEntity that are missing

-- Add only truly missing columns
ALTER TABLE dimensions ADD COLUMN IF NOT EXISTS base_uri VARCHAR(500);
ALTER TABLE dimensions ADD COLUMN IF NOT EXISTS value_count BIGINT DEFAULT 0;
ALTER TABLE dimensions ADD COLUMN IF NOT EXISTS is_shared BOOLEAN DEFAULT FALSE;
ALTER TABLE dimensions ADD COLUMN IF NOT EXISTS metadata JSONB;
