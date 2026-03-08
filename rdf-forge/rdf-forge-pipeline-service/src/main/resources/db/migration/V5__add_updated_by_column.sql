-- Add updated_by column to pipelines table
ALTER TABLE pipelines ADD COLUMN IF NOT EXISTS updated_by UUID;

-- Create index for updated_by for better query performance
CREATE INDEX IF NOT EXISTS idx_pipelines_updated_by ON pipelines(updated_by);
