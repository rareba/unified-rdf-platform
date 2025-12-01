package io.rdfforge.triplestore.service;

import io.rdfforge.triplestore.connector.FusekiConnector;
import io.rdfforge.triplestore.connector.TriplestoreConnector;
import io.rdfforge.triplestore.connector.TriplestoreConnector.*;
import io.rdfforge.triplestore.entity.TriplestoreConnectionEntity;
import io.rdfforge.triplestore.entity.TriplestoreConnectionEntity.*;
import io.rdfforge.triplestore.repository.TriplestoreConnectionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Transactional
public class TriplestoreService {
    
    private final TriplestoreConnectionRepository repository;
    private final Map<UUID, TriplestoreConnector> connectorCache = new ConcurrentHashMap<>();
    
    public TriplestoreService(TriplestoreConnectionRepository repository) {
        this.repository = repository;
    }
    
    public List<TriplestoreConnectionEntity> getConnections(UUID projectId) {
        if (projectId != null) {
            return repository.findByProjectIdOrderByNameAsc(projectId);
        }
        return repository.findAll();
    }
    
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
        }).orElseThrow(() -> new RuntimeException("Connection not found: " + id));
    }
    
    public void deleteConnection(UUID id) {
        connectorCache.remove(id);
        repository.deleteById(id);
    }
    
    public Map<String, Object> testConnection(UUID id) {
        TriplestoreConnectionEntity connection = repository.findById(id)
            .orElseThrow(() -> new RuntimeException("Connection not found: " + id));
        
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
    
    public List<GraphInfo> listGraphs(UUID connectionId) {
        TriplestoreConnector connector = getConnector(connectionId);
        return connector.listGraphs();
    }
    
    public QueryResult executeQuery(UUID connectionId, String query, String graph) {
        TriplestoreConnector connector = getConnector(connectionId);
        return connector.executeQuery(query, graph);
    }
    
    public void executeUpdate(UUID connectionId, String update, String graph) {
        TriplestoreConnector connector = getConnector(connectionId);
        connector.executeUpdate(update, graph);
    }
    
    public Map<String, Object> uploadRdf(UUID connectionId, String graphUri, String content, String format) {
        TriplestoreConnector connector = getConnector(connectionId);
        long startTime = System.currentTimeMillis();
        connector.uploadRdf(graphUri, content, format);
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
    
    public String exportGraph(UUID connectionId, String graphUri, String format) {
        TriplestoreConnector connector = getConnector(connectionId);
        return connector.exportGraph(graphUri, format);
    }
    
    public List<ResourceInfo> listResources(UUID connectionId, String graphUri, int limit, int offset) {
        TriplestoreConnector connector = getConnector(connectionId);
        return connector.listResources(graphUri, limit, offset);
    }
    
    public ResourceInfo getResource(UUID connectionId, String graphUri, String resourceUri) {
        TriplestoreConnector connector = getConnector(connectionId);
        return connector.getResource(graphUri, resourceUri);
    }
    
    public List<ResourceInfo> searchResources(UUID connectionId, String graphUri, String searchTerm) {
        TriplestoreConnector connector = getConnector(connectionId);
        
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
            """, graphUri, searchTerm, searchTerm);
        
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
                .orElseThrow(() -> new RuntimeException("Connection not found: " + id));
            return createConnector(connection);
        });
    }
    
    private TriplestoreConnector createConnector(TriplestoreConnectionEntity connection) {
        String username = null;
        String password = null;
        
        if (connection.getAuthType() == AuthType.BASIC && connection.getAuthConfig() != null) {
            username = (String) connection.getAuthConfig().get("username");
            password = (String) connection.getAuthConfig().get("password");
        }
        
        return switch (connection.getType()) {
            case FUSEKI -> new FusekiConnector(connection.getUrl(), username, password);
            default -> throw new UnsupportedOperationException(
                "Connector not implemented for type: " + connection.getType()
            );
        };
    }
}
