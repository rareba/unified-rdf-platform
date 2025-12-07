# RDF Forge Demo Materials

This folder contains all the materials needed to test and demonstrate RDF Forge platform features.

## Quick Start

The application is running at:
- **UI**: http://localhost (port 80 via Traefik)
- **Traefik Dashboard**: http://localhost:8088
- **GraphDB**: http://localhost:7200
- **MinIO Console**: http://minio.localhost (or http://localhost:9001)
- **Keycloak**: http://auth.localhost
- **Prometheus**: http://prometheus.localhost (with observability profile)
- **Grafana**: http://grafana.localhost (admin/admin) (with observability profile)

## Folder Structure

```
demo_material/
├── csv_data/                  # CSV files for data upload and cube creation
├── shacl_shapes/              # SHACL shape files for validation
├── rdf_samples/               # Sample RDF/Turtle files
├── pipeline_configs/          # Example pipeline configurations
└── README.md                  # This file
```

---

## Testing Each Feature

### 1. Dashboard
Navigate to the home page to see the dashboard with system overview.

### 2. Data Sources (Upload CSV)
1. Go to **Data Sources** in the sidebar
2. Click **Upload** button
3. Select a CSV file from `csv_data/` folder:
   - `energy-consumption-by-country.csv` - European energy statistics
   - `museum-visits-quarterly.csv` - Swiss museum visitor data
   - `public-transport-usage.csv` - Swiss public transport statistics
4. The file will be uploaded to MinIO storage

### 3. Cube Creator Wizard
1. Go to **Cube Creator** in the sidebar
2. Click **Create New Cube**
3. Step 1: Select a data source (one of the uploaded CSVs)
4. Step 2: Map columns to dimensions/measures:
   - **Dimensions**: country_code, year, quarter, etc.
   - **Measures**: population, revenue, passenger_count, etc.
5. Step 3: Configure cube metadata (name, description, base URI)
6. Step 4: Review and create the cube

### 4. SHACL Studio (Shape Editor)
1. Go to **SHACL Studio** in the sidebar
2. Upload or paste a SHACL shape from `shacl_shapes/`:
   - `energy-cube-shape.ttl` - Validates energy data cubes
   - `museum-data-shape.ttl` - Validates museum visitor data
   - `transport-data-shape.ttl` - Validates transport statistics
3. Use the visual editor to modify shapes
4. Test validation against sample data

### 5. Validation Testing
1. In SHACL Studio, load `energy-cube-shape.ttl`
2. Load data from `rdf_samples/invalid-cube-for-testing.ttl`
3. Run validation to see errors detected:
   - Missing required properties
   - Invalid data types
   - Out-of-range values
   - Pattern violations

### 6. Pipeline Designer
1. Go to **Pipelines** in the sidebar
2. Create a new pipeline or import from `pipeline_configs/`:
   - `csv-to-cube-pipeline.json` - Full CSV to cube conversion
   - `validation-only-pipeline.json` - Validate existing data
3. Configure pipeline steps visually
4. Run the pipeline and monitor progress

### 7. Jobs Monitor
1. Go to **Jobs** in the sidebar
2. View running and completed jobs
3. Check job logs and status
4. Cancel or restart jobs as needed

### 8. Dimensions Manager
1. Go to **Dimensions** in the sidebar
2. Browse existing dimension hierarchies
3. Create shared dimensions for reuse across cubes
4. Link dimensions to SKOS concept schemes

### 9. Triplestore Browser
1. Go to **Triplestore** in the sidebar
2. Browse graphs in GraphDB
3. Run SPARQL queries:

```sparql
# List all datasets
SELECT ?dataset ?label WHERE {
  ?dataset a <http://purl.org/linked-data/cube#DataSet> .
  ?dataset <http://www.w3.org/2000/01/rdf-schema#label> ?label .
}

# Count observations per dataset
SELECT ?dataset (COUNT(?obs) as ?count) WHERE {
  ?obs a <http://purl.org/linked-data/cube#Observation> .
  ?obs <http://purl.org/linked-data/cube#dataSet> ?dataset .
} GROUP BY ?dataset
```

### 10. Settings
1. Go to **Settings** in the sidebar
2. Configure:
   - API timeout settings
   - Default namespaces
   - Theme preferences

---

## Sample Data Descriptions

### CSV Files

| File | Description | Columns |
|------|-------------|---------|
| `energy-consumption-by-country.csv` | European energy statistics | country_code, country_name, year, energy_consumption_gwh, renewable_percentage |
| `museum-visits-quarterly.csv` | Swiss museum visitors | museum_id, museum_name, location, quarter, year, visitor_count, ticket_revenue |
| `public-transport-usage.csv` | Public transport stats | line_id, transport_type, city, route_name, date, passenger_count, on_time_percentage |

### SHACL Shapes

| Shape | Purpose | Key Validations |
|-------|---------|-----------------|
| `energy-cube-shape.ttl` | Energy data | Country codes (ISO 2-letter), positive values, percentage ranges |
| `museum-data-shape.ttl` | Museum data | ID patterns (MUS###), visitor/revenue consistency |
| `transport-data-shape.ttl` | Transport data | Transport type enum, date formats, on-time thresholds |

### RDF Samples

| File | Purpose |
|------|---------|
| `sample-cube-observations.ttl` | Valid cube structure for reference |
| `invalid-cube-for-testing.ttl` | Intentionally invalid data for testing validation |

---

## Kubernetes Simulation Features

This deployment simulates Kubernetes patterns:

- **Ingress Controller**: Traefik routes all traffic
- **Service Mesh**: DNS-based service discovery
- **Replicas**: Gateway, pipeline-service, shacl-service have 2 replicas
- **Health Checks**: All services have readiness/liveness probes
- **Resource Limits**: CPU/memory constraints on all containers
- **Secrets**: Docker secrets simulate K8s secrets
- **ConfigMaps**: Docker configs simulate K8s configmaps
- **Persistent Volumes**: Named volumes for data persistence

### Scale Services (simulate HPA)
```bash
docker compose -f docker-compose.k8s-simulation.yml up -d --scale pipeline-service=3
```

### View Logs (like kubectl logs)
```bash
docker compose -f docker-compose.k8s-simulation.yml logs -f gateway
```

### Enable Observability Stack
```bash
docker compose -f docker-compose.k8s-simulation.yml --profile observability up -d
```

---

## Credentials

| Service | Username | Password |
|---------|----------|----------|
| Keycloak Admin | admin | admin |
| GraphDB | - | - (no auth) |
| MinIO | rdfforge | (see docker/secrets/minio-secret.txt) |
| Grafana | admin | admin |

---

## Troubleshooting

### Services not starting
```bash
# Check service status
docker compose -f rdf-forge/docker-compose.k8s-simulation.yml ps

# View logs for specific service
docker compose -f rdf-forge/docker-compose.k8s-simulation.yml logs gateway
```

### GraphDB not responding
```bash
# Check if repository was created
curl http://localhost:7200/rest/repositories
```

### Clear everything and restart
```bash
docker compose -f rdf-forge/docker-compose.k8s-simulation.yml down -v
docker compose -f rdf-forge/docker-compose.k8s-simulation.yml up -d
```
