# RDF Forge Deployment Guide

This guide covers the deployment options for RDF Forge, a comprehensive platform for RDF data management, SHACL validation, and pipeline orchestration.

## Table of Contents

- [Quick Start](#quick-start)
- [Deployment Modes Overview](#deployment-modes-overview)
- [Mode 1: Standalone (Offline)](#mode-1-standalone-offline)
- [Mode 2: Development](#mode-2-development)
- [Mode 3: Kubernetes Production](#mode-3-kubernetes-production)
- [Port Reference](#port-reference)
- [Environment Variables](#environment-variables)
- [Troubleshooting](#troubleshooting)

---

## Quick Start

### Fastest Way to Get Started (Standalone Mode)

```bash
# Navigate to rdf-forge directory
cd rdf-forge

# Start all services with a single command
docker compose -f docker-compose.standalone.yml up -d

# Wait for services to be healthy (GraphDB takes ~60 seconds)
docker compose -f docker-compose.standalone.yml ps

# Access the application
# UI:      http://localhost:4200
# API:     http://localhost:9080
# GraphDB: http://localhost:7200
```

### Stop Services

```bash
docker compose -f docker-compose.standalone.yml down

# To also remove data volumes (fresh start):
docker compose -f docker-compose.standalone.yml down -v
```

---

## Deployment Modes Overview

RDF Forge supports three deployment modes to accommodate different use cases:

| Mode | File | Use Case | Auth | Services |
|------|------|----------|------|----------|
| **Standalone** | `docker-compose.standalone.yml` | Demo, testing, local development | None | All local |
| **Development** | `docker-compose.development.yml` | Development with remote K8s | Optional | Flexible |
| **Kubernetes** | Helm charts (separate repo) | Production | Keycloak | K8s cluster |

### Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────┐
│                         STANDALONE MODE                              │
│  ┌──────────┐  ┌─────────┐  ┌─────────────────────────────────────┐ │
│  │ UI :4200 │──│ Gateway │──│ Backend Services (internal network) │ │
│  └──────────┘  │  :9080  │  │  - Pipeline, SHACL, Job, Data,      │ │
│                └─────────┘  │    Dimension, Triplestore Services  │ │
│                             └─────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │ Infrastructure: GraphDB:7200 │ PostgreSQL:5432 │ Redis:6379  │   │
│  │                 MinIO:9000/9001                               │   │
│  └──────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Mode 1: Standalone (Offline)

**Purpose**: Demo, testing, local development - completely standalone

### Features

- ✅ **No authentication** - instant access
- ✅ **All services local** - no external dependencies
- ✅ **Windows-safe ports** - avoids Hyper-V reserved port conflicts
- ✅ **Single command startup**
- ✅ **Persistent data volumes**

### Start Standalone Mode

```bash
docker compose -f docker-compose.standalone.yml up -d
```

### Access Points

| Service | URL | Description |
|---------|-----|-------------|
| **UI** | http://localhost:4200 | Angular frontend application |
| **API Gateway** | http://localhost:9080 | REST API endpoint |
| **GraphDB** | http://localhost:7200 | Triplestore admin interface |
| **MinIO Console** | http://localhost:9001 | File storage management |

### What's Included

1. **Frontend**: Angular UI (no authentication)
2. **API Gateway**: Routes requests to backend services
3. **Backend Services** (internal network only):
   - Pipeline Service - Pipeline management
   - SHACL Service - RDF validation
   - Job Service - Job scheduling
   - Data Service - Data management
   - Dimension Service - Cube dimensions
   - Triplestore Service - SPARQL abstraction
4. **Infrastructure**:
   - GraphDB - RDF triplestore
   - PostgreSQL - Relational database
   - Redis - Caching and queues
   - MinIO - S3-compatible file storage

### View Logs

```bash
# All services
docker compose -f docker-compose.standalone.yml logs -f

# Specific service
docker compose -f docker-compose.standalone.yml logs -f gateway

# Last 100 lines
docker compose -f docker-compose.standalone.yml logs --tail=100 ui
```

### Health Check

```bash
# Check service status
docker compose -f docker-compose.standalone.yml ps

# Wait for GraphDB to be ready (takes ~60 seconds)
docker compose -f docker-compose.standalone.yml logs -f graphdb
```

---

## Mode 2: Development

**Purpose**: Development with external Kubernetes cluster or remote services

### Features

- ✅ **Flexible configuration** - use profiles to run what you need
- ✅ **Connect to remote services** - K8s, external databases
- ✅ **Optional authentication** - Keycloak integration
- ✅ **Selective deployment** - run only specific services locally

### Usage Scenarios

#### Scenario A: UI Only (Connect to Remote K8s)

Run just the UI locally, connecting to a Kubernetes backend:

```bash
REMOTE_API_URL=https://api.k8s.example.com \
docker compose -f docker-compose.development.yml up ui
```

#### Scenario B: UI + Local Gateway (Remote Backend Services)

```bash
docker compose -f docker-compose.development.yml --profile local-backend up
```

#### Scenario C: UI + Local Infrastructure

Run UI and local databases/triplestore:

```bash
docker compose -f docker-compose.development.yml --profile infrastructure up
```

#### Scenario D: Full Local Stack

Run everything locally (similar to standalone but with dev naming):

```bash
docker compose -f docker-compose.development.yml --profile full up
```

### Environment Configuration

Create a `.env` file from the example:

```bash
cp .env.example .env
# Edit .env with your configuration
```

Key environment variables for development:

```bash
# Connect to remote API
REMOTE_API_URL=https://api.k8s.example.com

# Remote database
DATABASE_URL=jdbc:postgresql://remote-host:5432/rdfforge
DATABASE_USER=remote_user
DATABASE_PASSWORD=remote_password

# Enable authentication
AUTH_ENABLED=true
KEYCLOAK_URL=https://auth.k8s.example.com
```

### Available Profiles

| Profile | Services Started |
|---------|-----------------|
| (none) | UI only |
| `local-backend` | UI + Gateway |
| `infrastructure` | UI + GraphDB, PostgreSQL, Redis, MinIO |
| `full` | All services (complete local stack) |

---

## Mode 3: Kubernetes Production

**Purpose**: Production deployment with high availability and security

### Features

- ✅ **Horizontal scaling** - scale services independently
- ✅ **Keycloak authentication** - enterprise SSO
- ✅ **Resource management** - CPU/memory limits
- ✅ **Secrets management** - K8s secrets
- ✅ **Monitoring ready** - Prometheus metrics

### Deployment Approach

Kubernetes deployment uses Helm charts (maintained separately):

```bash
# Add Helm repository (when available)
helm repo add rdf-forge https://charts.rdfforge.example.com

# Install with default values
helm install rdf-forge rdf-forge/rdf-forge

# Install with custom values
helm install rdf-forge rdf-forge/rdf-forge -f values.yaml
```

### Helm Chart Components

The Helm chart includes:

1. **Deployments**: All microservices
2. **Services**: ClusterIP for internal, LoadBalancer for external
3. **Ingress**: Nginx/Traefik ingress rules
4. **ConfigMaps**: Application configuration
5. **Secrets**: Credentials and keys
6. **PVCs**: Persistent storage for GraphDB, PostgreSQL

### Production Checklist

Before deploying to production:

- [ ] Change all default passwords
- [ ] Configure TLS/SSL certificates
- [ ] Set up Keycloak realm and clients
- [ ] Configure resource limits (CPU/memory)
- [ ] Set up backup for databases
- [ ] Configure monitoring (Prometheus/Grafana)
- [ ] Set up log aggregation
- [ ] Configure horizontal pod autoscaling

---

## Port Reference

### Windows-Safe Port Mapping

These ports avoid the Windows Hyper-V reserved range (8000-8010):

| Service | Host Port | Container Port | Notes |
|---------|-----------|----------------|-------|
| UI | 4200 | 80 | Angular default |
| Gateway | 9080 | 8080 | Avoids 8000-8010 |
| GraphDB | 7200 | 7200 | Standard GraphDB |
| PostgreSQL | 5432 | 5432 | Standard |
| Redis | 6379 | 6379 | Standard |
| MinIO API | 9000 | 9000 | Standard |
| MinIO Console | 9001 | 9001 | Standard |

### Why These Ports?

Windows Hyper-V can reserve ports in the 8000-8010 range, causing errors like:

```
Error response from daemon: ports are not available: exposing port TCP 0.0.0.0:8005
```

Our configuration uses safe alternative ports to avoid these conflicts.

### Checking Reserved Ports (Windows)

```powershell
# Check which ports are reserved by Hyper-V
netsh interface ipv4 show excludedportrange protocol=tcp
```

---

## Environment Variables

### Quick Reference

```bash
# Ports
UI_PORT=4200
GATEWAY_PORT=9080
GRAPHDB_PORT=7200
POSTGRES_PORT=5432
REDIS_PORT=6379
MINIO_API_PORT=9000
MINIO_CONSOLE_PORT=9001

# Database
POSTGRES_DB=rdfforge
POSTGRES_USER=rdfforge
POSTGRES_PASSWORD=rdfforge

# MinIO
MINIO_ROOT_USER=minioadmin
MINIO_ROOT_PASSWORD=minioadmin

# GraphDB
GDB_HEAP_SIZE=2g

# Spring Profiles
SPRING_PROFILES=noauth,graphdb

# Authentication (optional)
AUTH_ENABLED=false
KEYCLOAK_URL=http://localhost:8080
KEYCLOAK_REALM=rdfforge
KEYCLOAK_CLIENT_ID=rdf-forge-ui
```

### Full Configuration

See [`.env.example`](.env.example) for complete configuration options.

---

## Troubleshooting

### Common Issues

#### Port Already in Use

```bash
# Check what's using a port (Windows)
netstat -ano | findstr :4200

# Check what's using a port (Linux/Mac)
lsof -i :4200
```

#### GraphDB Not Starting

GraphDB takes 60+ seconds to initialize:

```bash
# Watch GraphDB logs
docker compose -f docker-compose.standalone.yml logs -f graphdb

# Check health status
docker compose -f docker-compose.standalone.yml ps graphdb
```

#### Services Can't Connect to Database

Ensure PostgreSQL is healthy before other services start:

```bash
# Check PostgreSQL status
docker compose -f docker-compose.standalone.yml exec postgres pg_isready -U rdfforge
```

#### Windows Port Conflict (Hyper-V)

If you see "ports are not available" errors:

1. Check reserved ports: `netsh interface ipv4 show excludedportrange protocol=tcp`
2. Use the standalone compose file which uses safe ports (9080 instead of 8000-8010)

#### Reset Everything (Fresh Start)

```bash
# Stop and remove all containers, networks, and volumes
docker compose -f docker-compose.standalone.yml down -v

# Remove all images (forces rebuild)
docker compose -f docker-compose.standalone.yml down --rmi all -v

# Start fresh
docker compose -f docker-compose.standalone.yml up -d --build
```

### Getting Help

1. Check logs: `docker compose -f docker-compose.standalone.yml logs -f`
2. Check service status: `docker compose -f docker-compose.standalone.yml ps`
3. Verify network: `docker network ls | grep rdf-forge`

---

## Migration from Old Configuration

If you were using the old `docker-compose.offline.yml` or `docker-compose.online.yml`:

### From `docker-compose.offline.yml`

The new `docker-compose.standalone.yml` replaces this with:
- Windows-safe ports (9080 instead of 8000)
- Backend services not exposed to host (internal network only)
- Cleaner configuration

```bash
# Stop old containers
docker compose -f docker-compose.offline.yml down

# Start new standalone mode
docker compose -f docker-compose.standalone.yml up -d
```

### From `docker-compose.online.yml`

For authenticated deployment, use Kubernetes mode or modify development mode:

```bash
# Development with authentication
AUTH_ENABLED=true \
KEYCLOAK_URL=http://your-keycloak:8080 \
docker compose -f docker-compose.development.yml --profile full up
```

---

## Files Reference

| File | Purpose |
|------|---------|
| `docker-compose.standalone.yml` | Complete offline stack (recommended for local dev) |
| `docker-compose.development.yml` | Flexible development with profiles |
| `.env.example` | Environment variable template |
| `DEPLOYMENT.md` | This documentation |

### Deprecated Files

These files are superseded by the new configuration:
- `docker-compose.offline.yml` → Use `docker-compose.standalone.yml`
- `docker-compose.online.yml` → Use `docker-compose.development.yml` with auth
- `docker-compose.kubernetes.yml` → Use `docker-compose.development.yml` with remote API
- `.env.offline` / `.env.online` → Use `.env.example`
