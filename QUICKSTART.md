# Cube Creator X - Quick Start Guide

## Overview

Cube Creator X (formerly RDF Forge) is a unified platform for creating RDF data cubes, managing pipelines, and working with triplestores.

## Features

- **Pipeline Editor**: Drag-and-drop visual pipeline designer for ETL workflows
- **Cube Creator**: Step-by-step wizard for creating RDF data cubes from CSV files
- **SHACL Studio**: Create and validate SHACL shapes for data quality
- **Data Manager**: Upload and manage data sources
- **Dimension Manager**: Define and manage cube dimensions
- **Triplestore Browser**: Query and explore RDF triplestores
- **Job Monitor**: Track pipeline execution and view logs

## Running in Standalone Mode

The standalone mode runs all services without requiring Keycloak authentication, perfect for local development and demos.

### Prerequisites

- Docker and Docker Compose
- Java 21 (for backend compilation)
- Node.js 18+ and npm (for UI development)

### Quick Start

1. **Navigate to the rdf-forge directory:**
   ```bash
   cd rdf-forge
   ```

2. **Start all services:**
   ```bash
   docker-compose -f docker-compose.standalone.yml up --build
   ```

3. **Access the application:**
   Open your browser to http://localhost:4200

### Services

The standalone deployment includes:

- **Frontend (UI)**: http://localhost:4200
- **API Gateway**: http://localhost:9080 (proxied through UI at /api/v1)
- **GraphDB**: http://localhost:7200
- **PostgreSQL**: localhost:5432
- **MinIO Console**: http://localhost:9001 (S3 API on port 9000)

### Development

#### Backend Development

Build all services:
```bash
cd rdf-forge
mvn clean install
```

Run individual service:
```bash
cd rdf-forge-pipeline-service
mvn spring-boot:run -Dspring-boot.run.profiles=standalone,noauth
```

#### Frontend Development

Start dev server:
```bash
cd rdf-forge-ui
npm install
npm run start
```

Build for production:
```bash
npm run build -- --configuration=standalone
```

### Troubleshooting

**Services not starting:**
- Check Docker is running
- Ensure ports 8080, 5432, 7200, 9000, 9001 are available
- Check logs: `docker-compose -f docker-compose.standalone.yml logs`

**UI not loading:**
- Wait for all services to be healthy (can take 1-2 minutes)
- Check UI build completed successfully
- Verify nginx is serving files from `/app/browser`

**Database connection errors:**
- Wait for PostgreSQL health check to pass
- Check environment variables in docker-compose.standalone.yml

## Next Steps

1. **Create a Pipeline**: Go to Pipelines > New Pipeline
2. **Upload Data**: Go to Data Sources > Upload
3. **Create a Cube**: Go to Cube Creator and follow the wizard
4. **Validate Data**: Go to SHACL Studio to create validation shapes

## Architecture

- **Backend**: Spring Boot 3.2.5 microservices (Java 21)
- **Frontend**: Angular 21 with PrimeNG
- **Data Processing**: Apache Camel pipelines
- **RDF Storage**: GraphDB triplestore
- **Object Storage**: MinIO S3-compatible storage
- **Database**: PostgreSQL for metadata

## Recent Fixes (December 2025)

### Pagination Handling
Fixed critical issue where Angular frontend expected plain arrays but Spring Boot backend returned paginated responses with `{content: [], pageable: {}}` structure.

**Changes Made:**
- Added `getArray<T>()` method to `ApiService` that automatically extracts content from paginated responses
- Updated all service methods (`data.service.ts`, `dimension.service.ts`, `job.service.ts`, `pipeline.service.ts`, `shacl.service.ts`, `triplestore.service.ts`) to use `getArray()` for list operations
- This resolves errors like `t.slice is not a function` and `a.filter is not a function`

### API Gateway Configuration
Fixed nginx proxy configuration to correctly route API calls from port 80 to the gateway service on port 8080 (was incorrectly set to 8000).

### Branding Updates
- Renamed application from "RDF Forge" to "Cube Creator X" throughout the UI
- Updated page titles, headers, and package naming
