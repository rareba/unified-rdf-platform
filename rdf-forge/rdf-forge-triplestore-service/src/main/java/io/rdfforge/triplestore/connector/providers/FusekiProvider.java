package io.rdfforge.triplestore.connector.providers;

import io.rdfforge.triplestore.connector.*;
import io.rdfforge.triplestore.entity.TriplestoreConnectionEntity;
import io.rdfforge.triplestore.entity.TriplestoreConnectionEntity.*;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static io.rdfforge.triplestore.connector.TriplestoreProviderInfo.*;

/**
 * Provider for Apache Jena Fuseki triplestore.
 *
 * Fuseki is the SPARQL server component of Apache Jena, providing
 * standard SPARQL 1.1 Query, Update, and Graph Store Protocol support.
 */
@Component
public class FusekiProvider implements TriplestoreProvider {

    private static final TriplestoreProviderInfo INFO = new TriplestoreProviderInfo(
        "FUSEKI",
        "Apache Jena Fuseki",
        "Open-source SPARQL server with full SPARQL 1.1 support. " +
            "Part of the Apache Jena project, it provides a robust and standards-compliant triplestore.",
        "Apache Software Foundation",
        "https://jena.apache.org/documentation/fuseki2/",
        List.of(
            new AuthTypeInfo(
                "none",
                "No Authentication",
                "Connect without authentication (suitable for development)",
                List.of()
            ),
            new AuthTypeInfo(
                "basic",
                "Basic Authentication",
                "HTTP Basic Authentication with username and password",
                List.of(
                    new ConfigField("username", "Username", "text", "Fuseki username", true),
                    new ConfigField("password", "Password", "password", "Fuseki password", true)
                )
            )
        ),
        List.of("turtle", "rdfxml", "ntriples", "jsonld", "nquads", "trig"),
        Map.of(
            "queryPath", new ConfigField(
                "queryPath", "Query Path", "text",
                "Path to the query endpoint (default: /query)", false, "/query"
            ),
            "updatePath", new ConfigField(
                "updatePath", "Update Path", "text",
                "Path to the update endpoint (default: /update)", false, "/update"
            ),
            "dataPath", new ConfigField(
                "dataPath", "Data Path", "text",
                "Path to the Graph Store Protocol endpoint (default: /data)", false, "/data"
            )
        ),
        List.of(
            CAPABILITY_SPARQL_QUERY,
            CAPABILITY_SPARQL_UPDATE,
            CAPABILITY_GRAPH_STORE,
            CAPABILITY_NAMED_GRAPHS,
            CAPABILITY_TRANSACTIONS,
            CAPABILITY_FULL_TEXT_SEARCH
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

        if (connection.getAuthType() == AuthType.BASIC && connection.getAuthConfig() != null) {
            username = (String) connection.getAuthConfig().get("username");
            password = (String) connection.getAuthConfig().get("password");
        }

        // Get custom paths from config or use defaults
        Map<String, Object> config = connection.getAuthConfig();
        String queryPath = config != null && config.containsKey("queryPath")
            ? (String) config.get("queryPath") : "/query";
        String updatePath = config != null && config.containsKey("updatePath")
            ? (String) config.get("updatePath") : "/update";
        String dataPath = config != null && config.containsKey("dataPath")
            ? (String) config.get("dataPath") : "/data";

        return new FusekiConnector(baseUrl, queryPath, updatePath, dataPath, username, password);
    }

    @Override
    public boolean supports(TriplestoreType type) {
        return type == TriplestoreType.FUSEKI;
    }

    /**
     * Fuseki-specific connector implementation.
     */
    private static class FusekiConnector extends AbstractSparqlConnector {

        FusekiConnector(String baseUrl, String queryPath, String updatePath,
                       String dataPath, String username, String password) {
            super(
                baseUrl,
                baseUrl + queryPath,
                baseUrl + updatePath,
                baseUrl + dataPath,
                username,
                password
            );
        }

        @Override
        protected String getListGraphsQuery() {
            // Fuseki supports standard SPARQL for listing graphs
            return """
                SELECT ?g (COUNT(*) as ?count) WHERE {
                  GRAPH ?g { ?s ?p ?o }
                } GROUP BY ?g ORDER BY ?g
                """;
        }
    }
}
