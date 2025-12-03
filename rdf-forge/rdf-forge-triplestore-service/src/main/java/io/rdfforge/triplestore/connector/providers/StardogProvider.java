package io.rdfforge.triplestore.connector.providers;

import io.rdfforge.triplestore.connector.*;
import io.rdfforge.triplestore.entity.TriplestoreConnectionEntity;
import io.rdfforge.triplestore.entity.TriplestoreConnectionEntity.*;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static io.rdfforge.triplestore.connector.TriplestoreProviderInfo.*;

/**
 * Provider for Stardog triplestore.
 *
 * Stardog is the world's leading knowledge graph platform for the enterprise.
 * It provides SPARQL query, OWL reasoning, and integrates with ML pipelines.
 */
@Component
public class StardogProvider implements TriplestoreProvider {

    private static final TriplestoreProviderInfo INFO = new TriplestoreProviderInfo(
        "STARDOG",
        "Stardog",
        "Enterprise knowledge graph platform with advanced reasoning, " +
            "virtual graphs, and machine learning integration. Supports SPARQL, GraphQL, and SQL.",
        "Stardog Union",
        "https://docs.stardog.com/",
        List.of(
            new AuthTypeInfo(
                "basic",
                "Basic Authentication",
                "HTTP Basic Authentication (required for Stardog)",
                List.of(
                    new ConfigField("username", "Username", "text", "Stardog username", true, "admin"),
                    new ConfigField("password", "Password", "password", "Stardog password", true)
                )
            ),
            new AuthTypeInfo(
                "token",
                "JWT Token",
                "Stardog JWT token authentication",
                List.of(
                    new ConfigField("token", "JWT Token", "password", "Stardog JWT token", true)
                )
            )
        ),
        List.of("turtle", "rdfxml", "ntriples", "jsonld", "nquads", "trig"),
        Map.of(
            "database", new ConfigField(
                "database", "Database Name", "text",
                "The Stardog database name", true, null, "e.g., myDatabase"
            ),
            "reasoning", new ConfigField(
                "reasoning", "Reasoning Level", "select",
                "The reasoning level to use for queries", false, "NONE",
                null, List.of("NONE", "RDFS", "QL", "RL", "EL", "DL", "SL")
            ),
            "timeout", new ConfigField(
                "timeout", "Query Timeout", "number",
                "Query timeout in milliseconds (0 = no timeout)", false, "0"
            ),
            "namespaces", new ConfigField(
                "namespaces", "Default Namespaces", "textarea",
                "Additional namespace prefixes (one per line: prefix=uri)", false
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

        // Get database from config
        String database = config != null ? (String) config.get("database") : null;
        String reasoning = config != null ? (String) config.get("reasoning") : "NONE";

        return new StardogConnector(baseUrl, database, username, password, token, reasoning);
    }

    @Override
    public boolean supports(TriplestoreType type) {
        return type == TriplestoreType.STARDOG;
    }

    /**
     * Stardog-specific connector implementation.
     */
    private static class StardogConnector extends AbstractSparqlConnector {

        private final String database;
        private final String token;
        private final String reasoning;

        StardogConnector(String baseUrl, String database, String username, String password,
                        String token, String reasoning) {
            super(
                baseUrl,
                buildEndpoint(baseUrl, database, "/query"),
                buildEndpoint(baseUrl, database, "/update"),
                buildEndpoint(baseUrl, database, ""),
                username,
                password
            );
            this.database = database;
            this.token = token;
            this.reasoning = reasoning != null ? reasoning : "NONE";
        }

        private static String buildEndpoint(String baseUrl, String database, String path) {
            String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
            if (database != null && !database.isEmpty()) {
                return base + "/" + database + path;
            }
            return base + path;
        }

        @Override
        protected String getTestQuery() {
            // Stardog supports standard SPARQL test
            return "SELECT (1 as ?test) WHERE {}";
        }

        @Override
        protected String getListGraphsQuery() {
            // Stardog uses standard SPARQL GRAPH query
            // Also supports 'stardog:context:all' for all contexts
            return """
                SELECT DISTINCT ?g (COUNT(*) as ?count) WHERE {
                  GRAPH ?g { ?s ?p ?o }
                } GROUP BY ?g ORDER BY ?g
                """;
        }

        // Note: In a full implementation, you would:
        // 1. Add JWT token to Authorization header
        // 2. Add SD-Connection-String header for reasoning: "reasoning=" + reasoning
        // 3. Use Stardog's admin API for database management
        // 4. Handle virtual graphs and data sources
    }
}
