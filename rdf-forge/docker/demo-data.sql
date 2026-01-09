-- =============================================================================
-- RDF Forge - Demo Data for Standalone Mode
-- =============================================================================
-- This script populates the database with sample data for demonstration purposes
-- Run after init-db.sql in standalone mode
-- =============================================================================

-- Demo Users (for standalone/noauth mode)
INSERT INTO users (id, external_id, email, name, roles, created_at, last_login) VALUES
('00000000-0000-0000-0000-000000000001', 'demo-admin', 'admin@example.com', 'Demo Admin', '["admin"]'::jsonb, NOW(), NOW()),
('00000000-0000-0000-0000-000000000002', 'demo-editor', 'editor@example.com', 'Demo Editor', '["editor"]'::jsonb, NOW(), NOW()),
('00000000-0000-0000-0000-000000000003', 'demo-viewer', 'viewer@example.com', 'Demo Viewer', '["viewer"]'::jsonb, NOW(), NOW())
ON CONFLICT (external_id) DO NOTHING;

-- Demo Project
INSERT INTO projects (id, name, description, owner_id, created_at) VALUES
('11111111-1111-1111-1111-111111111111', 'Swiss Statistics Demo', 'Demo project showcasing Swiss statistical data cubes', '00000000-0000-0000-0000-000000000001', NOW())
ON CONFLICT (id) DO NOTHING;

-- Demo Pipelines
INSERT INTO pipelines (id, project_id, name, description, definition, definition_format, tags, created_by, created_at) VALUES
('22222222-2222-2222-2222-222222222221', '11111111-1111-1111-1111-111111111111',
 'Swiss Population Pipeline',
 'Pipeline to transform Swiss population data by canton into RDF Data Cube format',
 '{
  "name": "swiss-population",
  "steps": [
    {
      "operation": "op:load-csv",
      "params": { "source": "swiss-population-by-canton.csv", "hasHeader": true }
    },
    {
      "operation": "op:map-to-rdf",
      "params": {
        "baseUri": "https://example.org/swiss-stats/",
        "mappings": [
          {"column": "canton_code", "role": "dimension", "dimensionUri": "https://example.org/dimension/canton"},
          {"column": "year", "role": "dimension", "datatype": "xsd:gYear"},
          {"column": "population", "role": "measure", "datatype": "xsd:integer"}
        ]
      }
    },
    {
      "operation": "op:graph-store-put",
      "params": { "graph": "https://example.org/graph/swiss-population" }
    }
  ]
}', 'JSON', ARRAY['statistics', 'population', 'demo'], '00000000-0000-0000-0000-000000000001', NOW()),

('22222222-2222-2222-2222-222222222222', '11111111-1111-1111-1111-111111111111',
 'Employment Statistics Pipeline',
 'Pipeline to process Swiss employment statistics by canton and sector',
 '{
  "name": "swiss-employment",
  "steps": [
    {
      "operation": "op:load-csv",
      "params": { "source": "swiss-employment-statistics.csv", "hasHeader": true }
    },
    {
      "operation": "op:map-to-rdf",
      "params": {
        "baseUri": "https://example.org/swiss-stats/",
        "mappings": [
          {"column": "canton_code", "role": "dimension"},
          {"column": "year", "role": "dimension", "datatype": "xsd:gYear"},
          {"column": "quarter", "role": "dimension"},
          {"column": "sector", "role": "dimension"},
          {"column": "employment_count", "role": "measure", "datatype": "xsd:integer"},
          {"column": "unemployment_rate", "role": "measure", "datatype": "xsd:decimal"}
        ]
      }
    },
    {
      "operation": "op:graph-store-put",
      "params": { "graph": "https://example.org/graph/swiss-employment" }
    }
  ]
}', 'JSON', ARRAY['statistics', 'employment', 'demo'], '00000000-0000-0000-0000-000000000001', NOW()),

('22222222-2222-2222-2222-222222222223', '11111111-1111-1111-1111-111111111111',
 'GDP by Sector Pipeline',
 'Pipeline to process Swiss GDP data by economic sector',
 '{
  "name": "swiss-gdp",
  "steps": [
    {
      "operation": "op:load-csv",
      "params": { "source": "swiss-gdp-by-sector.csv", "hasHeader": true }
    },
    {
      "operation": "op:transform",
      "params": { "script": "normalize-sectors.js" }
    },
    {
      "operation": "op:map-to-rdf",
      "params": {
        "baseUri": "https://example.org/swiss-stats/",
        "mappings": [
          {"column": "year", "role": "dimension", "datatype": "xsd:gYear"},
          {"column": "sector", "role": "dimension"},
          {"column": "subsector", "role": "dimension"},
          {"column": "gdp_millions_chf", "role": "measure", "datatype": "xsd:decimal"},
          {"column": "growth_rate_pct", "role": "measure", "datatype": "xsd:decimal"},
          {"column": "employment_share_pct", "role": "attribute", "datatype": "xsd:decimal"}
        ]
      }
    },
    {
      "operation": "op:graph-store-put",
      "params": { "graph": "https://example.org/graph/swiss-gdp" }
    }
  ]
}', 'JSON', ARRAY['statistics', 'economy', 'gdp', 'demo'], '00000000-0000-0000-0000-000000000001', NOW()),

('22222222-2222-2222-2222-222222222224', '11111111-1111-1111-1111-111111111111',
 'SHACL Validation Pipeline',
 'Pipeline to validate RDF data against SHACL shapes',
 '{
  "name": "shacl-validation",
  "steps": [
    {
      "operation": "op:sparql-construct",
      "params": {
        "query": "CONSTRUCT { ?s ?p ?o } WHERE { GRAPH ?g { ?s ?p ?o } }",
        "source": "graphdb"
      }
    },
    {
      "operation": "op:shacl-validate",
      "params": { "shapes": "data-cube-shape" }
    },
    {
      "operation": "op:report",
      "params": { "format": "html" }
    }
  ]
}', 'JSON', ARRAY['validation', 'shacl', 'demo'], '00000000-0000-0000-0000-000000000001', NOW()),

('22222222-2222-2222-2222-222222222225', '11111111-1111-1111-1111-111111111111',
 'Full ETL Pipeline',
 'Complete ETL pipeline: Load, Transform, Validate, Publish',
 '{
  "name": "full-etl",
  "steps": [
    {
      "operation": "op:load-csv",
      "params": { "source": "${sourceFile}", "hasHeader": true }
    },
    {
      "operation": "op:map-to-rdf",
      "params": { "mapping": "${mappingConfig}" }
    },
    {
      "operation": "op:shacl-validate",
      "params": { "shapes": "${validationShapes}" }
    },
    {
      "operation": "op:graph-store-put",
      "params": {
        "graph": "${targetGraph}",
        "endpoint": "${targetEndpoint}"
      }
    }
  ],
  "variables": {
    "sourceFile": { "type": "string", "required": true },
    "mappingConfig": { "type": "object", "required": true },
    "validationShapes": { "type": "string", "required": false },
    "targetGraph": { "type": "uri", "required": true },
    "targetEndpoint": { "type": "uri", "default": "http://graphdb:7200" }
  }
}', 'JSON', ARRAY['etl', 'template', 'demo'], '00000000-0000-0000-0000-000000000001', NOW())
ON CONFLICT (id) DO NOTHING;

-- Demo SHACL Shapes
INSERT INTO shapes (id, project_id, uri, name, description, target_class, content_format, content, category, tags, created_by, created_at) VALUES
('33333333-3333-3333-3333-333333333331', '11111111-1111-1111-1111-111111111111',
 'https://example.org/shapes/DataCubeShape',
 'RDF Data Cube Validation',
 'SHACL shape for validating RDF Data Cube datasets according to the QB vocabulary',
 'http://purl.org/linked-data/cube#DataSet',
 'TURTLE',
 '@prefix sh: <http://www.w3.org/ns/shacl#> .
@prefix qb: <http://purl.org/linked-data/cube#> .
@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
@prefix ex: <https://example.org/shapes/> .

ex:DataCubeShape a sh:NodeShape ;
    sh:targetClass qb:DataSet ;
    sh:name "Data Cube Dataset Shape" ;
    sh:description "Validates RDF Data Cube datasets" ;

    sh:property [
        sh:path rdfs:label ;
        sh:minCount 1 ;
        sh:datatype xsd:string ;
        sh:message "Dataset must have at least one label"
    ] ;

    sh:property [
        sh:path qb:structure ;
        sh:minCount 1 ;
        sh:maxCount 1 ;
        sh:class qb:DataStructureDefinition ;
        sh:message "Dataset must reference exactly one Data Structure Definition"
    ] .

ex:ObservationShape a sh:NodeShape ;
    sh:targetClass qb:Observation ;
    sh:name "Observation Shape" ;
    sh:description "Validates individual observations in a data cube" ;

    sh:property [
        sh:path qb:dataSet ;
        sh:minCount 1 ;
        sh:maxCount 1 ;
        sh:class qb:DataSet ;
        sh:message "Observation must belong to exactly one dataset"
    ] .',
 'Data Cube', ARRAY['cube', 'validation', 'qb'], '00000000-0000-0000-0000-000000000001', NOW()),

('33333333-3333-3333-3333-333333333332', '11111111-1111-1111-1111-111111111111',
 'https://example.org/shapes/CantonShape',
 'Swiss Canton Validation',
 'SHACL shape for validating Swiss canton dimension values',
 'https://example.org/dimension/Canton',
 'TURTLE',
 '@prefix sh: <http://www.w3.org/ns/shacl#> .
@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
@prefix skos: <http://www.w3.org/2004/02/skos/core#> .
@prefix ex: <https://example.org/shapes/> .

ex:CantonShape a sh:NodeShape ;
    sh:targetClass <https://example.org/dimension/Canton> ;
    sh:name "Swiss Canton Shape" ;
    sh:description "Validates Swiss canton dimension values" ;

    sh:property [
        sh:path skos:notation ;
        sh:minCount 1 ;
        sh:maxCount 1 ;
        sh:datatype xsd:string ;
        sh:minLength 2 ;
        sh:maxLength 2 ;
        sh:pattern "^[A-Z]{2}$" ;
        sh:message "Canton code must be exactly 2 uppercase letters"
    ] ;

    sh:property [
        sh:path skos:prefLabel ;
        sh:minCount 1 ;
        sh:uniqueLang true ;
        sh:message "Canton must have labels, unique per language"
    ] ;

    sh:property [
        sh:path skos:inScheme ;
        sh:minCount 1 ;
        sh:message "Canton must belong to a concept scheme"
    ] .',
 'Dimension', ARRAY['dimension', 'canton', 'switzerland'], '00000000-0000-0000-0000-000000000001', NOW())
ON CONFLICT (id) DO NOTHING;

-- Demo Data Sources (references to files in MinIO)
INSERT INTO data_sources (id, project_id, name, original_filename, format, size_bytes, row_count, column_count, storage_type, storage_path, metadata, uploaded_by, uploaded_at) VALUES
('44444444-4444-4444-4444-444444444441', '11111111-1111-1111-1111-111111111111',
 'Swiss Population by Canton',
 'swiss-population-by-canton.csv',
 'CSV',
 1524,
 26,
 6,
 'S3',
 'demo/swiss-population-by-canton.csv',
 '{"columns": [
    {"name": "canton_code", "type": "string"},
    {"name": "canton_name", "type": "string"},
    {"name": "year", "type": "integer"},
    {"name": "population", "type": "integer"},
    {"name": "area_km2", "type": "integer"},
    {"name": "density_per_km2", "type": "integer"}
  ], "encoding": "UTF-8", "delimiter": ","}'::jsonb,
 '00000000-0000-0000-0000-000000000001', NOW()),

('44444444-4444-4444-4444-444444444442', '11111111-1111-1111-1111-111111111111',
 'Swiss Employment Statistics',
 'swiss-employment-statistics.csv',
 'CSV',
 2048,
 30,
 6,
 'S3',
 'demo/swiss-employment-statistics.csv',
 '{"columns": [
    {"name": "canton_code", "type": "string"},
    {"name": "year", "type": "integer"},
    {"name": "quarter", "type": "string"},
    {"name": "sector", "type": "string"},
    {"name": "employment_count", "type": "integer"},
    {"name": "unemployment_rate", "type": "decimal"}
  ], "encoding": "UTF-8", "delimiter": ","}'::jsonb,
 '00000000-0000-0000-0000-000000000001', NOW()),

('44444444-4444-4444-4444-444444444443', '11111111-1111-1111-1111-111111111111',
 'Swiss GDP by Sector',
 'swiss-gdp-by-sector.csv',
 'CSV',
 1856,
 30,
 6,
 'S3',
 'demo/swiss-gdp-by-sector.csv',
 '{"columns": [
    {"name": "year", "type": "integer"},
    {"name": "sector", "type": "string"},
    {"name": "subsector", "type": "string"},
    {"name": "gdp_millions_chf", "type": "integer"},
    {"name": "growth_rate_pct", "type": "decimal"},
    {"name": "employment_share_pct", "type": "decimal"}
  ], "encoding": "UTF-8", "delimiter": ","}'::jsonb,
 '00000000-0000-0000-0000-000000000001', NOW())
ON CONFLICT (id) DO NOTHING;

-- Demo Dimensions
INSERT INTO dimensions (id, project_id, uri, name, description, type, hierarchy_type, is_shared, created_by, created_at) VALUES
('55555555-5555-5555-5555-555555555551', '11111111-1111-1111-1111-111111111111',
 'https://example.org/dimension/canton',
 'Swiss Canton',
 'Swiss cantons (administrative divisions)',
 'CODED',
 'FLAT',
 true,
 '00000000-0000-0000-0000-000000000001', NOW()),

('55555555-5555-5555-5555-555555555552', '11111111-1111-1111-1111-111111111111',
 'https://example.org/dimension/year',
 'Reference Year',
 'Calendar year for statistical reference',
 'TEMPORAL',
 'FLAT',
 true,
 '00000000-0000-0000-0000-000000000001', NOW()),

('55555555-5555-5555-5555-555555555553', '11111111-1111-1111-1111-111111111111',
 'https://example.org/dimension/sector',
 'Economic Sector',
 'Economic sector classification (Primary, Secondary, Tertiary)',
 'CODED',
 'FLAT',
 true,
 '00000000-0000-0000-0000-000000000001', NOW()),

('55555555-5555-5555-5555-555555555554', '11111111-1111-1111-1111-111111111111',
 'https://example.org/dimension/quarter',
 'Quarter',
 'Calendar quarter (Q1, Q2, Q3, Q4)',
 'TEMPORAL',
 'FLAT',
 true,
 '00000000-0000-0000-0000-000000000001', NOW())
ON CONFLICT (id) DO NOTHING;

-- Demo Dimension Values (Swiss Cantons)
INSERT INTO dimension_values (id, dimension_id, uri, code, label, label_lang, sort_order) VALUES
('66666666-6666-6666-6666-666666666601', '55555555-5555-5555-5555-555555555551', 'https://example.org/canton/ZH', 'ZH', 'Zurich', 'en', 1),
('66666666-6666-6666-6666-666666666602', '55555555-5555-5555-5555-555555555551', 'https://example.org/canton/BE', 'BE', 'Bern', 'en', 2),
('66666666-6666-6666-6666-666666666603', '55555555-5555-5555-5555-555555555551', 'https://example.org/canton/LU', 'LU', 'Lucerne', 'en', 3),
('66666666-6666-6666-6666-666666666604', '55555555-5555-5555-5555-555555555551', 'https://example.org/canton/UR', 'UR', 'Uri', 'en', 4),
('66666666-6666-6666-6666-666666666605', '55555555-5555-5555-5555-555555555551', 'https://example.org/canton/SZ', 'SZ', 'Schwyz', 'en', 5),
('66666666-6666-6666-6666-666666666606', '55555555-5555-5555-5555-555555555551', 'https://example.org/canton/OW', 'OW', 'Obwalden', 'en', 6),
('66666666-6666-6666-6666-666666666607', '55555555-5555-5555-5555-555555555551', 'https://example.org/canton/NW', 'NW', 'Nidwalden', 'en', 7),
('66666666-6666-6666-6666-666666666608', '55555555-5555-5555-5555-555555555551', 'https://example.org/canton/GL', 'GL', 'Glarus', 'en', 8),
('66666666-6666-6666-6666-666666666609', '55555555-5555-5555-5555-555555555551', 'https://example.org/canton/ZG', 'ZG', 'Zug', 'en', 9),
('66666666-6666-6666-6666-666666666610', '55555555-5555-5555-5555-555555555551', 'https://example.org/canton/FR', 'FR', 'Fribourg', 'en', 10),
('66666666-6666-6666-6666-666666666611', '55555555-5555-5555-5555-555555555551', 'https://example.org/canton/SO', 'SO', 'Solothurn', 'en', 11),
('66666666-6666-6666-6666-666666666612', '55555555-5555-5555-5555-555555555551', 'https://example.org/canton/BS', 'BS', 'Basel-Stadt', 'en', 12),
('66666666-6666-6666-6666-666666666613', '55555555-5555-5555-5555-555555555551', 'https://example.org/canton/BL', 'BL', 'Basel-Landschaft', 'en', 13),
('66666666-6666-6666-6666-666666666614', '55555555-5555-5555-5555-555555555551', 'https://example.org/canton/SH', 'SH', 'Schaffhausen', 'en', 14),
('66666666-6666-6666-6666-666666666615', '55555555-5555-5555-5555-555555555551', 'https://example.org/canton/AR', 'AR', 'Appenzell Ausserrhoden', 'en', 15),
('66666666-6666-6666-6666-666666666616', '55555555-5555-5555-5555-555555555551', 'https://example.org/canton/AI', 'AI', 'Appenzell Innerrhoden', 'en', 16),
('66666666-6666-6666-6666-666666666617', '55555555-5555-5555-5555-555555555551', 'https://example.org/canton/SG', 'SG', 'St. Gallen', 'en', 17),
('66666666-6666-6666-6666-666666666618', '55555555-5555-5555-5555-555555555551', 'https://example.org/canton/GR', 'GR', 'Graubunden', 'en', 18),
('66666666-6666-6666-6666-666666666619', '55555555-5555-5555-5555-555555555551', 'https://example.org/canton/AG', 'AG', 'Aargau', 'en', 19),
('66666666-6666-6666-6666-666666666620', '55555555-5555-5555-5555-555555555551', 'https://example.org/canton/TG', 'TG', 'Thurgau', 'en', 20),
('66666666-6666-6666-6666-666666666621', '55555555-5555-5555-5555-555555555551', 'https://example.org/canton/TI', 'TI', 'Ticino', 'en', 21),
('66666666-6666-6666-6666-666666666622', '55555555-5555-5555-5555-555555555551', 'https://example.org/canton/VD', 'VD', 'Vaud', 'en', 22),
('66666666-6666-6666-6666-666666666623', '55555555-5555-5555-5555-555555555551', 'https://example.org/canton/VS', 'VS', 'Valais', 'en', 23),
('66666666-6666-6666-6666-666666666624', '55555555-5555-5555-5555-555555555551', 'https://example.org/canton/NE', 'NE', 'Neuchatel', 'en', 24),
('66666666-6666-6666-6666-666666666625', '55555555-5555-5555-5555-555555555551', 'https://example.org/canton/GE', 'GE', 'Geneva', 'en', 25),
('66666666-6666-6666-6666-666666666626', '55555555-5555-5555-5555-555555555551', 'https://example.org/canton/JU', 'JU', 'Jura', 'en', 26)
ON CONFLICT (id) DO NOTHING;

-- Demo Economic Sector Values
INSERT INTO dimension_values (id, dimension_id, uri, code, label, label_lang, hierarchy_level, parent_id, sort_order) VALUES
('66666666-6666-6666-6666-666666666701', '55555555-5555-5555-5555-555555555553', 'https://example.org/sector/primary', 'PRIMARY', 'Primary Sector', 'en', 0, NULL, 1),
('66666666-6666-6666-6666-666666666702', '55555555-5555-5555-5555-555555555553', 'https://example.org/sector/secondary', 'SECONDARY', 'Secondary Sector', 'en', 0, NULL, 2),
('66666666-6666-6666-6666-666666666703', '55555555-5555-5555-5555-555555555553', 'https://example.org/sector/tertiary', 'TERTIARY', 'Tertiary Sector', 'en', 0, NULL, 3),
('66666666-6666-6666-6666-666666666711', '55555555-5555-5555-5555-555555555553', 'https://example.org/sector/agriculture', 'AGRI', 'Agriculture', 'en', 1, '66666666-6666-6666-6666-666666666701', 11),
('66666666-6666-6666-6666-666666666712', '55555555-5555-5555-5555-555555555553', 'https://example.org/sector/forestry', 'FOREST', 'Forestry', 'en', 1, '66666666-6666-6666-6666-666666666701', 12),
('66666666-6666-6666-6666-666666666721', '55555555-5555-5555-5555-555555555553', 'https://example.org/sector/manufacturing', 'MANU', 'Manufacturing', 'en', 1, '66666666-6666-6666-6666-666666666702', 21),
('66666666-6666-6666-6666-666666666722', '55555555-5555-5555-5555-555555555553', 'https://example.org/sector/construction', 'CONST', 'Construction', 'en', 1, '66666666-6666-6666-6666-666666666702', 22),
('66666666-6666-6666-6666-666666666723', '55555555-5555-5555-5555-555555555553', 'https://example.org/sector/energy', 'ENERGY', 'Energy', 'en', 1, '66666666-6666-6666-6666-666666666702', 23),
('66666666-6666-6666-6666-666666666731', '55555555-5555-5555-5555-555555555553', 'https://example.org/sector/finance', 'FIN', 'Financial Services', 'en', 1, '66666666-6666-6666-6666-666666666703', 31),
('66666666-6666-6666-6666-666666666732', '55555555-5555-5555-5555-555555555553', 'https://example.org/sector/healthcare', 'HEALTH', 'Healthcare', 'en', 1, '66666666-6666-6666-6666-666666666703', 32),
('66666666-6666-6666-6666-666666666733', '55555555-5555-5555-5555-555555555553', 'https://example.org/sector/education', 'EDU', 'Education', 'en', 1, '66666666-6666-6666-6666-666666666703', 33),
('66666666-6666-6666-6666-666666666734', '55555555-5555-5555-5555-555555555553', 'https://example.org/sector/retail', 'RETAIL', 'Retail Trade', 'en', 1, '66666666-6666-6666-6666-666666666703', 34),
('66666666-6666-6666-6666-666666666735', '55555555-5555-5555-5555-555555555553', 'https://example.org/sector/it', 'IT', 'Information Technology', 'en', 1, '66666666-6666-6666-6666-666666666703', 35)
ON CONFLICT (id) DO NOTHING;

-- Demo Triplestore Connection (GraphDB)
INSERT INTO triplestore_connections (id, project_id, name, type, url, default_graph, auth_type, auth_config, is_default, health_status, created_by, created_at) VALUES
('77777777-7777-7777-7777-777777777771', '11111111-1111-1111-1111-111111111111',
 'GraphDB Local',
 'GRAPHDB',
 'http://graphdb:7200',
 'https://example.org/graph/default',
 'NONE',
 '{"repository": "rdf-forge"}',
 true,
 'HEALTHY',
 '00000000-0000-0000-0000-000000000001', NOW())
ON CONFLICT (id) DO NOTHING;

-- Demo Jobs (some completed, some pending)
INSERT INTO jobs (id, pipeline_id, pipeline_version, status, priority, is_dry_run, triggered_by, started_at, completed_at, metrics, created_by, created_at) VALUES
('88888888-8888-8888-8888-888888888881', '22222222-2222-2222-2222-222222222221', 1, 'COMPLETED', 5, false, 'MANUAL',
 NOW() - INTERVAL '2 hours', NOW() - INTERVAL '1 hour 55 minutes',
 '{"triplesGenerated": 156, "observationsCreated": 26, "processingTimeMs": 4523}'::jsonb,
 '00000000-0000-0000-0000-000000000001', NOW() - INTERVAL '2 hours'),

('88888888-8888-8888-8888-888888888882', '22222222-2222-2222-2222-222222222222', 1, 'COMPLETED', 5, false, 'MANUAL',
 NOW() - INTERVAL '1 hour', NOW() - INTERVAL '55 minutes',
 '{"triplesGenerated": 420, "observationsCreated": 30, "processingTimeMs": 6234}'::jsonb,
 '00000000-0000-0000-0000-000000000001', NOW() - INTERVAL '1 hour'),

('88888888-8888-8888-8888-888888888883', '22222222-2222-2222-2222-222222222223', 1, 'COMPLETED', 5, false, 'MANUAL',
 NOW() - INTERVAL '30 minutes', NOW() - INTERVAL '25 minutes',
 '{"triplesGenerated": 540, "observationsCreated": 30, "processingTimeMs": 5127}'::jsonb,
 '00000000-0000-0000-0000-000000000001', NOW() - INTERVAL '30 minutes')
ON CONFLICT (id) DO NOTHING;

-- Job Logs for completed jobs
INSERT INTO job_logs (job_id, timestamp, level, step, message) VALUES
('88888888-8888-8888-8888-888888888881', NOW() - INTERVAL '2 hours', 'INFO', 'load-csv', 'Loading CSV file: swiss-population-by-canton.csv'),
('88888888-8888-8888-8888-888888888881', NOW() - INTERVAL '1 hour 59 minutes', 'INFO', 'load-csv', 'Loaded 26 rows with 6 columns'),
('88888888-8888-8888-8888-888888888881', NOW() - INTERVAL '1 hour 58 minutes', 'INFO', 'map-to-rdf', 'Starting RDF mapping'),
('88888888-8888-8888-8888-888888888881', NOW() - INTERVAL '1 hour 57 minutes', 'INFO', 'map-to-rdf', 'Generated 156 triples'),
('88888888-8888-8888-8888-888888888881', NOW() - INTERVAL '1 hour 56 minutes', 'INFO', 'graph-store-put', 'Uploading to GraphDB'),
('88888888-8888-8888-8888-888888888881', NOW() - INTERVAL '1 hour 55 minutes', 'INFO', 'graph-store-put', 'Pipeline completed successfully'),

('88888888-8888-8888-8888-888888888882', NOW() - INTERVAL '1 hour', 'INFO', 'load-csv', 'Loading CSV file: swiss-employment-statistics.csv'),
('88888888-8888-8888-8888-888888888882', NOW() - INTERVAL '59 minutes', 'INFO', 'load-csv', 'Loaded 30 rows with 6 columns'),
('88888888-8888-8888-8888-888888888882', NOW() - INTERVAL '58 minutes', 'INFO', 'map-to-rdf', 'Starting RDF mapping'),
('88888888-8888-8888-8888-888888888882', NOW() - INTERVAL '57 minutes', 'INFO', 'map-to-rdf', 'Generated 420 triples'),
('88888888-8888-8888-8888-888888888882', NOW() - INTERVAL '56 minutes', 'INFO', 'graph-store-put', 'Uploading to GraphDB'),
('88888888-8888-8888-8888-888888888882', NOW() - INTERVAL '55 minutes', 'INFO', 'graph-store-put', 'Pipeline completed successfully');
