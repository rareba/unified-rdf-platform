package io.rdfforge.triplestore.reconciliation.matchers;

import io.rdfforge.common.security.AuthUser;
import io.rdfforge.triplestore.reconciliation.Matcher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * STUB: Wikidata entity search matcher. NOT ACTIVE in Phase 8 v1.
 *
 * <p>Activation: set {@code rdf-forge.matchers.wikidata.enabled=true}.
 *
 * <p>TODO — follow-ups:
 * <ul>
 *   <li>Parse Wikidata {@code wbsearchentities} JSON and surface candidates</li>
 *   <li>Map to {@link io.rdfforge.triplestore.reconciliation.MatchCandidateEntity.MatchPredicate#EXACT_MATCH}</li>
 *   <li>Add rate-limiting and response caching</li>
 *   <li>Externalize endpoint URL via application config</li>
 * </ul>
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "rdf-forge.matchers.wikidata.enabled", havingValue = "true")
public class WikidataMatcher implements Matcher {

    private static final String API_URL =
        "https://www.wikidata.org/w/api.php?action=wbsearchentities&format=json&language=en";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override public String id() { return "wikidata"; }

    @Override public String displayName() { return "Wikidata"; }

    @Override public boolean enabled() { return true; }

    @Override
    public boolean supports(MatchQuery query) {
        return query.label() != null && !query.label().isBlank();
    }

    @Override
    public List<MatchCandidate> match(MatchQuery query, AuthUser user) {
        // TODO Phase 8.1: parse response; for now we just log and return empty.
        String q = query.label();
        if (q == null || q.isBlank()) return List.of();
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(API_URL + "&search=" + java.net.URLEncoder.encode(q, java.nio.charset.StandardCharsets.UTF_8)))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            log.debug("Wikidata search '{}' returned status {}", q, resp.statusCode());
        } catch (Exception e) {
            log.warn("Wikidata search failed: {}", e.getMessage());
        }
        return List.of();
    }
}
