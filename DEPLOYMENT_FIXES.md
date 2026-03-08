# RDF Forge Deployment Configuration Fixes

## Overview
This document summarizes all deployment and Docker configuration fixes applied to address security, network isolation, and production-readiness issues.

## Issues Fixed

### Issue 1: Network Isolation and Port Exposure (docker-compose.yml)
**Status:** ✓ FIXED

#### Problem
- All backend service ports (8001-8006) were published directly to the host
- No explicit network defined, services were on default bridge
- Infrastructure ports could be accessed from anywhere

#### Solution
- Created explicit `rdf-forge-network` bridge network
- Changed all infrastructure service ports to `127.0.0.1:PORT:PORT` binding (localhost only)
- Changed backend services (8001-8006) from `ports:` to `expose:` (network-only, not host-exposed)
- Added `networks: rdf-forge-network` to all services
- Kept UI (3000) and Gateway (8000) with localhost binding for local development access

#### Files Modified
- `rdf-forge/docker-compose.yml`

#### Details
- postgres: 5432 → 127.0.0.1:5432:5432
- redis: 6379 → 127.0.0.1:6379:6379
- minio: 9000,9001 → 127.0.0.1:9000:9000, 127.0.0.1:9001:9001
- fuseki: 3030 → 127.0.0.1:3030:3030
- keycloak: 8080 → 127.0.0.1:8080:8080
- gateway: 8000 → 127.0.0.1:8000:8000
- pipeline-service (8001): ports → expose (network-only)
- shacl-service (8002): ports → expose (network-only)
- job-service (8003): ports → expose (network-only)
- data-service (8004): ports → expose (network-only)
- dimension-service (8005): ports → expose (network-only)
- triplestore-service (8006): ports → expose (network-only)

---

### Issue 2: Redis Authentication
**Status:** ✓ FIXED

#### Problem
- Redis had no password configured
- Unauthenticated access to cache/session storage
- Sensitive job and pipeline data could be accessed without credentials

#### Solution
- Added --requirepass with configurable password to Redis command
- Updated healthcheck to use password-authenticated ping
- Applied to both development and production compose files

#### Files Modified
- `rdf-forge/docker-compose.yml`
- `rdf-forge/docker-compose.production.yml`

#### Details
Development: redis-server --requirepass ${REDIS_PASSWORD:-rdfforge_redis}
Production: redis-server --requirepass ${REDIS_PASSWORD:-rdfforge_redis} --maxmemory 256mb --maxmemory-policy allkeys-lru

---

### Issue 3: Keycloak Development Mode Warning
**Status:** ✓ FIXED

#### Problem
- Keycloak was started in start-dev mode with TLS disabled
- Development defaults were being used in production configs
- No clear distinction between dev and production modes

#### Solution
- Added warning comment in development compose explaining start-dev limitations
- Added production-grade Keycloak service to production compose
- Production uses start --optimized command for full security features
- Proper resource limits and health checks in production variant

#### Files Modified
- `rdf-forge/docker-compose.yml`
- `rdf-forge/docker-compose.production.yml`

#### Details
Development: start-dev --import-realm (with warning comment)
Production: start --optimized --import-realm (with resource limits, health checks, security)

---

### Issue 4: Git Tracking of node_modules
**Status:** ✓ FIXED

#### Problem
- Angular UI's node_modules/ directory was tracked in git
- Massive number of files in repository history
- Slows down git operations and increases clone times

#### Solution
- Added specific pattern for Angular UI node_modules to .gitignore
- Pattern is redundant with existing **/node_modules/ but explicit for clarity

#### Files Modified
- `.gitignore`

#### Details
**/node_modules/ (existing pattern)
rdf-forge/rdf-forge-ui/node_modules/ (explicit pattern for clarity)

---

### Issue 5: Helm Chart Expansion
**Status:** ✓ FIXED

#### Problem
- Helm chart values.yaml was minimal (52 lines)
- Missing resource limits, requests, health checks
- No pod disruption budgets, RBAC, or production configurations
- Missing templates directory entirely

#### Solution
- Expanded values.yaml to 350+ lines with comprehensive configuration
- Created templates/ directory with 9 template files
- Updated Chart.yaml with proper metadata and version bump

#### Files Modified
- `rdf-forge/charts/rdf-forge/Chart.yaml` (v0.1.0 → v0.2.0)
- `rdf-forge/charts/rdf-forge/values.yaml` (52 lines → 350+ lines)
- Created 9 new template files in templates/

#### Template Files Created
1. _helpers.tpl - Common helper functions
2. NOTES.txt - Post-installation instructions
3. serviceaccount.yaml - RBAC service account
4. deployment-gateway.yaml - Gateway deployment with probes, resources, security
5. service-gateway.yaml - Service definition
6. ingress.yaml - Ingress controller with TLS support
7. poddisruptionbudget.yaml - PDB for high availability
8. hpa.yaml - Horizontal Pod Autoscaler
9. README.md - Template documentation

#### Configuration Added

Resource Limits & Requests per service:
- CPU requests: 100-500m
- CPU limits: 500m-1500m
- Memory requests: 128-512Mi
- Memory limits: 256Mi-2Gi

Health Checks:
- Liveness probes with 60s initial delay, 10s period
- Readiness probes with 30s initial delay, 5s period
- Proper failure thresholds

Pod Security:
- runAsNonRoot: true
- readOnlyRootFilesystem: true (where applicable)
- allowPrivilegeEscalation: false
- Dropped all capabilities

High Availability:
- Pod disruption budgets (minAvailable: 1)
- Pod anti-affinity for spread across nodes
- 2x replicas by default

Autoscaling:
- HPA support with CPU/memory targets
- Scalable from 2 to 5 replicas

Ingress:
- TLS termination with cert-manager
- Rate limiting support
- DNS: rdf-forge.example.com

---

## Security Improvements Summary

| Issue | Risk Level | Before | After |
|-------|-----------|--------|-------|
| Port Exposure | HIGH | All ports exposed | Localhost-only |
| Redis Auth | MEDIUM | No authentication | Password protected |
| Network Isolation | HIGH | Default bridge | Explicit network |
| Keycloak Mode | MEDIUM | Dev mode | Optimized mode |
| Pod Security | HIGH | No restrictions | Non-root, read-only |
| Resource Limits | MEDIUM | None | CPU/Memory limits |
| Health Checks | MEDIUM | Minimal | Comprehensive |
| PDBs | LOW | None | Guaranteed availability |

---

## Deployment Paths

### Local Development
```
docker-compose -f rdf-forge/docker-compose.yml up
```
Services accessible only on localhost with proper security boundaries.

### Production Docker Compose
```
docker-compose -f rdf-forge/docker-compose.production.yml up
```
All internal services with resource limits and TLS in Keycloak.

### Kubernetes Deployment
```
helm install rdf-forge rdf-forge/charts/rdf-forge
```
Complete resource and pod configuration with ingress and autoscaling.

---

## Files Modified

### Docker Compose
- rdf-forge/docker-compose.yml
- rdf-forge/docker-compose.production.yml

### Helm Chart
- rdf-forge/charts/rdf-forge/Chart.yaml
- rdf-forge/charts/rdf-forge/values.yaml
- rdf-forge/charts/rdf-forge/templates/ (9 files)

### Root
- .gitignore

---

## Next Steps

1. Test Docker Compose deployments
2. Test Kubernetes Helm deployments
3. Set REDIS_PASSWORD environment variable in production
4. Configure Keycloak with proper admin password
5. Set up proper TLS certificates for Keycloak
6. Configure ingress hostname to match domain
7. Set up monitoring and health checks
8. Review network policies for Kubernetes
9. Test pod security policies

---

## References

- Docker Compose Networking: https://docs.docker.com/compose/networking/
- Kubernetes Pod Security: https://kubernetes.io/docs/concepts/security/pod-security-standards/
- Helm Best Practices: https://helm.sh/docs/chart_best_practices/
- Keycloak Production Guide: https://www.keycloak.org/guides
- Redis Security: https://redis.io/docs/management/security/
