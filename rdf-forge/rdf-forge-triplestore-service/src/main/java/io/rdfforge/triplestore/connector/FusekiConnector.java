package io.rdfforge.triplestore.connector;

import org.apache.jena.query.*;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdfconnection.RDFConnection;
import org.apache.jena.rdfconnection.RDFConnectionRemote;
import org.apache.jena.rdfconnection.RDFConnectionRemoteBuilder;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.update.UpdateFactory;
import org.apache.jena.update.UpdateRequest;

import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class FusekiConnector implements TriplestoreConnector {
    
    private final String endpoint;
    private final String queryEndpoint;
    private final String updateEndpoint;
    private final String graphStoreEndpoint;
    private final String username;
    private final String password;
    
    public FusekiConnector(String endpoint, String username, String password) {
        this.endpoint = endpoint.endsWith("/") ? endpoint.substring(0, endpoint.length() - 1) : endpoint;
        this.queryEndpoint = this.endpoint + "/query";
        this.updateEndpoint = this.endpoint + "/update";
        this.graphStoreEndpoint = this.endpoint + "/data";
        this.username = username;
        this.password = password;
    }
    
    @Override
    public boolean testConnection() {
        try {
            String testQuery = "SELECT (1 as ?test) WHERE {}";
            QueryResult result = executeQuery(testQuery, null);
            return !result.bindings().isEmpty();
        } catch (Exception e) {
            return false;
        }
    }
    
    @Override
    public List<GraphInfo> listGraphs() {
        String query = """
            SELECT ?g (COUNT(*) as ?count) WHERE {
              GRAPH ?g { ?s ?p ?o }
            } GROUP BY ?g ORDER BY ?g
            """;
        
        QueryResult result = executeQuery(query, null);
        List<GraphInfo> graphs = new ArrayList<>();
        
        for (Map<String, RdfValue> binding : result.bindings()) {
            RdfValue gValue = binding.get("g");
            RdfValue countValue = binding.get("count");
            if (gValue != null) {
                long count = countValue != null ? Long.parseLong(countValue.value()) : 0;
                graphs.add(new GraphInfo(gValue.value(), count));
            }
        }
        
        return graphs;
    }
    
    @Override
    public QueryResult executeQuery(String query, String graph) {
        long startTime = System.currentTimeMillis();
        
        try (RDFConnection conn = createConnection()) {
            Query parsedQuery = QueryFactory.create(query);
            
            try (QueryExecution qExec = conn.query(parsedQuery)) {
                ResultSet results = qExec.execSelect();
                
                List<String> variables = results.getResultVars();
                List<Map<String, RdfValue>> bindings = new ArrayList<>();
                
                while (results.hasNext()) {
                    QuerySolution solution = results.next();
                    Map<String, RdfValue> binding = new HashMap<>();
                    
                    for (String var : variables) {
                        RDFNode node = solution.get(var);
                        if (node != null) {
                            binding.put(var, nodeToRdfValue(node));
                        }
                    }
                    
                    bindings.add(binding);
                }
                
                long executionTime = System.currentTimeMillis() - startTime;
                return new QueryResult(variables, bindings, executionTime);
            }
        }
    }
    
    @Override
    public void executeUpdate(String update, String graph) {
        try (RDFConnection conn = createConnection()) {
            UpdateRequest updateRequest = UpdateFactory.create(update);
            conn.update(updateRequest);
        }
    }
    
    @Override
    public void uploadRdf(String graphUri, String content, String format) {
        Lang lang = switch (format.toLowerCase()) {
            case "turtle", "ttl" -> Lang.TURTLE;
            case "rdfxml", "rdf" -> Lang.RDFXML;
            case "ntriples", "nt" -> Lang.NTRIPLES;
            case "jsonld", "json-ld" -> Lang.JSONLD;
            default -> Lang.TURTLE;
        };
        
        Model model = ModelFactory.createDefaultModel();
        RDFDataMgr.read(model, new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)), lang);
        
        try (RDFConnection conn = createConnection()) {
            conn.load(graphUri, model);
        }
    }
    
    @Override
    public void deleteGraph(String graphUri) {
        try (RDFConnection conn = createConnection()) {
            conn.delete(graphUri);
        }
    }
    
    @Override
    public String exportGraph(String graphUri, String format) {
        Lang lang = switch (format.toLowerCase()) {
            case "turtle", "ttl" -> Lang.TURTLE;
            case "rdfxml", "rdf" -> Lang.RDFXML;
            case "ntriples", "nt" -> Lang.NTRIPLES;
            case "jsonld", "json-ld" -> Lang.JSONLD;
            default -> Lang.TURTLE;
        };
        
        try (RDFConnection conn = createConnection()) {
            Model model = conn.fetch(graphUri);
            StringWriter writer = new StringWriter();
            RDFDataMgr.write(writer, model, lang);
            return writer.toString();
        }
    }
    
    @Override
    public List<ResourceInfo> listResources(String graphUri, int limit, int offset) {
        String query = String.format("""
            SELECT DISTINCT ?s ?type ?label WHERE {
              GRAPH <%s> {
                ?s a ?type .
                OPTIONAL { ?s rdfs:label ?label }
              }
            }
            LIMIT %d OFFSET %d
            """, graphUri, limit, offset);
        
        QueryResult result = executeQuery(query, graphUri);
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
    
    @Override
    public ResourceInfo getResource(String graphUri, String resourceUri) {
        String typesQuery = String.format("""
            SELECT ?type WHERE {
              GRAPH <%s> { <%s> a ?type }
            }
            """, graphUri, resourceUri);
        
        String propsQuery = String.format("""
            SELECT ?p ?o WHERE {
              GRAPH <%s> { <%s> ?p ?o }
            }
            """, graphUri, resourceUri);
        
        QueryResult typesResult = executeQuery(typesQuery, graphUri);
        List<String> types = typesResult.bindings().stream()
            .map(b -> b.get("type"))
            .filter(Objects::nonNull)
            .map(RdfValue::value)
            .toList();
        
        QueryResult propsResult = executeQuery(propsQuery, graphUri);
        List<PropertyValue> properties = new ArrayList<>();
        String label = null;
        
        for (Map<String, RdfValue> binding : propsResult.bindings()) {
            RdfValue pValue = binding.get("p");
            RdfValue oValue = binding.get("o");
            
            if (pValue != null && oValue != null) {
                properties.add(new PropertyValue(
                    pValue.value(),
                    oValue.value(),
                    oValue.type(),
                    oValue.datatype(),
                    oValue.language()
                ));
                
                if (pValue.value().endsWith("label") && label == null) {
                    label = oValue.value();
                }
            }
        }
        
        return new ResourceInfo(resourceUri, types, label, properties);
    }
    
    private RDFConnection createConnection() {
        RDFConnectionRemoteBuilder builder = RDFConnectionRemote.newBuilder()
            .destination(endpoint)
            .queryEndpoint(queryEndpoint)
            .updateEndpoint(updateEndpoint)
            .gspEndpoint(graphStoreEndpoint);
        
        if (username != null && !username.isEmpty()) {
            builder.httpClient(org.apache.jena.http.HttpEnv.getDftHttpClient());
        }
        
        return builder.build();
    }
    
    private RdfValue nodeToRdfValue(RDFNode node) {
        if (node.isURIResource()) {
            return new RdfValue("uri", node.asResource().getURI(), null, null);
        } else if (node.isLiteral()) {
            var literal = node.asLiteral();
            return new RdfValue(
                "literal",
                literal.getString(),
                literal.getDatatypeURI(),
                literal.getLanguage().isEmpty() ? null : literal.getLanguage()
            );
        } else if (node.isAnon()) {
            return new RdfValue("bnode", node.asResource().getId().getLabelString(), null, null);
        }
        return new RdfValue("unknown", node.toString(), null, null);
    }
}
