package io.rdfforge.pipeline.client;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.rdfforge.common.security.AuthUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Authenticated client for talking to {@code rdf-forge-shacl-service} from
 * the Release Factory. Every call forwards the caller's identity via the
 * {@code X-User-*} headers that the gateway relies on downstream.
 *
 * <p>All public methods either return the requested value or throw
 * {@link ShaclClientException}. Any 4xx/5xx/IOException is translated into
 * a {@code ShaclClientException} with a concise, credential-free message.
 * The release build path catches the exception and marks the release
 * {@code FAILED} with that message in {@code failure_reason}.
 *
 * <p>Kept small on purpose: no retry logic, no circuit breaker. The release
 * build is a single user-facing action; if shacl-service is down the build
 * fails fast and the user retries.
 */
@Slf4j
@Component
@org.springframework.context.annotation.Lazy
public class AuthenticatedShaclClient {

    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USER_EMAIL = "X-User-Email";
    public static final String HEADER_USER_ROLES = "X-User-Roles";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public AuthenticatedShaclClient(
            RestTemplateBuilder builder,
            ObjectMapper objectMapper,
            @Value("${rdf-forge.shacl-service.base-url:${services.shacl-service.url:http://rdf-forge-shacl-service:8002}}")
            String baseUrl) {
        this.restTemplate = builder
            .connectTimeout(Duration.ofSeconds(5))
            .readTimeout(Duration.ofSeconds(30))
            .build();
        this.objectMapper = objectMapper;
        this.baseUrl = stripTrailingSlash(baseUrl);
    }

    // Constructor for unit tests — lets us inject a pre-built RestTemplate
    // (MockRestServiceServer) without going through the builder.
    public AuthenticatedShaclClient(RestTemplate restTemplate, ObjectMapper objectMapper, String baseUrl) {
        this.restTemplate = Objects.requireNonNull(restTemplate);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.baseUrl = stripTrailingSlash(baseUrl);
    }

    // ─────────────────────────── ontologies ──────────────────────────

    /**
     * Fetch an ontology's content plus the metadata fields needed to produce
     * a stable slug in the bundle. Format is forwarded as a query param.
     *
     * @throws ShaclClientException on any non-2xx response.
     */
    public OntologySummary fetchOntology(UUID id, AuthUser user, String format) {
        URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl)
            .path("/api/v1/ontologies/{id}/content")
            .queryParam("format", format == null ? "TURTLE" : format)
            .build(id);

        ResponseEntity<String> body = exchange(uri, HttpMethod.GET, user, "ontology " + id);
        try {
            JsonNode json = objectMapper.readTree(body.getBody());
            String content = textOrEmpty(json, "content");
            String name = textOrEmpty(json, "name");
            // The content endpoint doesn't carry prefix; fall back to the metadata call.
            String prefix = fetchOntologyPrefix(id, user);
            return new OntologySummary(id, name, prefix, content);
        } catch (ShaclClientException e) {
            throw e;
        } catch (Exception e) {
            throw new ShaclClientException(0,
                "Failed to parse ontology " + id + " response", e);
        }
    }

    private String fetchOntologyPrefix(UUID id, AuthUser user) {
        URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl)
            .path("/api/v1/ontologies/{id}")
            .build(id);
        try {
            ResponseEntity<String> body = exchange(uri, HttpMethod.GET, user, "ontology metadata " + id);
            JsonNode json = objectMapper.readTree(body.getBody());
            return textOrEmpty(json, "prefix");
        } catch (Exception e) {
            // Prefix is optional. Don't let a metadata hiccup fail the whole build.
            log.debug("Could not fetch prefix for ontology {}: {}", id, e.getMessage());
            return null;
        }
    }

    // ─────────────────────────── shapes ──────────────────────────────

    /**
     * Fetch a shape by id. The shape's {@code content} field carries the raw
     * Turtle (or other RDF serialization) that goes into the bundle.
     */
    public ShapeSummary fetchShape(UUID id, AuthUser user) {
        URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl)
            .path("/api/v1/shapes/{id}")
            .build(id);
        ResponseEntity<String> body = exchange(uri, HttpMethod.GET, user, "shape " + id);
        try {
            JsonNode json = objectMapper.readTree(body.getBody());
            return new ShapeSummary(
                id,
                textOrEmpty(json, "name"),
                textOrEmpty(json, "content"),
                textOrEmpty(json, "contentFormat"));
        } catch (ShaclClientException e) {
            throw e;
        } catch (Exception e) {
            throw new ShaclClientException(0,
                "Failed to parse shape " + id + " response", e);
        }
    }

    // ─────────────────────────── validation runs ─────────────────────

    /**
     * Fetch the most recent ValidationRun for a suite, or null if the suite
     * has never run. Uses {@code GET /api/v1/validation/runs?suiteId=&limit=1}.
     */
    public ValidationRunSummary fetchLatestRun(UUID suiteId, AuthUser user) {
        URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl)
            .path("/api/v1/validation/runs")
            .queryParam("suiteId", suiteId)
            .queryParam("limit", 1)
            .build()
            .toUri();
        ResponseEntity<String> body = exchange(uri, HttpMethod.GET, user, "validation runs " + suiteId);
        try {
            List<JsonNode> runs = objectMapper.readValue(body.getBody(),
                new TypeReference<List<JsonNode>>() {});
            if (runs == null || runs.isEmpty()) {
                return null;
            }
            JsonNode r = runs.get(0);
            return new ValidationRunSummary(
                parseUuid(r, "id"),
                parseUuid(r, "suiteId"),
                parseUuid(r, "projectId"),
                parseInstant(r, "ranAt"),
                textOrEmpty(r, "status"),
                intOrZero(r, "issueCount"),
                intOrZero(r, "errorCount"),
                intOrZero(r, "warningCount"),
                intOrZero(r, "infoCount"),
                intOrZero(r, "fatalCount"),
                textOrEmpty(r, "summary"));
        } catch (ShaclClientException e) {
            throw e;
        } catch (Exception e) {
            throw new ShaclClientException(0,
                "Failed to parse validation runs for suite " + suiteId, e);
        }
    }

    /** Fetch issues for a run, capped at {@code limit}. */
    public List<ValidationIssueSummary> fetchIssues(UUID runId, AuthUser user, int limit) {
        URI uri = UriComponentsBuilder.fromHttpUrl(baseUrl)
            .path("/api/v1/validation/runs/{id}/issues")
            .queryParam("limit", limit)
            .build(runId);
        ResponseEntity<String> body = exchange(uri, HttpMethod.GET, user, "validation issues " + runId);
        try {
            List<JsonNode> items = objectMapper.readValue(body.getBody(),
                new TypeReference<List<JsonNode>>() {});
            List<ValidationIssueSummary> out = new ArrayList<>();
            if (items == null) return out;
            for (JsonNode i : items) {
                out.add(new ValidationIssueSummary(
                    parseUuid(i, "id"),
                    textOrEmpty(i, "ruleId"),
                    textOrEmpty(i, "severity"),
                    textOrEmpty(i, "resourceUri"),
                    textOrEmpty(i, "message"),
                    textOrEmpty(i, "sourcePath")));
            }
            return out;
        } catch (ShaclClientException e) {
            throw e;
        } catch (Exception e) {
            throw new ShaclClientException(0,
                "Failed to parse validation issues for run " + runId, e);
        }
    }

    // ─────────────────────────── internals ───────────────────────────

    /**
     * Perform a single GET (or whatever method) with the caller's identity
     * propagated as X-User-* headers. Maps 4xx/5xx/IO errors to
     * {@link ShaclClientException} with a concise, credential-free message.
     */
    private ResponseEntity<String> exchange(URI uri, HttpMethod method, AuthUser user, String what) {
        HttpHeaders headers = new HttpHeaders();
        if (user != null && !user.isAnonymous()) {
            if (user.id() != null) {
                headers.add(HEADER_USER_ID, user.id().toString());
            }
            if (user.email() != null && !user.email().isBlank()) {
                headers.add(HEADER_USER_EMAIL, user.email());
            }
            if (user.roles() != null && !user.roles().isEmpty()) {
                headers.add(HEADER_USER_ROLES,
                    user.roles().stream().collect(Collectors.joining(",")));
            }
        }
        headers.add("Accept", "application/json");
        HttpEntity<Void> req = new HttpEntity<>(headers);
        try {
            ResponseEntity<String> resp = restTemplate.exchange(uri, method, req, String.class);
            HttpStatusCode status = resp.getStatusCode();
            if (status.is2xxSuccessful()) {
                return resp;
            }
            throw new ShaclClientException(status.value(),
                "Failed to fetch " + what + ": HTTP " + status.value());
        } catch (HttpClientErrorException e) {
            throw new ShaclClientException(e.getStatusCode().value(),
                "Failed to fetch " + what + ": HTTP " + e.getStatusCode().value());
        } catch (HttpServerErrorException e) {
            throw new ShaclClientException(e.getStatusCode().value(),
                "Failed to fetch " + what + ": HTTP " + e.getStatusCode().value());
        } catch (ResourceAccessException e) {
            throw new ShaclClientException(HttpStatus.SERVICE_UNAVAILABLE.value(),
                "Failed to fetch " + what + ": shacl-service unreachable", e);
        } catch (RestClientException e) {
            throw new ShaclClientException(HttpStatus.BAD_GATEWAY.value(),
                "Failed to fetch " + what + ": " + e.getClass().getSimpleName());
        }
    }

    private static String stripTrailingSlash(String s) {
        if (s == null) return "";
        return s.endsWith("/") ? s.substring(0, s.length() - 1) : s;
    }

    private static String textOrEmpty(JsonNode n, String field) {
        if (n == null) return null;
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static int intOrZero(JsonNode n, String field) {
        if (n == null) return 0;
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? 0 : v.asInt(0);
    }

    private static UUID parseUuid(JsonNode n, String field) {
        String t = textOrEmpty(n, field);
        if (t == null || t.isBlank()) return null;
        try { return UUID.fromString(t); }
        catch (IllegalArgumentException e) { return null; }
    }

    private static Instant parseInstant(JsonNode n, String field) {
        String t = textOrEmpty(n, field);
        if (t == null || t.isBlank()) return null;
        try { return Instant.parse(t); }
        catch (Exception e) { return null; }
    }
}
