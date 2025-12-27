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
 * Operation to fetch a complete cube (metadata + observations) from a SPARQL endpoint.
 * Equivalent to barnard59's fetch-cube command.
 */
@Slf4j
@Component
@PluginInfo(
    author = "RDF Forge",
    version = "1.0.0",
    tags = {"cube", "sparql", "fetch"},
    documentation = "https://cube.link/",
    builtIn = true
)
public class FetchCubeOperation implements Operation {

    private static final String CUBE_NS = "https://cube.link/";

    @Override
    public String getId() {
        return "fetch-cube";
    }

    @Override
    public String getName() {
        return "Fetch Cube";
    }

    @Override
    public String getDescription() {
        return "Fetch complete cube (metadata + observations) from SPARQL endpoint";
    }

    @Override
    public OperationType getType() {
        return OperationType.SOURCE;
    }

    @Override
    public Map<String, ParameterSpec> getParameters() {
        return Map.of(
            "endpoint", new ParameterSpec("endpoint", "SPARQL endpoint URL", String.class, true, null),
            "cubeUri", new ParameterSpec("cubeUri", "URI of the cube to fetch", String.class, true, null),
            "graphUri", new ParameterSpec("graphUri", "Named graph containing the cube (optional)", String.class, false, null),
            "timeout", new ParameterSpec("timeout", "Query timeout in seconds", Integer.class, false, 60)
        );
    }

    @Override
    public OperationResult execute(OperationContext context) throws OperationException {
        String endpoint = (String) context.parameters().get("endpoint");
        String cubeUri = (String) context.parameters().get("cubeUri");
        String graphUri = (String) context.parameters().get("graphUri");
        Integer timeout = (Integer) context.parameters().getOrDefault("timeout", 60);

        log.info("Fetching cube {} from {}", cubeUri, endpoint);

        try {
            Model result = fetchCube(endpoint, cubeUri, graphUri, timeout);

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("tripleCount", result.size());
            metadata.put("cubeUri", cubeUri);
            metadata.put("endpoint", endpoint);

            return new OperationResult(true, null, result, metadata, null);
        } catch (Exception e) {
            log.error("Failed to fetch cube: {}", e.getMessage(), e);
            throw new OperationException(getId(), "Failed to fetch cube: " + e.getMessage());
        }
    }

    private Model fetchCube(String endpoint, String cubeUri, String graphUri, int timeout) {
        String query = buildFetchQuery(cubeUri, graphUri);
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

    private String buildFetchQuery(String cubeUri, String graphUri) {
        StringBuilder sb = new StringBuilder();
        sb.append("PREFIX cube: <").append(CUBE_NS).append(">\n");
        sb.append("PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n\n");
        sb.append("CONSTRUCT {\n");
        sb.append("  ?cube ?cp ?co .\n");
        sb.append("  ?obsSet ?osp ?oso .\n");
        sb.append("  ?obs ?obsp ?obsv .\n");
        sb.append("  ?constraint ?conp ?conv .\n");
        sb.append("  ?propShape ?psp ?psv .\n");
        sb.append("} WHERE {\n");

        if (graphUri != null && !graphUri.isBlank()) {
            sb.append("  GRAPH <").append(graphUri).append("> {\n");
        }

        sb.append("    BIND(<").append(cubeUri).append("> AS ?cube)\n");
        sb.append("    ?cube ?cp ?co .\n");
        sb.append("    OPTIONAL {\n");
        sb.append("      ?cube cube:observationSet ?obsSet .\n");
        sb.append("      ?obsSet ?osp ?oso .\n");
        sb.append("      OPTIONAL {\n");
        sb.append("        ?obsSet cube:observation ?obs .\n");
        sb.append("        ?obs ?obsp ?obsv .\n");
        sb.append("      }\n");
        sb.append("    }\n");
        sb.append("    OPTIONAL {\n");
        sb.append("      ?cube cube:observationConstraint ?constraint .\n");
        sb.append("      ?constraint ?conp ?conv .\n");
        sb.append("      OPTIONAL {\n");
        sb.append("        ?constraint <http://www.w3.org/ns/shacl#property> ?propShape .\n");
        sb.append("        ?propShape ?psp ?psv .\n");
        sb.append("      }\n");
        sb.append("    }\n");

        if (graphUri != null && !graphUri.isBlank()) {
            sb.append("  }\n");
        }

        sb.append("}\n");
        return sb.toString();
    }
}
