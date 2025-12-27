-- V5: Add hierarchy support columns to dimensions table
-- Supports meta:inHierarchy and meta:nextInHierarchy from cube-link

ALTER TABLE dimensions ADD COLUMN IF NOT EXISTS parent_dimension_id UUID;
ALTER TABLE dimensions ADD COLUMN IF NOT EXISTS hierarchy_level INTEGER DEFAULT 0;
ALTER TABLE dimensions ADD COLUMN IF NOT EXISTS hierarchy_name VARCHAR(255);

-- Index for hierarchy queries
CREATE INDEX IF NOT EXISTS idx_dimensions_parent ON dimensions(parent_dimension_id);
CREATE INDEX IF NOT EXISTS idx_dimensions_hierarchy ON dimensions(hierarchy_name);

-- Add foreign key constraint (self-referencing)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints 
        WHERE constraint_name = 'fk_dimensions_parent'
    ) THEN
        ALTER TABLE dimensions 
        ADD CONSTRAINT fk_dimensions_parent 
        FOREIGN KEY (parent_dimension_id) REFERENCES dimensions(id) ON DELETE SET NULL;
    END IF;
END $$;
