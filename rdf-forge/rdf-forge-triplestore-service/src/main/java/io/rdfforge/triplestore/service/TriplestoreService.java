package io.rdfforge.triplestore.service;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.rdfforge.common.exception.TriplestoreConnectionException;
import io.rdfforge.common.metrics.RdfForgeMetrics;
import io.rdfforge.triplestore.connector.TriplestoreConnector;
import io.rdfforge.triplestore.connector.TriplestoreConnector.*;
import io.rdfforge.triplestore.connector.TriplestoreProviderInfo;
import io.rdfforge.triplestore.connector.TriplestoreProviderRegistry;
import io.rdfforge.triplestore.entity.TriplestoreConnectionEntity;
import io.rdfforge.triplestore.entity.TriplestoreConnectionEntity.*;
import io.rdfforge.triplestore.repository.TriplestoreConnectionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Transactional
@Slf4j
public class TriplestoreService {

    // SPARQL query timeout in seconds (default: 30 seconds)
    @Value("${sparql.query.timeout.seconds:30}")
    private int queryTimeoutSeconds;

    // Maximum number of results to return (default: 10000)
    @Value("${sparql.query.max.results:10000}")
    private int maxQueryResults;

    private final TriplestoreConnectionRepository repository;
    private final TriplestoreProviderRegistry providerRegistry;
    private final Map<UUID, TriplestoreConnector> connectorCache = new ConcurrentHashMap<>();

    private final Timer sparqlQueryTimer;
    private final Timer sparqlUpdateTimer;
    private final Timer rdfUploadTimer;

    public TriplestoreService(TriplestoreConnectionRepository repository,
                              TriplestoreProviderRegistry providerRegistry,
                              MeterRegistry meterRegistry) {
        this.repository = repository;
        this.providerRegistry = providerRegistry;

        this.sparqlQueryTimer = Timer.builder(RdfForgeMetrics.SPARQL_QUERY_DURATION)
                .description("Duration of SPARQL query executions")
                .publishPercentileHistogram()
                .register(meterRegistry);
        this.sparqlUpdateTimer = Timer.builder(RdfForgeMetrics.SPARQL_UPDATE_DURATION)
                .description("Duration of SPARQL update operations")
                .publishPercentileHistogram()
                .register(meterRegistry);
        this.rdfUploadTimer = Timer.builder(RdfForgeMetrics.RDF_UPLOAD_DURATION)
                .description("Duration of RDF upload operations")
                .publishPercentileHistogram()
                .register(meterRegistry);
    }

    /**
     * Get all available triplestore providers.
     * @return List of provider information
     */
    @Transactional(readOnly = true)
    public List<TriplestoreProviderInfo> getAvailableProviders() {
        return providerRegistry.getAvailableProviders();
    }

    /**
     * Get provider info for a specific type.
     * @param type The provider type
     * @return Provider information if found
     */
    @Transactional(readOnly = true)
    public Optional<TriplestoreProviderInfo> getProviderInfo(String type) {
        return providerRegistry.getProviderInfo(type);
    }
    
    @Transactional(readOnly = true)
    public List<TriplestoreConnectionEntity> getConnections(UUID projectId) {
        if (projectId != null) {
            return repository.findByProjectIdOrderByNameAsc(projectId);
        }
        return repository.findAll();
    }
    
    @Transactional(readOnly = true)
    public Optional<TriplestoreConnectionEntity> getConnection(UUID id) {
        return repository.findById(id);
    }
    
    public TriplestoreConnectionEntity createConnection(TriplestoreConnectionEntity connection, UUID userId) {
        connection.setCreatedBy(userId);
        connection.setCreatedAt(Instant.now());
        connection.setHealthStatus(HealthStatus.UNKNOWN);
        return repository.save(connection);
    }
    
    public TriplestoreConnectionEntity updateConnection(UUID id, TriplestoreConnectionEntity updates) {
        return repository.findById(id).map(existing -> {
            existing.setName(updates.getName());
            existing.setType(updates.getType());
            existing.setUrl(updates.getUrl());
            existing.setDefaultGraph(updates.getDefaultGraph());
            existing.setAuthType(updates.getAuthType());
            existing.setAuthConfig(updates.getAuthConfig());
            existing.setIsDefault(updates.getIsDefault());
            connectorCache.remove(id);
            return repository.save(existing);
        }).orElseThrow(() -> new TriplestoreConnectionException("Connection not found: " + id));
    }

    public void deleteConnection(UUID id) {
        connectorCache.remove(id);
        repository.deleteById(id);
    }
    
    public Map<String, Object> testConnection(UUID id) {
        TriplestoreConnectionEntity connection = repository.findById(id)
            .orElseThrow(() -> new TriplestoreConnectionException("Connection not found: " + id));
        
        TriplestoreConnector connector = createConnector(connection);
        long startTime = System.currentTimeMillis();
        boolean success = connector.testConnection();
        long latency = System.currentTimeMillis() - startTime;
        
        connection.setHealthStatus(success ? HealthStatus.HEALTHY : HealthStatus.UNHEALTHY);
        connection.setLastHealthCheck(Instant.now());
        repository.save(connection);
        
        return Map.of(
            "success", success,
            "latencyMs", latency,
            "message", success ? "Connection successful" : "Connection failed"
        );
    }
    
    @Transactional(readOnly = true)
    public List<GraphInfo> listGraphs(UUID connectionId) {
        TriplestoreConnector connector = getConnector(connectionId);
        return connector.listGraphs();
    }
    
    @Transactional(readOnly = true)
    public QueryResult executeQuery(UUID connectionId, String query, String graph) {
        TriplestoreConnector connector = getConnector(connectionId);

        // Add query timeout hint if not present
        String timedQuery = addQueryTimeout(query);

        // Add result limit if not present
        String limitedQuery = addResultLimit(timedQuery);

        try {
            return sparqlQueryTimer.record(() -> {
                QueryResult result = connector.executeQuery(limitedQuery, graph);
                log.debug("SPARQL query returned {} results", result.bindings().size());
                return result;
            });
        } catch (Exception e) {
            log.error("SPARQL query failed for connection {}: {}", connectionId, e.getMessage());
            throw new TriplestoreConnectionException("Query execution failed: " + e.getMessage());
        }
    }

    /**
     * Add timeout hint to SPARQL query if not already present.
     */
    private String addQueryTimeout(String query) {
        if (query == null || query.contains("timeout") || query.contains("maxQueryTime")) {
            return query;
        }

        // Add timeout as a comment/hint for compatible triplestores
        return "# query timeout: " + queryTimeoutSeconds + "s\n" + query;
    }

    /**
     * Add result limit to SPARQL query if not already present.
     */
    private String addResultLimit(String query) {
        if (query == null || query.toUpperCase().contains("LIMIT")) {
            return query;
        }

        // Add LIMIT clause for SELECT queries
        String upperQuery = query.toUpperCase().trim();
        if (upperQuery.startsWith("SELECT") && !upperQuery.contains("LIMIT")) {
            return query + "\nLIMIT " + maxQueryResults;
        }

        return query;
    }
    
    public void executeUpdate(UUID connectionId, String update, String graph) {
        TriplestoreConnector connector = getConnector(connectionId);
        sparqlUpdateTimer.record(() -> connector.executeUpdate(update, graph));
    }
    
    public Map<String, Object> uploadRdf(UUID connectionId, String graphUri, String content, String format) {
        TriplestoreConnector connector = getConnector(connectionId);
        long startTime = System.currentTimeMillis();
        rdfUploadTimer.record(() -> connector.uploadRdf(graphUri, content, format));
        long duration = System.currentTimeMillis() - startTime;
        
        List<GraphInfo> graphs = connector.listGraphs();
        long tripleCount = graphs.stream()
            .filter(g -> g.uri().equals(graphUri))
            .findFirst()
            .map(GraphInfo::tripleCount)
            .orElse(0L);
        
        return Map.of(
            "success", true,
            "triplesLoaded", tripleCount,
            "durationMs", duration
        );
    }
    
    public void deleteGraph(UUID connectionId, String graphUri) {
        TriplestoreConnector connector = getConnector(connectionId);
        connector.deleteGraph(graphUri);
    }
    
    @Transactional(readOnly = true)
    public String exportGraph(UUID connectionId, String graphUri, String format) {
        TriplestoreConnector connector = getConnector(connectionId);
        return connector.exportGraph(graphUri, format);
    }
    
    @Transactional(readOnly = true)
    public List<ResourceInfo> listResources(UUID connectionId, String graphUri, int limit, int offset) {
        TriplestoreConnector connector = getConnector(connectionId);
        return connector.listResources(graphUri, limit, offset);
    }
    
    @Transactional(readOnly = true)
    public ResourceInfo getResource(UUID connectionId, String graphUri, String resourceUri) {
        TriplestoreConnector connector = getConnector(connectionId);
        return connector.getResource(graphUri, resourceUri);
    }
    
    @Transactional(readOnly = true)
    public List<ResourceInfo> searchResources(UUID connectionId, String graphUri, String searchTerm) {
        TriplestoreConnector connector = getConnector(connectionId);

        // Sanitize inputs to prevent SPARQL injection
        String sanitizedSearch = sanitizeSparqlLiteral(searchTerm);
        String sanitizedGraph = sanitizeSparqlIri(graphUri);

        String query = String.format("""
            PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            SELECT DISTINCT ?s ?type ?label WHERE {
              GRAPH <%s> {
                { ?s rdfs:label ?label FILTER(CONTAINS(LCASE(STR(?label)), LCASE("%s"))) }
                UNION
                { FILTER(CONTAINS(LCASE(STR(?s)), LCASE("%s"))) }
                OPTIONAL { ?s a ?type }
              }
            }
            LIMIT 50
            """, sanitizedGraph, sanitizedSearch, sanitizedSearch);
        
        QueryResult result = connector.executeQuery(query, graphUri);
        List<ResourceInfo> resources = new ArrayList<>();
        
        for (Map<String, RdfValue> binding : result.bindings()) {
            RdfValue sValue = binding.get("s");
            RdfValue typeValue = binding.get("type");
            RdfValue labelValue = binding.get("label");
            
            if (sValue != null) {
                resources.add(new ResourceInfo(
                    sValue.value(),
                    typeValue != null ? List.of(typeValue.value()) : List.of(),
                    labelValue != null ? labelValue.value() : null,
                    List.of()
                ));
            }
        }
        
        return resources;
    }
    
    private TriplestoreConnector getConnector(UUID connectionId) {
        return connectorCache.computeIfAbsent(connectionId, id -> {
            TriplestoreConnectionEntity connection = repository.findById(id)
                .orElseThrow(() -> {
                    log.error("Triplestore connection not found: {}", id);
                    return new TriplestoreConnectionException("Connection not found: " + id);
                });
            log.info("Creating connector for triplestore: {} (type: {})",
                connection.getName(), connection.getType());
            return createConnector(connection);
        });
    }

    private TriplestoreConnector createConnector(TriplestoreConnectionEntity connection) {
        try {
            // Use the provider registry to create the connector
            // This allows new providers to be added simply by implementing TriplestoreProvider
            TriplestoreConnector connector = providerRegistry.createConnector(connection);
            if (connector == null) {
                throw new TriplestoreConnectionException(
                    "Failed to create connector for type: " + connection.getType());
            }
            return connector;
        } catch (Exception e) {
            log.error("Failed to create connector for triplestore: {} ({})",
                connection.getName(), connection.getType(), e);
            throw new TriplestoreConnectionException(
                "Failed to create connector: " + e.getMessage());
        }
    }

    /**
     * Sanitize a string value for safe inclusion in a SPARQL literal.
     * Escapes characters that could break out of a double-quoted string.
     */
    private String sanitizeSparqlLiteral(String value) {
        if (value == null) return "";
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }

    /**
     * Sanitize an IRI for safe inclusion in a SPARQL query.
     * Validates the IRI format and rejects malicious input.
     */
    private String sanitizeSparqlIri(String iri) {
        if (iri == null || iri.isBlank()) {
            throw new IllegalArgumentException("Graph URI cannot be empty");
        }
        // Reject IRIs containing characters that could break out of angle brackets
        if (iri.contains(">") || iri.contains("<") || iri.contains("{") || iri.contains("}") ||
            iri.contains("|") || iri.contains("\\") || iri.contains("^") || iri.contains("`")) {
            throw new IllegalArgumentException("Invalid characters in graph URI: " + iri);
        }
        // Basic URI validation
        if (!iri.matches("^[a-zA-Z][a-zA-Z0-9+.-]*:.*$")) {
            throw new IllegalArgumentException("Invalid URI format: " + iri);
        }
        return iri;
    }

    /**
     * Clear the connector cache to force reconnection.
     * Useful when connection settings change or to fix stale connections.
     */
    public void clearConnectorCache(UUID connectionId) {
        if (connectionId != null) {
            connectorCache.remove(connectionId);
            log.info("Cleared connector cache for connection: {}", connectionId);
        } else {
            connectorCache.clear();
            log.info("Cleared all connector cache entries");
        }
    }
}
