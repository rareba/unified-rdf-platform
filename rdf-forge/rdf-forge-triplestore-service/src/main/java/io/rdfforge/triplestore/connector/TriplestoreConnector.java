package io.rdfforge.triplestore.connector;

import java.util.List;
import java.util.Map;

public interface TriplestoreConnector {
    
    boolean testConnection();
    
    List<GraphInfo> listGraphs();
    
    QueryResult executeQuery(String query, String graph);
    
    void executeUpdate(String update, String graph);
    
    void uploadRdf(String graphUri, String content, String format);
    
    void deleteGraph(String graphUri);
    
    String exportGraph(String graphUri, String format);
    
    List<ResourceInfo> listResources(String graphUri, int limit, int offset);
    
    ResourceInfo getResource(String graphUri, String resourceUri);
    
    record GraphInfo(String uri, long tripleCount) {}
    
    record QueryResult(
        List<String> variables,
        List<Map<String, RdfValue>> bindings,
        long executionTimeMs
    ) {}
    
    record RdfValue(String type, String value, String datatype, String language) {}
    
    record ResourceInfo(
        String uri,
        List<String> types,
        String label,
        List<PropertyValue> properties
    ) {}
    
    record PropertyValue(
        String predicate,
        String object,
        String objectType,
        String datatype,
        String language
    ) {}
}
