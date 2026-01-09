# Operations Catalog

This document lists all built-in operations available in RDF Forge.

## Data Sources (SOURCE)

### load-csv
Load data from a CSV file.

**Parameters:**
| Name | Type | Required | Description |
|------|------|----------|-------------|
| filePath | String | Yes | Path to CSV file |
| delimiter | String | No | Column delimiter (default: `,`) |
| hasHeader | Boolean | No | First row is header (default: true) |
| encoding | String | No | File encoding (default: UTF-8) |

### load-json
Load data from a JSON file.

**Parameters:**
| Name | Type | Required | Description |
|------|------|----------|-------------|
| filePath | String | Yes | Path to JSON file |
| jsonPath | String | No | JSONPath expression to extract array |

### http-get
Fetch data from an HTTP endpoint.

**Parameters:**
| Name | Type | Required | Description |
|------|------|----------|-------------|
| url | String | Yes | URL to fetch |
| headers | Map | No | HTTP headers |
| format | String | No | Response format (json, csv) |

### s3-get
Load file from S3-compatible storage.

**Parameters:**
| Name | Type | Required | Description |
|------|------|----------|-------------|
| bucket | String | Yes | Bucket name |
| key | String | Yes | Object key |
| endpoint | String | No | S3 endpoint URL |

---

## Transformations (TRANSFORM)

### filter
Filter rows based on column values.

**Parameters:**
| Name | Type | Required | Description |
|------|------|----------|-------------|
| column | String | Yes | Column to filter |
| operator | String | Yes | Comparison (equals, contains, startsWith) |
| value | String | Yes | Value to compare |

### map
Transform row values.

**Parameters:**
| Name | Type | Required | Description |
|------|------|----------|-------------|
| mappings | Map | Yes | Column -> expression mappings |

### map-to-rdf
Convert tabular data to RDF triples.

**Parameters:**
| Name | Type | Required | Description |
|------|------|----------|-------------|
| baseUri | String | Yes | Base URI for resources |
| subjectColumn | String | No | Column for subject URI |
| subjectTemplate | String | No | Template: `{column}` syntax |
| typeUri | String | No | RDF type for resources |
| propertyMappings | Map | Yes | Column -> property URI |
| datatypeMappings | Map | No | Column -> XSD datatype |

**Example:**
```json
{
  "baseUri": "http://example.org/",
  "subjectColumn": "id",
  "typeUri": "http://example.org/Person",
  "propertyMappings": {
    "name": "http://xmlns.com/foaf/0.1/name",
    "email": "http://xmlns.com/foaf/0.1/mbox"
  }
}
```

---

## Validation (VALIDATION)

### shacl-validate
Validate RDF data against SHACL shapes.

**Parameters:**
| Name | Type | Required | Description |
|------|------|----------|-------------|
| shapesUri | String | Yes | URI of shapes graph |
| failOnError | Boolean | No | Fail pipeline on violations |

---

## Outputs (OUTPUT)

### write-rdf
Write RDF model to file.

**Parameters:**
| Name | Type | Required | Description |
|------|------|----------|-------------|
| filePath | String | Yes | Output file path |
| format | String | No | Format: turtle, json-ld, n-triples |

---

## Destination Providers

### triplestore
Publish to SPARQL triplestore (Fuseki, GraphDB).

**Config:**
- `triplestoreId`: Connection ID
- `graph`: Target graph URI
- `clearGraph`: Clear before publishing

### file
Write to local file system.

**Config:**
- `path`: Output path
- `format`: RDF format
- `compress`: gzip output

### s3
Publish to S3-compatible storage.

**Config:**
- `bucket`: Target bucket
- `key`: Object key
- `endpoint`: S3 endpoint

### gitlab
Publish to GitLab repository.

**Config:**
- `projectId`: GitLab project
- `branch`: Target branch
- `path`: File path in repo
