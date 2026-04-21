-- Update default Fuseki connection to use BASIC auth for write operations
UPDATE triplestore_connections
SET auth_type = 'BASIC',
    auth_config = '{"username":"admin","password":"admin"}'::jsonb
WHERE name = 'Local Fuseki' AND type = 'FUSEKI' AND auth_type = 'NONE';
