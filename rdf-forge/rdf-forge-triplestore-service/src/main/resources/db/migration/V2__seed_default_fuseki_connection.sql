-- Seed default Fuseki connection pointing to the 'ds' dataset
-- Only inserts if no connections exist yet
INSERT INTO triplestore_connections (name, type, url, auth_type, is_default, health_status)
SELECT 'Local Fuseki', 'FUSEKI', 'http://fuseki:3030/ds', 'NONE', TRUE, 'UNKNOWN'
WHERE NOT EXISTS (SELECT 1 FROM triplestore_connections);
