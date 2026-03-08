# RDF Forge Helm Chart Templates

This directory contains the Kubernetes deployment templates for RDF Forge services.

## Template Files

- **_helpers.tpl**: Common template helpers and functions used across all templates
- **NOTES.txt**: Post-installation notes displayed to users
- **serviceaccount.yaml**: Kubernetes ServiceAccount for RBAC
- **deployment-gateway.yaml**: Main API Gateway deployment
- **service-gateway.yaml**: Service exposing the gateway
- **ingress.yaml**: Ingress controller configuration for external access
- **poddisruptionbudget.yaml**: Pod Disruption Budget for high availability
- **hpa.yaml**: Horizontal Pod Autoscaler for auto-scaling

## Values Structure

All configuration is defined in `values.yaml` and follows this hierarchy:

```
global:
  - Global settings (image registry, pull secrets)

services:
  - gateway, pipeline, shacl, job, data, dimension, triplestore, ui
  - Each with: replicas, port, resources, probes

ingress:
  - enabled, className, annotations, hosts, tls

podDisruptionBudget:
  - enabled, minAvailable, maxUnavailable

autoscaling:
  - enabled, minReplicas, maxReplicas, target metrics
```

## Extending the Chart

To add additional services (e.g., pipeline-service, data-service):

1. Add service configuration to `values.yaml`
2. Create `deployment-<service>.yaml` based on `deployment-gateway.yaml`
3. Create `service-<service>.yaml` based on `service-gateway.yaml`

Example:
```bash
cp templates/deployment-gateway.yaml templates/deployment-pipeline.yaml
# Edit to change:
# - metadata.name: rdf-forge-pipeline
# - .Values.services.pipeline (instead of gateway)
# - environment variables specific to pipeline service
```

## Required Kubernetes Objects

When deploying this chart, ensure your cluster has:

- Ingress Controller (for Ingress support)
- cert-manager (optional, for TLS cert management)
- Metrics Server (for HPA)
- Service account with appropriate RBAC (auto-created)
