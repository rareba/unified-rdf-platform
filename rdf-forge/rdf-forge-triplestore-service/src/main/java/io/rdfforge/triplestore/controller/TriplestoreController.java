package io.rdfforge.triplestore.controller;

import io.rdfforge.triplestore.connector.TriplestoreConnector.*;
import io.rdfforge.triplestore.connector.TriplestoreProviderInfo;
import io.rdfforge.triplestore.entity.TriplestoreConnectionEntity;
import io.rdfforge.triplestore.service.TriplestoreService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/triplestores")
@Tag(name = "Triplestores", description = "Triplestore connection and management API")
@CrossOrigin(origins = "*")
public class TriplestoreController {

    private final TriplestoreService triplestoreService;

    public TriplestoreController(TriplestoreService triplestoreService) {
        this.triplestoreService = triplestoreService;
    }

    // ==================== Provider Discovery ====================

    @GetMapping("/providers")
    @Operation(summary = "List providers", description = "Get all available triplestore providers and their capabilities")
    public ResponseEntity<List<TriplestoreProviderInfo>> getProviders() {
        return ResponseEntity.ok(triplestoreService.getAvailableProviders());
    }

    @GetMapping("/providers/{type}")
    @Operation(summary = "Get provider", description = "Get details about a specific triplestore provider")
    public ResponseEntity<TriplestoreProviderInfo> getProvider(@PathVariable String type) {
        return triplestoreService.getProviderInfo(type)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    // ==================== Connection Management ====================

    @GetMapping
    @Operation(summary = "List connections", description = "List configured triplestore connections")
    public ResponseEntity<List<TriplestoreConnectionEntity>> getConnections(
        @RequestParam(required = false) UUID projectId
    ) {
        return ResponseEntity.ok(triplestoreService.getConnections(projectId));
    }
    
    @GetMapping("/{id}")
    @Operation(summary = "Get connection", description = "Get connection details by ID")
    public ResponseEntity<TriplestoreConnectionEntity> getConnection(@PathVariable UUID id) {
        return triplestoreService.getConnection(id)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }
    
    @PostMapping
    @Operation(summary = "Create connection", description = "Configure a new triplestore connection")
    public ResponseEntity<TriplestoreConnectionEntity> createConnection(
        @RequestBody TriplestoreConnectionEntity connection
    ) {
        return ResponseEntity.ok(triplestoreService.createConnection(connection, null));
    }
    
    @PutMapping("/{id}")
    @Operation(summary = "Update connection", description = "Update triplestore connection details")
    public ResponseEntity<TriplestoreConnectionEntity> updateConnection(
        @PathVariable UUID id,
        @RequestBody TriplestoreConnectionEntity connection
    ) {
        return ResponseEntity.ok(triplestoreService.updateConnection(id, connection));
    }
    
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete connection", description = "Remove a triplestore connection configuration")
    public ResponseEntity<Void> deleteConnection(@PathVariable UUID id) {
        triplestoreService.deleteConnection(id);
        return ResponseEntity.noContent().build();
    }
    
    @GetMapping("/{id}/health")
    @Operation(summary = "Check health", description = "Check connection status")
    public ResponseEntity<Map<String, Object>> testConnection(@PathVariable UUID id) {
        return ResponseEntity.ok(triplestoreService.testConnection(id));
    }
    
    @PostMapping("/{id}/connect")
    @Operation(summary = "Connect", description = "Establish connection (test)")
    public ResponseEntity<Map<String, Object>> connect(@PathVariable UUID id) {
        return ResponseEntity.ok(triplestoreService.testConnection(id));
    }
    
    @GetMapping("/{id}/graphs")
    @Operation(summary = "List graphs", description = "List named graphs in the triplestore")
    public ResponseEntity<List<GraphInfo>> listGraphs(@PathVariable UUID id) {
        return ResponseEntity.ok(triplestoreService.listGraphs(id));
    }
    
    @GetMapping("/{id}/graphs/{graphUri}/resources")
    @Operation(summary = "List resources", description = "List resources in a specific graph")
    public ResponseEntity<List<ResourceInfo>> listResources(
        @PathVariable UUID id,
        @PathVariable String graphUri,
        @RequestParam(defaultValue = "100") int limit,
        @RequestParam(defaultValue = "0") int offset
    ) {
        String decodedGraphUri = URLDecoder.decode(graphUri, StandardCharsets.UTF_8);
        return ResponseEntity.ok(triplestoreService.listResources(id, decodedGraphUri, limit, offset));
    }
    
    @GetMapping("/{id}/graphs/{graphUri}/search")
    @Operation(summary = "Search resources", description = "Search for resources by keyword")
    public ResponseEntity<List<ResourceInfo>> searchResources(
        @PathVariable UUID id,
        @PathVariable String graphUri,
        @RequestParam String q
    ) {
        String decodedGraphUri = URLDecoder.decode(graphUri, StandardCharsets.UTF_8);
        return ResponseEntity.ok(triplestoreService.searchResources(id, decodedGraphUri, q));
    }
    
    @GetMapping("/{id}/graphs/{graphUri}/resources/{resourceUri}")
    @Operation(summary = "Get resource", description = "Get details of a specific resource")
    public ResponseEntity<ResourceInfo> getResource(
        @PathVariable UUID id,
        @PathVariable String graphUri,
        @PathVariable String resourceUri
    ) {
        String decodedGraphUri = URLDecoder.decode(graphUri, StandardCharsets.UTF_8);
        String decodedResourceUri = URLDecoder.decode(resourceUri, StandardCharsets.UTF_8);
        return ResponseEntity.ok(triplestoreService.getResource(id, decodedGraphUri, decodedResourceUri));
    }
    
    @PostMapping("/{id}/sparql")
    @Operation(summary = "Execute query", description = "Execute a SPARQL query")
    public ResponseEntity<QueryResult> executeSparql(
        @PathVariable UUID id,
        @RequestBody SparqlRequest request
    ) {
        return ResponseEntity.ok(triplestoreService.executeQuery(id, request.query(), request.graph()));
    }
    
    @PostMapping("/{id}/upload")
    @Operation(summary = "Upload RDF", description = "Upload RDF data to a graph")
    public ResponseEntity<Map<String, Object>> uploadRdf(
        @PathVariable UUID id,
        @RequestBody UploadRequest request
    ) {
        return ResponseEntity.ok(triplestoreService.uploadRdf(
            id, request.graphUri(), request.content(), request.format()
        ));
    }
    
    @DeleteMapping("/{id}/graphs/{graphUri}")
    @Operation(summary = "Delete graph", description = "Clear/drop a named graph")
    public ResponseEntity<Void> deleteGraph(
        @PathVariable UUID id,
        @PathVariable String graphUri
    ) {
        String decodedGraphUri = URLDecoder.decode(graphUri, StandardCharsets.UTF_8);
        triplestoreService.deleteGraph(id, decodedGraphUri);
        return ResponseEntity.noContent().build();
    }
    
    @GetMapping("/{id}/graphs/{graphUri}/export")
    @Operation(summary = "Export graph", description = "Export a named graph as RDF")
    public ResponseEntity<String> exportGraph(
        @PathVariable UUID id,
        @PathVariable String graphUri,
        @RequestParam(defaultValue = "turtle") String format
    ) {
        String decodedGraphUri = URLDecoder.decode(graphUri, StandardCharsets.UTF_8);
        String content = triplestoreService.exportGraph(id, decodedGraphUri, format);
        return ResponseEntity.ok(content);
    }
    
    public record SparqlRequest(String query, String graph) {}
    
    public record UploadRequest(String graphUri, String content, String format) {}
}
