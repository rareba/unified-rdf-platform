package io.rdfforge.engine.operation.output;

import io.rdfforge.engine.operation.Operation;
import io.rdfforge.engine.operation.OperationException;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.query.DatasetAccessor;
import org.apache.jena.query.DatasetAccessorFactory;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
@Component
public class GraphStorePutOperation implements Operation {

    @Override
    public String getId() {
        return "graph-store-put";
    }

    @Override
    public String getName() {
        return "Graph Store PUT";
    }

    @Override
    public String getDescription() {
        return "Upload RDF data to a triplestore using Graph Store Protocol";
    }

    @Override
    public OperationType getType() {
        return OperationType.OUTPUT;
    }

    @Override
    public Map<String, ParameterSpec> getParameters() {
        return Map.of(
            "endpoint", new ParameterSpec("endpoint", "Graph Store endpoint URL", String.class, true, null),
            "graph", new ParameterSpec("graph", "Target graph URI (null for default)", String.class, false, null),
            "method", new ParameterSpec("method", "HTTP method (PUT or POST)", String.class, false, "PUT"),
            "username", new ParameterSpec("username", "Basic auth username", String.class, false, null),
            "password", new ParameterSpec("password", "Basic auth password", String.class, false, null),
            "format", new ParameterSpec("format", "RDF format (turtle, ntriples, rdfxml)", String.class, false, "ntriples"),
            "batchSize", new ParameterSpec("batchSize", "Batch size for large uploads", Integer.class, false, 10000)
        );
    }

    @Override
    public OperationResult execute(OperationContext context) throws OperationException {
        String endpoint = (String) context.parameters().get("endpoint");
        String graph = (String) context.parameters().get("graph");
        String method = (String) context.parameters().getOrDefault("method", "PUT");
        String username = (String) context.parameters().get("username");
        String password = (String) context.parameters().get("password");
        String format = (String) context.parameters().getOrDefault("format", "ntriples");

        if (context.inputModel() == null) {
            throw new OperationException(getId(), "No RDF model provided for upload");
        }

        Model model = context.inputModel();
        long tripleCount = model.size();

        try {
            String targetUrl = endpoint;
            if (graph != null && !graph.isEmpty()) {
                targetUrl = endpoint + (endpoint.contains("?") ? "&" : "?") + "graph=" + 
                    java.net.URLEncoder.encode(graph, StandardCharsets.UTF_8);
            }

            if (context.callback() != null) {
                context.callback().onLog("INFO", "Uploading " + tripleCount + " triples to " + targetUrl);
            }

            RDFFormat rdfFormat = switch (format.toLowerCase()) {
                case "turtle", "ttl" -> RDFFormat.TURTLE;
                case "rdfxml", "rdf" -> RDFFormat.RDFXML;
                default -> RDFFormat.NTRIPLES;
            };

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            RDFDataMgr.write(baos, model, rdfFormat);
            byte[] data = baos.toByteArray();

            HttpURLConnection conn = (HttpURLConnection) URI.create(targetUrl).toURL().openConnection();
            conn.setRequestMethod(method);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", rdfFormat.getLang().getContentType().getContentTypeStr());

            if (username != null && password != null) {
                String auth = Base64.getEncoder().encodeToString((username + ":" + password).getBytes());
                conn.setRequestProperty("Authorization", "Basic " + auth);
            }

            try (OutputStream os = conn.getOutputStream()) {
                os.write(data);
            }

            int responseCode = conn.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                String errorBody = "";
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getErrorStream()))) {
                    errorBody = br.lines().reduce("", (a, b) -> a + b);
                }
                throw new OperationException(getId(), "HTTP error " + responseCode + ": " + errorBody);
            }

            if (context.callback() != null) {
                context.callback().onLog("INFO", "Successfully uploaded " + tripleCount + " triples");
                context.callback().onMetric("triplesUploaded", tripleCount);
            }

            Map<String, Object> metadata = new HashMap<>();
            metadata.put("endpoint", endpoint);
            metadata.put("graph", graph);
            metadata.put("triplesUploaded", tripleCount);
            metadata.put("responseCode", responseCode);

            return new OperationResult(true, null, model, metadata, null);

        } catch (IOException e) {
            throw new OperationException(getId(), "Error uploading to triplestore: " + e.getMessage(), e);
        }
    }
}
