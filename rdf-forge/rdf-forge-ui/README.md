# RDF Forge UI

A modern Angular 21 application for managing RDF data, SHACL validation, and data pipelines. Built with PrimeNG components and supporting multiple deployment configurations.

## Table of Contents

- [Overview](#overview)
- [Prerequisites](#prerequisites)
- [Quick Start](#quick-start)
- [Project Structure](#project-structure)
- [Build Configurations](#build-configurations)
- [Development](#development)
- [Testing](#testing)
- [Docker Deployment](#docker-deployment)
- [Environment Configuration](#environment-configuration)
- [Features](#features)
- [Technology Stack](#technology-stack)

---

## Overview

RDF Forge UI provides a comprehensive interface for:

- **Dashboard** - Overview of system status and recent activities
- **Pipeline Management** - Create, edit, and execute data pipelines
- **SHACL Studio** - Design and validate SHACL shapes
- **Job Monitoring** - Track pipeline execution and job status
- **Data Management** - Upload, browse, and manage RDF data
- **Dimension Management** - Manage cube dimensions
- **Triplestore Browser** - Query and explore the triplestore

---

## Prerequisites

- **Node.js** 20.x or later
- **npm** 11.x or later
- **Angular CLI** 21.x

```bash
# Install Angular CLI globally
npm install -g @angular/cli@21
```

---

## Quick Start

### Local Development (No Backend)

```bash
# Install dependencies
npm install --legacy-peer-deps

# Start development server
ng serve

# Open browser at http://localhost:4200
```

### With Docker Backend (Offline Mode)

```bash
# From parent directory (rdf-forge)
docker compose -f docker-compose.offline.yml up -d

# Start UI with offline configuration
ng serve --configuration offline
```

### With Docker Backend (Online Mode with Auth)

```bash
# From parent directory (rdf-forge)
docker compose -f docker-compose.online.yml up -d

# Start UI with online configuration
ng serve --configuration online
```

---

## Project Structure

```
rdf-forge-ui/
├── src/
│   ├── app/
│   │   ├── core/                    # Core module (guards, interceptors, services)
│   │   ├── shared/                  # Shared components, pipes, directives
│   │   ├── features/                # Feature modules
│   │   │   ├── cube/               # Cube wizard
│   │   │   ├── dashboard/          # Dashboard
│   │   │   ├── data/               # Data management
│   │   │   ├── dimension/          # Dimension management
│   │   │   ├── job/                # Job monitoring
│   │   │   ├── pipeline/           # Pipeline designer
│   │   │   ├── settings/           # Application settings
│   │   │   ├── shacl/              # SHACL studio
│   │   │   └── triplestore/        # Triplestore browser
│   │   ├── app.config.ts           # Application configuration
│   │   ├── app.routes.ts           # Route definitions
│   │   ├── app.ts                  # Root component
│   │   ├── app.html                # Root template
│   │   └── app.scss                # Root styles
│   ├── environments/               # Environment configurations
│   │   ├── environment.ts          # Default environment
│   │   ├── environment.offline.ts  # Offline mode (no auth)
│   │   └── environment.online.ts   # Online mode (with Keycloak)
│   ├── styles.scss                 # Global styles
│   ├── main.ts                     # Application entry point
│   └── index.html                  # HTML entry point
├── public/                         # Static assets
├── angular.json                    # Angular CLI configuration
├── package.json                    # Dependencies and scripts
├── tsconfig.json                   # TypeScript configuration
├── tsconfig.app.json              # App-specific TypeScript config
├── tsconfig.spec.json             # Test TypeScript config
├── karma.conf.js                  # Karma test configuration
├── eslint.config.js               # ESLint configuration
├── Dockerfile                     # Docker build configuration
└── nginx.conf                     # Nginx configuration for production
```

### Feature Modules

| Module | Path | Description |
|--------|------|-------------|
| **Dashboard** | `/dashboard` | System overview and statistics |
| **Pipeline** | `/pipelines` | Pipeline list and designer |
| **SHACL** | `/shacl` | SHACL studio and shape editor |
| **Jobs** | `/jobs` | Job list and monitor |
| **Data** | `/data` | Data management |
| **Dimensions** | `/dimensions` | Dimension management |
| **Triplestore** | `/triplestore` | SPARQL query interface |
| **Cube** | `/cube` | Cube wizard |
| **Settings** | `/settings` | Application settings |

---

## Build Configurations

The application supports multiple build configurations defined in [`angular.json`](angular.json):

### Development (Default)

```bash
ng build --configuration development
# or simply
ng build
```

- No optimization
- Source maps enabled
- Fast builds for development

### Production

```bash
ng build --configuration production
```

- Full optimization
- Output hashing for cache busting
- Budget enforcement

### Offline Mode

```bash
ng build --configuration offline
```

- Authentication disabled
- Connects to local backend services
- Uses [`environment.offline.ts`](src/environments/environment.offline.ts)

### Online Mode

```bash
ng build --configuration online
```

- Keycloak authentication enabled
- Production optimizations
- Uses [`environment.online.ts`](src/environments/environment.online.ts)

### Configuration Comparison

| Feature | Development | Production | Offline | Online |
|---------|-------------|------------|---------|--------|
| Optimization | ❌ | ✅ | ❌ | ✅ |
| Source Maps | ✅ | ❌ | ✅ | ❌ |
| Output Hashing | ❌ | ✅ | ❌ | ✅ |
| Authentication | Depends | Depends | ❌ | ✅ |
| Budget Checks | ❌ | ✅ | ❌ | ✅ |

---

## Development

### Development Server

```bash
# Default development server
ng serve

# With specific configuration
ng serve --configuration offline
ng serve --configuration online

# With custom port
ng serve --port 4201

# Open browser automatically
ng serve --open
```

The development server runs at `http://localhost:4200` by default with hot reload enabled.

### Code Scaffolding

```bash
# Generate a new component
ng generate component features/my-feature/my-component

# Generate a service
ng generate service core/services/my-service

# Generate a module
ng generate module features/my-feature

# Available schematics
ng generate --help
```

### Linting

```bash
# Run ESLint
ng lint

# Fix auto-fixable issues
ng lint --fix
```

### Code Style

The project uses Prettier for code formatting:

```json
{
  "printWidth": 100,
  "singleQuote": true
}
```

---

## Testing

### Unit Tests

Unit tests use Karma with Jasmine:

```bash
# Run tests once
ng test --watch=false

# Run tests with watch mode
ng test

# Run tests with code coverage
ng test --code-coverage

# Run specific test file
ng test --include=**/dashboard.spec.ts
```

### Test Configuration

Tests are configured in [`karma.conf.js`](karma.conf.js) and [`tsconfig.spec.json`](tsconfig.spec.json).

Coverage reports are generated in `coverage/` directory.

### Test Structure

```typescript
// Example test file: dashboard.spec.ts
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DashboardComponent } from './dashboard';

describe('DashboardComponent', () => {
  let component: DashboardComponent;
  let fixture: ComponentFixture<DashboardComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [DashboardComponent]
    }).compileComponents();

    fixture = TestBed.createComponent(DashboardComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
```

### Coverage Exclusions

The following are excluded from coverage:

- `src/test.ts`
- `src/**/*.spec.ts`

---

## Docker Deployment

### Building the Docker Image

```bash
# Build with default (production) configuration
docker build -t rdf-forge-ui .

# Build with offline configuration
docker build --build-arg BUILD_CONFIG=offline -t rdf-forge-ui:offline .

# Build with online configuration
docker build --build-arg BUILD_CONFIG=online -t rdf-forge-ui:online .
```

### Dockerfile Overview

The Dockerfile uses a multi-stage build:

1. **Builder Stage**: Node.js 20 Alpine for building the Angular app
2. **Production Stage**: Nginx Alpine for serving static files

```dockerfile
# Stage 1: Build
FROM node:20-alpine AS builder
ARG BUILD_CONFIG=production
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci --legacy-peer-deps
COPY . .
RUN npm run build -- --configuration ${BUILD_CONFIG}

# Stage 2: Serve
FROM nginx:stable-alpine
COPY --from=builder /app/dist/rdf-forge-ui/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
```

### Nginx Configuration

The [`nginx.conf`](nginx.conf) handles:

- API proxy to backend gateway
- SPA routing (fallback to index.html)

```nginx
server {
    listen 80;
    
    location /api {
        proxy_pass http://gateway:8000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location / {
        root /usr/share/nginx/html;
        index index.html index.htm;
        try_files $uri $uri/ /index.html;
    }
}
```

### Running with Docker Compose

See the parent [DEPLOYMENT.md](../DEPLOYMENT.md) for full deployment instructions.

---

## Environment Configuration

### Environment Files

| File | Purpose |
|------|---------|
| [`environment.ts`](src/environments/environment.ts) | Default/development environment |
| [`environment.offline.ts`](src/environments/environment.offline.ts) | Offline mode (no authentication) |
| [`environment.online.ts`](src/environments/environment.online.ts) | Online mode (Keycloak authentication) |

### Environment Structure

```typescript
export const environment = {
  production: boolean,        // Production mode flag
  apiBaseUrl: string,         // API base URL (e.g., '/api/v1')
  auth: {
    enabled: boolean,         // Enable/disable authentication
    keycloak?: {              // Keycloak configuration (when enabled)
      url: string,            // Keycloak server URL
      realm: string,          // Keycloak realm
      clientId: string        // OAuth2 client ID
    }
  }
};
```

### Offline Mode Configuration

```typescript
// environment.offline.ts
export const environment = {
  production: false,
  apiBaseUrl: '/api/v1',
  auth: {
    enabled: false
  }
};
```

### Online Mode Configuration

```typescript
// environment.online.ts
export const environment = {
  production: true,
  apiBaseUrl: '/api/v1',
  auth: {
    enabled: true,
    keycloak: {
      url: 'http://localhost:8080',
      realm: 'rdfforge',
      clientId: 'rdf-forge-ui'
    }
  }
};
```

### Using Environment Variables

Import and use the environment in your code:

```typescript
import { environment } from '../environments/environment';

if (environment.auth.enabled) {
  // Initialize Keycloak
}
```

---

## Features

### Dashboard

- System health overview
- Recent activity feed
- Quick action buttons
- Statistics widgets

### Pipeline Designer

- Visual pipeline editor
- Drag-and-drop step configuration
- Pipeline validation
- Execution history

### SHACL Studio

- Shape editor with syntax highlighting
- SHACL validation
- Shape visualization
- Import/export shapes

### Job Monitor

- Real-time job status
- Log viewer
- Job history
- Retry/cancel operations

### Data Manager

- File upload (RDF formats)
- Data browser
- Graph management
- Export functionality

### Triplestore Browser

- SPARQL query editor
- Query history
- Result visualization
- Named graph browser

---

## Technology Stack

### Core Framework

- **Angular 21** - Modern web framework
- **TypeScript 5.9** - Type-safe JavaScript
- **RxJS 7.8** - Reactive programming

### UI Components

- **PrimeNG 20** - Rich UI component library
- **PrimeFlex 4** - CSS utility library
- **PrimeIcons 7** - Icon library

### Authentication

- **Keycloak Angular 20** - Keycloak integration
- **Keycloak JS 26** - JavaScript adapter

### Additional Libraries

- **ngx-flowchart** - Pipeline visualization

### Development Tools

- **Angular CLI 21** - Development tooling
- **ESLint** - Code linting
- **Prettier** - Code formatting
- **Karma/Jasmine** - Unit testing
- **Vitest** - Alternative test runner

---

## Scripts Reference

| Script | Command | Description |
|--------|---------|-------------|
| Start | `npm start` | Start development server |
| Build | `npm run build` | Build for production |
| Watch | `npm run watch` | Build with watch mode |
| Test | `npm test` | Run unit tests |
| Lint | `npm run lint` | Run ESLint |

---

## Browser Support

The application targets modern evergreen browsers:

- Chrome (latest)
- Firefox (latest)
- Safari (latest)
- Edge (latest)

---

## Related Documentation

- [Deployment Guide](../DEPLOYMENT.md) - Full stack deployment instructions
- [Architecture Analysis](../ARCHITECTURE_ANALYSIS.md) - System architecture overview
- [Angular CLI Reference](https://angular.dev/tools/cli) - Official Angular CLI documentation
