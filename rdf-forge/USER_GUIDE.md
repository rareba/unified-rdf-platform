# RDF Forge - User Guide

Welcome to RDF Forge! This guide will help you get started with creating, managing, and publishing RDF data cubes.

---

## Table of Contents

1. [Getting Started](#getting-started)
2. [Dashboard](#dashboard)
3. [Data Management](#data-management)
4. [Creating Cubes](#creating-cubes)
5. [Pipeline Designer](#pipeline-designer)
6. [Job Monitoring](#job-monitoring)
7. [Triplestore Browser](#triplestore-browser)
8. [Settings](#settings)
9. [Troubleshooting](#troubleshooting)

---

## Getting Started

### Login

1. Navigate to the RDF Forge URL
2. Click "Login" to authenticate with Keycloak
3. Enter your credentials
4. You'll be redirected to the Dashboard

### First Time Setup

1. **Check System Health**: Go to Settings → System and verify all services are running
2. **Configure Triplestore**: Add your triplestore connection in Triplestore Browser
3. **Upload Sample Data**: Try uploading a CSV file to test the system

---

## Dashboard

The Dashboard provides an overview of your RDF Forge instance:

### Statistics Cards
- **Pipelines**: Total number of pipelines created
- **Jobs**: Running, completed, and failed jobs
- **Data Sources**: Uploaded datasets
- **SHACL Shapes**: Validation shapes defined

### Quick Actions
- Create New Pipeline
- Upload Data
- Create Cube
- Validate Data

### Recent Activity
View recent operations and their status

---

## Data Management

### Uploading Data

1. Navigate to **Data** → **Data Manager**
2. Click the **Upload** button
3. Select your file (CSV, JSON, Excel, Parquet)
4. Add optional metadata (name, description)
5. Click **Upload**

### Supported Formats

| Format | Extensions | Notes |
|--------|------------|-------|
| CSV | .csv | UTF-8 encoding recommended |
| Excel | .xlsx, .xls | First sheet only |
| JSON | .json | Array of objects |
| Parquet | .parquet | Columnar format |

### Data Preview

After upload, click on a data source to:
- Preview first 100 rows
- View column statistics
- Check data types
- Download the file

### Column Types

RDF Forge automatically detects:
- **String**: Text data
- **Number**: Integer or decimal
- **Date/Time**: Temporal data
- **Boolean**: True/False values
- **URI**: Linked data references

---

## Creating Cubes

### Using the Cube Wizard

The Cube Wizard guides you through creating a data cube in 6 steps:

#### Step 1: Basic Information
- **Cube Name**: Human-readable name
- **Cube ID**: Auto-generated from name (editable)
- **Description**: Optional description

#### Step 2: Select Data Source
- Choose from uploaded datasets
- Or upload a new file

#### Step 3: Map Columns
For each column, specify its role:

| Role | Description | Example |
|------|-------------|---------|
| **Dimension** | Categorical axis | Year, Region, Product |
| **Measure** | Numerical value | Sales, Population |
| **Attribute** | Metadata | Unit, Currency |
| **Key** | Unique identifier | ID |
| **Ignore** | Exclude from cube | - |

**Quick Role Toggle**: Use the toolbar buttons for bulk assignment

#### Step 4: Cube Metadata
- **Title**: Display title
- **Publisher**: Organization name
- **License**: Data license
- **Contact**: Contact information

#### Step 5: Validation
- Review cube structure
- Check for errors
- Preview sample observations

#### Step 6: Publish
- Select target triplestore
- Choose graph URI
- Click **Publish Cube**

### Cube Best Practices

1. **Use consistent URIs**: Define prefixes for your domain
2. **Add descriptions**: Help users understand dimensions
3. **Validate before publish**: Catch errors early
4. **Use appropriate scales**: Linear vs logarithmic
5. **Document units**: Always specify measurement units

---

## Pipeline Designer

The Pipeline Designer allows you to create custom data transformation workflows.

### Interface Overview

```
┌─────────────────────────────────────────────────────────────┐
│  Toolbar: Save | Run | Validate | Clear | Import/Export      │
├──────────────┬──────────────────────────────────────────────┤
│              │                                              │
│  Operations  │              Canvas                         │
│  Palette     │                                              │
│              │    ┌─────┐      ┌─────┐      ┌─────┐        │
│  ┌────────┐  │    │ CSV │─────▶│ Map │─────▶│ RDF │        │
│  │ Load   │  │    └─────┘      └─────┘      └─────┘        │
│  │ CSV    │  │                                              │
│  └────────┘  │                                              │
│  ┌────────┐  │    [Nodes connected by edges]                │
│  │ Filter │  │                                              │
│  └────────┘  │                                              │
│              │                                              │
└──────────────┴──────────────────────────────────────────────┘
```

### Adding Operations

1. **Drag and Drop**: Drag operations from palette to canvas
2. **Double Click**: Double-click canvas to add node
3. **Templates**: Use pre-built templates for common workflows

### Operation Types

#### Source Operations
- **Load CSV**: Read CSV files
- **Load JSON**: Read JSON files
- **HTTP Get**: Fetch data from URL
- **S3 Get**: Read from S3/MinIO

#### Transform Operations
- **Map**: Transform fields
- **Filter**: Filter rows
- **Map to RDF**: Convert to RDF
- **Join**: Merge datasets

#### Validation Operations
- **Validate SHACL**: Validate against shapes
- **Validate Schema**: Check structure

#### Output Operations
- **Save RDF**: Write to file
- **Graph Store Put**: Publish to triplestore
- **S3 Put**: Write to S3/MinIO

### Connecting Operations

1. Click the output port of a node
2. Drag to the input port of another node
3. Release to create connection

### Configuration

1. Double-click a node to open configuration
2. Set required parameters
3. Optional parameters use defaults
4. Click **Save** to apply

### Running Pipelines

1. Click **Run** button
2. Set runtime variables (optional)
3. Click **Start**
4. Monitor in Job Monitor

### Templates

Built-in templates:
- **CSV to Cube**: Standard cube creation
- **ETL Pipeline**: Extract, Transform, Load
- **Validation**: Data validation workflow
- **Publishing**: Publish to multiple targets

---

## Job Monitoring

### Job List

Navigate to **Jobs** to see:
- All job executions
- Status (Pending, Running, Completed, Failed)
- Progress bars
- Start/completion times

### Job Details

Click a job to view:
- **Overview**: Status, duration, metrics
- **Logs**: Real-time log streaming
- **Configuration**: Variables and settings

### Log Streaming

Real-time logs show:
- Timestamps
- Log level (DEBUG, INFO, WARN, ERROR)
- Step name
- Message

**Filter Logs**:
- By level: Show only ERROR, WARN, etc.
- By search: Find specific text
- Auto-scroll: Follow new logs

### Actions

- **Cancel**: Stop a running job
- **Retry**: Re-run a failed job
- **Export**: Download logs as JSON/CSV

---

## Triplestore Browser

### Managing Connections

1. Click **Add Connection**
2. Select triplestore type
3. Enter endpoint URLs
4. Configure authentication
5. Test connection
6. Save

### Supported Triplestores

- Apache Fuseki
- Ontotext GraphDB
- Stardog
- OpenLink Virtuoso
- BlazeGraph

### Browsing Graphs

1. Select a connection
2. View list of graphs
3. Click a graph to see:
   - Triple count
   - Last modified
   - Sample data

### SPARQL Query

1. Select target graph
2. Write or paste SPARQL query
3. Click **Execute**
4. View results as:
   - Table
   - JSON
   - Download (CSV, TSV)

### Publishing

From Pipeline Designer or Cube Wizard:
1. Select target triplestore
2. Specify graph URI
3. Choose operation (Create/Replace/Append)
4. Publish

---

## Settings

### Appearance
- **Theme**: Light/Dark/Auto
- **Language**: English, German, French, Italian
- **Date Format**: Regional preferences

### RDF Configuration
- **Default Prefixes**: Common namespaces
- **Custom Prefixes**: Add your own
- **Base URI**: Default base for relative URIs

### Pipeline Defaults
- **Default Timeout**: Maximum execution time
- **Retry Attempts**: Auto-retry on failure
- **Batch Size**: Rows processed per batch

### System
- **Health Check**: Verify all services
- **Export Settings**: Backup configuration
- **Reset**: Restore defaults

---

## Troubleshooting

### Common Issues

#### Cannot Login
- Check Keycloak is running
- Verify user exists in realm
- Check browser console for errors

#### Pipeline Fails
- Check job logs for error details
- Verify data source is accessible
- Check target triplestore connection
- Review pipeline configuration

#### Upload Fails
- Check file format is supported
- Verify file size < 500MB
- Check MinIO is accessible
- Review browser console

#### Slow Performance
- Check system resources
- Review database indexes
- Clear browser cache
- Use smaller batch sizes

### Error Messages

| Error | Cause | Solution |
|-------|-------|----------|
| "Unauthorized" | Session expired | Re-login |
| "Connection refused" | Service down | Check service status |
| "Validation failed" | Invalid data | Review validation errors |
| "Timeout" | Long operation | Increase timeout or optimize |

### Getting Help

1. Check **System** → **Health** for service status
2. Review logs in **Job Monitor**
3. Check [GitHub Issues](https://github.com/rdfforge/rdf-forge/issues)
4. Contact support: support@rdfforge.local

---

## Keyboard Shortcuts

| Shortcut | Action |
|----------|--------|
| `Ctrl + S` | Save (Pipeline) |
| `Ctrl + R` | Run (Pipeline) |
| `Ctrl + Z` | Undo |
| `Ctrl + Shift + Z` | Redo |
| `Delete` | Delete selected node |
| `Escape` | Close dialog |
| `Ctrl + +` | Zoom in |
| `Ctrl + -` | Zoom out |
| `Ctrl + 0` | Reset zoom |

---

## Best Practices

### Data Management
1. **Clean data before upload**: Remove empty rows, fix formatting
2. **Use consistent IDs**: For linking datasets
3. **Document your data**: Add descriptions
4. **Version control**: Use meaningful names

### Pipeline Design
1. **Start simple**: Add complexity gradually
2. **Test incrementally**: Run after major changes
3. **Use templates**: Learn from examples
4. **Document**: Add descriptions to pipelines

### Performance
1. **Batch processing**: For large datasets
2. **Filter early**: Reduce data volume
3. **Cache results**: For repeated operations
4. **Monitor resources**: Watch memory usage

### Collaboration
1. **Use descriptive names**: Clear naming conventions
2. **Share knowledge**: Document decisions
3. **Review pipelines**: Peer review for complex workflows
4. **Version control**: Track changes

---

## Glossary

| Term | Definition |
|------|------------|
| **RDF** | Resource Description Framework - data model |
| **Cube** | Multi-dimensional data structure |
| **Dimension** | Categorical axis (e.g., time, location) |
| **Measure** | Numerical value (e.g., sales, population) |
| **SHACL** | Shapes Constraint Language for validation |
| **Pipeline** | Data transformation workflow |
| **Triplestore** | Database for RDF triples |
| **SPARQL** | Query language for RDF |

---

## Additional Resources

- [Architecture Guide](ARCHITECTURE.md)
- [API Documentation](https://api.rdfforge.local/swagger)
- [Video Tutorials](https://rdfforge.local/tutorials)
- [Community Forum](https://community.rdfforge.local)

---

**Happy Data Publishing!** 🚀
