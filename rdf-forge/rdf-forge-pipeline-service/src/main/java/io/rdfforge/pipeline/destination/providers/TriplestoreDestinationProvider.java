package io.rdfforge.pipeline.destination.providers;

import io.rdfforge.pipeline.destination.DestinationInfo;
import io.rdfforge.pipeline.destination.DestinationInfo.ConfigField;
import io.rdfforge.pipeline.destination.DestinationProvider;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Destination provider for publishing RDF data to SPARQL triplestores.
 *
 * Supports GraphDB, Stardog, Fuseki, and any SPARQL 1.1 compliant endpoint.
 */
@Component
@Slf4j
public class TriplestoreDestinationProvider implements DestinationProvider {

    private static final String TYPE = "triplestore";

    @Override
    public DestinationInfo getDestinationInfo() {
        Map<String, ConfigField> configFields = new LinkedHashMap<>();

        configFields.put("endpoint", new ConfigField(
            "endpoint",
            "SPARQL Endpoint",
            "string",
            "The URL of the SPARQL update endpoint",
            true
        ));

        configFields.put("graph", new ConfigField(
            "graph",
            "Target Graph",
            "string",
            "The named graph URI to publish to (optional, uses default graph if empty)",
            false
        ));

        configFields.put("mode", new ConfigField(
            "mode",
            "Write Mode",
            "select",
            "How to handle existing data in the target graph",
            true,
            "append",
            List.of("append", "replace", "merge"),
            false
        ));

        configFields.put("username", new ConfigField(
            "username",
            "Username",
            "string",
            "Username for authentication (optional)",
            false
        ));

        configFields.put("password", new ConfigField(
            "password",
            "Password",
            "password",
            "Password for authentication (optional)",
            false,
            true
        ));

        configFields.put("batchSize", new ConfigField(
            "batchSize",
            "Batch Size",
            "number",
            "Number of triples per batch (default: 10000)",
            false,
            10000
        ));

        configFields.put("timeout", new ConfigField(
            "timeout",
            "Timeout (seconds)",
            "number",
            "Request timeout in seconds",
            false,
            300
        ));

        return new DestinationInfo(
            TYPE,
            "SPARQL Triplestore",
            "Publish RDF data to SPARQL-compliant triplestores (GraphDB, Stardog, Fuseki, etc.)",
            DestinationInfo.CATEGORY_TRIPLESTORE,
            configFields,
            List.of(
                DestinationInfo.CAPABILITY_NAMED_GRAPHS,
                DestinationInfo.CAPABILITY_TRANSACTIONS,
                DestinationInfo.CAPABILITY_APPEND,
                DestinationInfo.CAPABILITY_REPLACE,
                DestinationInfo.CAPABILITY_DELETE,
                DestinationInfo.CAPABILITY_BATCH
            ),
            List.of("turtle", "n-triples", "rdf/xml", "json-ld", "trig", "n-quads")
        );
    }

    @Override
    public PublishResult publish(Model model, Map<String, Object> config) throws IOException {
        String endpoint = (String) config.get("endpoint");
        String graphUri = (String) config.get("graph");
        String mode = (String) config.getOrDefault("mode", "append");
        String username = (String) config.get("username");
        String password = (String) config.get("password");
        int timeout = ((Number) config.getOrDefault("timeout", 300)).intValue() * 1000;

        if (endpoint == null || endpoint.isBlank()) {
            return PublishResult.failure("SPARQL endpoint is required");
        }

        try {
            // Handle write mode
            if ("replace".equals(mode)) {
                clearGraph(graphUri, config);
            }

            // Serialize model to N-Triples for efficient upload
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            RDFDataMgr.write(baos, model, RDFFormat.NTRIPLES);
            String rdfData = baos.toString(StandardCharsets.UTF_8);

            // Build SPARQL UPDATE query
            StringBuilder updateQuery = new StringBuilder();
            if (graphUri != null && !graphUri.isBlank()) {
                updateQuery.append("INSERT DATA { GRAPH <").append(graphUri).append("> { ");
            } else {
                updateQuery.append("INSERT DATA { ");
            }

            // For small datasets, inline the data
            // For large datasets, this should be batched
            long tripleCount = model.size();

            if (tripleCount <= 10000) {
                // Use SPARQL UPDATE with inline data
                updateQuery.append(rdfData);
                if (graphUri != null && !graphUri.isBlank()) {
                    updateQuery.append(" } }");
                } else {
                    updateQuery.append(" }");
                }

                executeSparqlUpdate(endpoint, updateQuery.toString(), username, password, timeout);
            } else {
                // Use Graph Store Protocol for large uploads
                uploadViaGraphStoreProtocol(endpoint, rdfData, graphUri, username, password, timeout);
            }

            return PublishResult.success(tripleCount, graphUri, Map.of(
                "endpoint", endpoint,
                "mode", mode
            ));

        } catch (Exception e) {
            log.error("Failed to publish to triplestore: {}", e.getMessage(), e);
            return PublishResult.failure("Failed to publish: " + e.getMessage());
        }
    }

    private void executeSparqlUpdate(String endpoint, String query, String username, String password, int timeout)
            throws IOException {
        URL url = new URL(endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/sparql-update");
            conn.setDoOutput(true);
            conn.setConnectTimeout(timeout);
            conn.setReadTimeout(timeout);

            if (username != null && !username.isBlank()) {
                String auth = username + ":" + (password != null ? password : "");
                String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
                conn.setRequestProperty("Authorization", "Basic " + encodedAuth);
            }

            try (OutputStream os = conn.getOutputStream()) {
                os.write(query.getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = conn.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                throw new IOException("SPARQL UPDATE failed with response code: " + responseCode);
            }
        } finally {
            conn.disconnect();
        }
    }

    private void uploadViaGraphStoreProtocol(String endpoint, String rdfData, String graphUri,
                                              String username, String password, int timeout) throws IOException {
        // Convert SPARQL endpoint to Graph Store Protocol endpoint
        // Most triplestores use /data or /statements suffix
        String gsEndpoint = endpoint.replace("/update", "/data")
                                    .replace("/sparql", "/data");

        if (graphUri != null && !graphUri.isBlank()) {
            gsEndpoint += "?graph=" + java.net.URLEncoder.encode(graphUri, StandardCharsets.UTF_8);
        }

        URL url = new URL(gsEndpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/n-triples");
            conn.setDoOutput(true);
            conn.setConnectTimeout(timeout);
            conn.setReadTimeout(timeout);

            if (username != null && !username.isBlank()) {
                String auth = username + ":" + (password != null ? password : "");
                String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
                conn.setRequestProperty("Authorization", "Basic " + encodedAuth);
            }

            try (OutputStream os = conn.getOutputStream()) {
                os.write(rdfData.getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = conn.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                throw new IOException("Graph Store upload failed with response code: " + responseCode);
            }
        } finally {
            conn.disconnect();
        }
    }

    @Override
    public void clearGraph(String graphUri, Map<String, Object> config) throws IOException {
        String endpoint = (String) config.get("endpoint");
        String username = (String) config.get("username");
        String password = (String) config.get("password");
        int timeout = ((Number) config.getOrDefault("timeout", 300)).intValue() * 1000;

        String clearQuery;
        if (graphUri != null && !graphUri.isBlank()) {
            clearQuery = "CLEAR GRAPH <" + graphUri + ">";
        } else {
            clearQuery = "CLEAR DEFAULT";
        }

        executeSparqlUpdate(endpoint, clearQuery, username, password, timeout);
        log.info("Cleared graph: {}", graphUri != null ? graphUri : "default");
    }

    @Override
    public boolean isAvailable(Map<String, Object> config) {
        String endpoint = (String) config.get("endpoint");
        if (endpoint == null || endpoint.isBlank()) {
            return false;
        }

        try {
            URL url = new URL(endpoint);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("HEAD");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            String username = (String) config.get("username");
            String password = (String) config.get("password");
            if (username != null && !username.isBlank()) {
                String auth = username + ":" + (password != null ? password : "");
                String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
                conn.setRequestProperty("Authorization", "Basic " + encodedAuth);
            }

            int responseCode = conn.getResponseCode();
            return responseCode < 400;
        } catch (Exception e) {
            log.debug("Triplestore availability check failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public ValidationResult validateConfig(Map<String, Object> config) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        String endpoint = (String) config.get("endpoint");
        if (endpoint == null || endpoint.isBlank()) {
            errors.add("SPARQL endpoint is required");
        } else {
            try {
                new URL(endpoint);
            } catch (Exception e) {
                errors.add("Invalid endpoint URL: " + endpoint);
            }
        }

        String graphUri = (String) config.get("graph");
        if (graphUri != null && !graphUri.isBlank() && !graphUri.startsWith("http")) {
            warnings.add("Graph URI should be a valid URI (starting with http:// or https://)");
        }

        String mode = (String) config.get("mode");
        if (mode != null && !List.of("append", "replace", "merge").contains(mode)) {
            errors.add("Invalid mode: " + mode + ". Must be one of: append, replace, merge");
        }

        if (errors.isEmpty()) {
            return ValidationResult.success();
        }
        return new ValidationResult(false, errors, warnings);
    }
}
