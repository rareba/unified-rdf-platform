package io.rdfforge.triplestore.reconciliation.matchers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.rdfforge.common.security.AuthUser;
import io.rdfforge.triplestore.reconciliation.Matcher;
import io.rdfforge.triplestore.reconciliation.MatchCandidateEntity.MatchPredicate;
import io.rdfforge.triplestore.reconciliation.MatchCandidateEntity.MatcherSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Wikidata entity search matcher, backed by the public
 * <a href="https://www.wikidata.org/w/api.php">wbsearchentities</a> endpoint.
 *
 * <p>Disabled by default — set {@code rdf-forge.matchers.wikidata.enabled=true}
 * to activate. The bean is always registered so the extension catalog can show
 * "Wikidata" with {@code available=false} when the operator has not opted in;
 * the alternative (ConditionalOnProperty) hides the matcher from users and
 * makes disabled state indistinguishable from "not built".
 *
 * <p>When enabled, a search returns up to {@code limit} candidates with
 * {@link MatchPredicate#EXACT_MATCH} for label-identical results and
 * {@link MatchPredicate#CLOSE_MATCH} otherwise. Confidence is a simple
 * position-weighted heuristic in [0.5, 1.0].
 */
@Slf4j
@Component
public class WikidataMatcher implements Matcher {

    private static final String DEFAULT_API_URL =
        "https://www.wikidata.org/w/api.php";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final boolean enabled;
    private final String apiUrl;
    private final String language;
    private final int timeoutSeconds;
    private final HttpClient httpClient;

    public WikidataMatcher(
            @Value("${rdf-forge.matchers.wikidata.enabled:false}") boolean enabled,
            @Value("${rdf-forge.matchers.wikidata.api-url:" + DEFAULT_API_URL + "}") String apiUrl,
            @Value("${rdf-forge.matchers.wikidata.language:en}") String language,
            @Value("${rdf-forge.matchers.wikidata.timeout-seconds:10}") int timeoutSeconds
    ) {
        this.enabled = enabled;
        this.apiUrl = apiUrl;
        this.language = language;
        this.timeoutSeconds = Math.max(1, timeoutSeconds);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(this.timeoutSeconds))
                .build();
    }

    @Override public String id() { return "wikidata"; }

    @Override public String displayName() { return "Wikidata"; }

    @Override public boolean enabled() { return enabled; }

    @Override
    public String description() {
        return enabled
            ? "Wikidata wbsearchentities API — external authority matcher."
            : "Wikidata wbsearchentities matcher — disabled. "
              + "Set rdf-forge.matchers.wikidata.enabled=true to enable.";
    }

    @Override
    public List<String> capabilities() {
        return List.of("external-authority", "wikidata", "label-search");
    }

    @Override
    public boolean supports(MatchQuery query) {
        return enabled && query.label() != null && !query.label().isBlank();
    }

    @Override
    public List<MatchCandidate> match(MatchQuery query, AuthUser user) {
        if (!enabled) {
            return List.of();
        }
        String needle = query.label();
        if (needle == null || needle.isBlank()) {
            return List.of();
        }
        int limit = Math.max(1, Math.min(query.limit(), 50));
        String url = apiUrl
                + "?action=wbsearchentities&format=json&language="
                + URLEncoder.encode(language, StandardCharsets.UTF_8)
                + "&limit=" + limit
                + "&search=" + URLEncoder.encode(needle, StandardCharsets.UTF_8);
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Accept", "application/json")
                    .header("User-Agent", "rdf-forge/1.0 (wikidata-matcher)")
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                log.warn("Wikidata search '{}' returned HTTP {}", needle, resp.statusCode());
                return List.of();
            }
            return parse(resp.body(), needle, query.sourceUri());
        } catch (Exception e) {
            log.warn("Wikidata search failed for '{}': {}", needle, e.toString());
            return List.of();
        }
    }

    private List<MatchCandidate> parse(String body, String needle, String sourceUri) {
        List<MatchCandidate> out = new ArrayList<>();
        try {
            JsonNode root = MAPPER.readTree(body);
            JsonNode results = root.path("search");
            if (!results.isArray()) {
                return out;
            }
            int position = 0;
            int total = results.size();
            for (JsonNode item : results) {
                String conceptUri = text(item, "concepturi");
                if (conceptUri == null || conceptUri.isBlank()) {
                    position++;
                    continue;
                }
                String label = text(item, "label");
                String description = text(item, "description");
                String qid = text(item, "id");
                boolean exact = label != null && label.equalsIgnoreCase(needle);
                MatchPredicate predicate = exact
                        ? MatchPredicate.EXACT_MATCH
                        : MatchPredicate.CLOSE_MATCH;
                // Position-weighted confidence: first hit ~1.0 if exact label;
                // decays smoothly for later results; all results floored at 0.5.
                double positional = total <= 1 ? 1.0
                        : 1.0 - ((double) position / (total * 2.0));
                double confidence = exact
                        ? Math.max(0.8, positional)
                        : Math.max(0.5, positional - 0.2);
                Map<String, Object> evidence = new LinkedHashMap<>();
                evidence.put("qid", qid);
                evidence.put("label", label);
                evidence.put("description", description);
                evidence.put("position", position);
                evidence.put("matchType", exact ? "exact-label" : "search-result");
                out.add(new MatchCandidate(
                        sourceUri,
                        conceptUri,
                        predicate,
                        Math.max(0.0, Math.min(1.0, confidence)),
                        MatcherSource.EXTERNAL_AUTHORITY,
                        "wikidata",
                        evidence
                ));
                position++;
            }
        } catch (Exception e) {
            log.warn("Failed to parse Wikidata response: {}", e.toString());
        }
        return out;
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        if (v.isMissingNode() || v.isNull()) return null;
        String s = v.asText();
        return s == null || s.isBlank() ? null : s;
    }

    // Visible for testing
    boolean isEnabled() { return enabled; }
    String apiUrl() { return apiUrl; }
}
