package io.rdfforge.triplestore.connector.providers;

import io.rdfforge.triplestore.connector.*;
import io.rdfforge.triplestore.entity.TriplestoreConnectionEntity;
import io.rdfforge.triplestore.entity.TriplestoreConnectionEntity.*;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static io.rdfforge.triplestore.connector.TriplestoreProviderInfo.*;

/**
 * Provider for Ontotext GraphDB triplestore.
 *
 * GraphDB is a highly-efficient, robust and scalable semantic graph database
 * with RDF and SPARQL support, plus advanced reasoning capabilities.
 */
@Component
public class GraphDBProvider implements TriplestoreProvider {

    private static final TriplestoreProviderInfo INFO = new TriplestoreProviderInfo(
        "GRAPHDB",
        "Ontotext GraphDB",
        "Enterprise-grade semantic graph database with RDF storage, SPARQL query, " +
            "and OWL/RDFS reasoning. Supports full-text search and geospatial queries.",
        "Ontotext",
        "https://graphdb.ontotext.com/documentation/",
        List.of(
            new AuthTypeInfo(
                "none",
                "No Authentication",
                "Connect without authentication (GraphDB Free or open repositories)",
                List.of()
            ),
            new AuthTypeInfo(
                "basic",
                "Basic Authentication",
                "HTTP Basic Authentication (GraphDB Standard/Enterprise)",
                List.of(
                    new ConfigField("username", "Username", "text", "GraphDB username", true),
                    new ConfigField("password", "Password", "password", "GraphDB password", true)
                )
            ),
            new AuthTypeInfo(
                "gdb-token",
                "GDB Token",
                "GraphDB access token authentication",
                List.of(
                    new ConfigField("token", "Access Token", "password", "GraphDB access token", true)
                )
            )
        ),
        List.of("turtle", "rdfxml", "ntriples", "jsonld", "nquads", "trig", "binary"),
        Map.of(
            "repository", new ConfigField(
                "repository", "Repository ID", "text",
                "The GraphDB repository identifier", true, null, "e.g., my-repo"
            ),
            "enableInference", new ConfigField(
                "enableInference", "Enable Inference", "boolean",
                "Enable reasoning/inference for queries", false, "false"
            ),
            "timeout", new ConfigField(
                "timeout", "Query Timeout", "number",
                "Query timeout in seconds (0 = no timeout)", false, "0"
            )
        ),
        List.of(
            CAPABILITY_SPARQL_QUERY,
            CAPABILITY_SPARQL_UPDATE,
            CAPABILITY_GRAPH_STORE,
            CAPABILITY_NAMED_GRAPHS,
            CAPABILITY_TRANSACTIONS,
            CAPABILITY_FULL_TEXT_SEARCH,
            CAPABILITY_GEOSPATIAL,
            CAPABILITY_REASONING,
            CAPABILITY_SHACL_VALIDATION,
            CAPABILITY_FEDERATED_QUERY
        )
    );

    @Override
    public TriplestoreProviderInfo getProviderInfo() {
        return INFO;
    }

    @Override
    public TriplestoreConnector createConnector(TriplestoreConnectionEntity connection) {
        String baseUrl = connection.getUrl();
        String username = null;
        String password = null;
        String token = null;

        Map<String, Object> config = connection.getAuthConfig();

        if (connection.getAuthType() == AuthType.BASIC && config != null) {
            username = (String) config.get("username");
            password = (String) config.get("password");
        } else if (connection.getAuthType() == AuthType.API_KEY && config != null) {
            token = (String) config.get("token");
        }

        // Get repository from config
        String repository = config != null ? (String) config.get("repository") : null;
        boolean enableInference = config != null && "true".equals(String.valueOf(config.get("enableInference")));

        return new GraphDBConnector(baseUrl, repository, username, password, token, enableInference);
    }

    @Override
    public boolean supports(TriplestoreType type) {
        return type == TriplestoreType.GRAPHDB;
    }

    /**
     * GraphDB-specific connector implementation.
     */
    private static class GraphDBConnector extends AbstractSparqlConnector {

        private final String repository;
        private final String token;
        private final boolean enableInference;

        GraphDBConnector(String baseUrl, String repository, String username, String password,
                        String token, boolean enableInference) {
            super(
                baseUrl,
                buildEndpoint(baseUrl, repository, ""),
                buildEndpoint(baseUrl, repository, "/statements"),
                buildEndpoint(baseUrl, repository, "/rdf-graphs/service"),
                username,
                password
            );
            this.repository = repository;
            this.token = token;
            this.enableInference = enableInference;
        }

        private static String buildEndpoint(String baseUrl, String repository, String path) {
            String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
            if (repository != null && !repository.isEmpty()) {
                return base + "/repositories/" + repository + path;
            }
            return base + path;
        }

        @Override
        protected String getTestQuery() {
            // GraphDB supports a simple test query
            return "SELECT (1 as ?test) WHERE {}";
        }

        @Override
        protected String getListGraphsQuery() {
            // GraphDB uses standard SPARQL for named graphs
            // but also supports a special system graph for metadata
            return """
                SELECT ?g (COUNT(*) as ?count) WHERE {
                  GRAPH ?g { ?s ?p ?o }
                  FILTER(?g != <http://www.openrdf.org/schema/sesame#nil>)
                } GROUP BY ?g ORDER BY ?g
                """;
        }

        // Note: In a full implementation, you would:
        // 1. Add token-based authentication to HTTP requests
        // 2. Handle inference toggle via GraphDB-specific headers
        // 3. Use GraphDB's REST API for repository management
    }
}
