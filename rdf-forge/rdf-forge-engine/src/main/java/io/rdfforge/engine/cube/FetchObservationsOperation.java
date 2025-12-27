package io.rdfforge.engine.cube;

import io.rdfforge.engine.operation.Operation;
import io.rdfforge.engine.operation.OperationException;
import io.rdfforge.engine.operation.PluginInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.sparql.exec.http.QueryExecutionHTTP;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Operation to fetch only observations from a cube in a SPARQL endpoint.
 * Equivalent to barnard59's fetch-observations command.
 */
@Slf4j
@Component
@PluginInfo(
    author = "RDF Forge",
    version = "1.0.0",
    tags = {"cube", "sparql", "observations", "fetch"},
    documentation = "https://cube.link/",
    builtIn = true
)
public class FetchObservationsOperation implements Operation {

    private static final String CUBE_NS = "https://cube.link/";

    @Override
    public String getId() {
        return "fetch-observations";
    }

    @Override
    public String getName() {
        return "Fetch Observations";
    }

    @Override
    public String getDescription() {
        return "Fetch only observations from a cube in SPARQL endpoint";
    }

    @Override
    public OperationType getType() {
        return OperationType.SOURCE;
    }

    @Override
    public Map<String, ParameterSpec> getParameters() {
        return Map.of(
            "endpoint", new ParameterSpec("endpoint", "SPARQL endpoint URL", String.class, true, null),
            "cubeUri", new ParameterSpec("cubeUri", "URI of the cube to fetch observations from", String.class, true, null),
            "graphUri", new ParameterSpec("graphUri", "Named graph containing the cube (optional)", String.class, false, null),
            "limit", new ParameterSpec("limit", "Maximum observations to fetch (0 = unlimited)", Integer.class, false, 0),
            "offset", new ParameterSpec("offset", "Offset for pagination", Integer.class, false, 0),
            "timeout", new ParameterSpec("timeout", "Query timeout in seconds", Integer.class, false, 120)
        );
    }

    @Override
    public OperationResult execute(OperationContext context) throws OperationException {
        String endpoint = (String) context.parameters().get("endpoint");
        String cubeUri = (String) context.parameters().get("cubeUri");
        String graphUri = (String) context.parameters().get("graphUri");
        Integer limit = (Integer) context.parameters().getOrDefault("limit", 0);
        Integer offset = (Integer) context.parameters().getOrDefault("offset", 0);
        Integer timeout = (Integer) context.parameters().getOrDefault("timeout", 120);

        log.info("Fetching observations for cube {} from {} (limit={}, offset={})",
            cubeUri, endpoint, limit, offset);

        try {
            Model result = fetchObservations(endpoint, cubeUri, graphUri, limit, offset, timeout);

            // Count observations
            long obsCount = countObservations(result);

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("tripleCount", result.size());
            metadata.put("observationCount", obsCount);
            metadata.put("cubeUri", cubeUri);
            metadata.put("endpoint", endpoint);
            metadata.put("limit", limit);
            metadata.put("offset", offset);

            return new OperationResult(true, null, result, metadata, null);
        } catch (Exception e) {
            log.error("Failed to fetch observations: {}", e.getMessage(), e);
            throw new OperationException(getId(), "Failed to fetch observations: " + e.getMessage());
        }
    }

    private Model fetchObservations(String endpoint, String cubeUri, String graphUri,
                                    int limit, int offset, int timeout) {
        String query = buildObservationsQuery(cubeUri, graphUri, limit, offset);
        log.debug("Executing SPARQL query:\n{}", query);

        Query q = QueryFactory.create(query);
        try (QueryExecution qe = QueryExecutionHTTP.create()
                .endpoint(endpoint)
                .query(q)
                .timeout(timeout, TimeUnit.SECONDS)
                .build()) {
            return qe.execConstruct();
        }
    }

    private String buildObservationsQuery(String cubeUri, String graphUri, int limit, int offset) {
        StringBuilder sb = new StringBuilder();
        sb.append("PREFIX cube: <").append(CUBE_NS).append(">\n");
        sb.append("PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n\n");
        sb.append("CONSTRUCT {\n");
        sb.append("  ?obs ?p ?v .\n");
        sb.append("} WHERE {\n");

        if (graphUri != null && !graphUri.isBlank()) {
            sb.append("  GRAPH <").append(graphUri).append("> {\n");
        }

        sb.append("    <").append(cubeUri).append("> cube:observationSet ?obsSet .\n");
        sb.append("    ?obsSet cube:observation ?obs .\n");
        sb.append("    ?obs ?p ?v .\n");

        if (graphUri != null && !graphUri.isBlank()) {
            sb.append("  }\n");
        }

        sb.append("}\n");

        if (limit > 0) {
            sb.append("LIMIT ").append(limit).append("\n");
        }
        if (offset > 0) {
            sb.append("OFFSET ").append(offset).append("\n");
        }

        return sb.toString();
    }

    private long countObservations(Model model) {
        String query = "PREFIX cube: <" + CUBE_NS + ">\n" +
            "SELECT (COUNT(DISTINCT ?obs) AS ?count) WHERE { ?obs a cube:Observation }";
        try (QueryExecution qe = QueryExecutionFactory.create(query, model)) {
            ResultSet rs = qe.execSelect();
            if (rs.hasNext()) {
                return rs.next().getLiteral("count").getLong();
            }
        }
        return 0;
    }
}
