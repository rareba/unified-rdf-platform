-- RDF Forge SHACL Service - Ontology Studio
-- Version: 2.0.0
-- Description: Creates the ontologies table used by the Ontology / Vocabulary Studio.

CREATE TABLE IF NOT EXISTS ontologies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    namespace VARCHAR(1000) NOT NULL,
    prefix VARCHAR(64),
    format VARCHAR(32) NOT NULL,
    content TEXT NOT NULL,
    version INTEGER NOT NULL DEFAULT 1,
    metadata JSONB,
    created_by UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_ontologies_project_name UNIQUE (project_id, name),
    CONSTRAINT ontologies_format_check
        CHECK (format IN ('TURTLE', 'RDF_XML', 'JSON_LD', 'N_TRIPLES', 'N_QUADS', 'TRIG'))
);

CREATE INDEX IF NOT EXISTS idx_ontologies_project_id ON ontologies(project_id);
CREATE INDEX IF NOT EXISTS idx_ontologies_created_by ON ontologies(created_by);
CREATE INDEX IF NOT EXISTS idx_ontologies_name_search
    ON ontologies USING gin(to_tsvector('english', name || ' ' || COALESCE(description, '')));

-- Reuse the updated_at trigger pattern from V1 (function already exists).
CREATE OR REPLACE FUNCTION update_ontologies_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_ontologies_updated_at
    BEFORE UPDATE ON ontologies
    FOR EACH ROW
    EXECUTE FUNCTION update_ontologies_updated_at();

COMMENT ON TABLE ontologies IS 'Stores project-scoped ontology / vocabulary documents (Turtle, RDF/XML, JSON-LD, etc.)';
COMMENT ON COLUMN ontologies.namespace IS 'Base IRI for this ontology (e.g. http://example.org/schema/)';
COMMENT ON COLUMN ontologies.prefix IS 'Recommended short prefix for the base namespace';
COMMENT ON COLUMN ontologies.content IS 'Serialized RDF content in the declared format';
