-- Cube Creator Redesign: Add project workflow fields
ALTER TABLE cubes ADD COLUMN IF NOT EXISTS status VARCHAR(50) DEFAULT 'draft';
ALTER TABLE cubes ADD COLUMN IF NOT EXISTS mappings_version INTEGER DEFAULT 0;
ALTER TABLE cubes ADD COLUMN IF NOT EXISTS csv_settings JSONB;

-- Index on status for filtering
CREATE INDEX IF NOT EXISTS idx_cubes_status ON cubes(status);

COMMENT ON COLUMN cubes.status IS 'Cube lifecycle: draft, mapped, transformed, published';
COMMENT ON COLUMN cubes.mappings_version IS 'Incremented on every columnMappings save, used for drift detection';
COMMENT ON COLUMN cubes.csv_settings IS 'CSV parsing settings: delimiter, encoding, quoteChar';
