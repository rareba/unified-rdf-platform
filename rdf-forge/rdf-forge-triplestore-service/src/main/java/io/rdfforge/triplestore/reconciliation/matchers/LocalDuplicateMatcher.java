package io.rdfforge.triplestore.reconciliation.matchers;

import io.rdfforge.common.security.AuthUser;
import io.rdfforge.triplestore.connector.TriplestoreConnector.QueryResult;
import io.rdfforge.triplestore.connector.TriplestoreConnector.RdfValue;
import io.rdfforge.triplestore.reconciliation.MatchCandidateEntity.MatchPredicate;
import io.rdfforge.triplestore.reconciliation.MatchCandidateEntity.MatcherSource;
import io.rdfforge.triplestore.reconciliation.Matcher;
import io.rdfforge.triplestore.service.TriplestoreService;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.query.ParameterizedSparqlString;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Local duplicate detection matcher — finds resources in the same project graph
 * that share or fuzzy-match the given label.
 *
 * <p>Uses Jena {@link ParameterizedSparqlString} so label input cannot inject
 * arbitrary SPARQL. The similarity score is Levenshtein-based with a floor of
 * 0.5 — strong structural evidence (exact label match) gets boosted to 1.0.
 */
@Slf4j
@Component
public class LocalDuplicateMatcher implements Matcher {

    private final TriplestoreService triplestoreService;

    public LocalDuplicateMatcher(TriplestoreService triplestoreService) {
        this.triplestoreService = triplestoreService;
    }

    @Override public String id() { return "local-duplicate"; }

    @Override public String displayName() { return "Local Duplicate (same graph)"; }

    @Override
    public boolean supports(MatchQuery q) {
        // Needs at least a label to compare and a triplestore to query.
        return q.label() != null && !q.label().isBlank() && q.triplestoreId() != null;
    }

    @Override
    public List<MatchCandidate> match(MatchQuery query, AuthUser user) {
        if (!supports(query)) return List.of();
        String label = query.label();
        int limit = query.limit() <= 0 ? 20 : Math.min(query.limit(), 100);

        // Use ParameterizedSparqlString — no user string concat into query text.
        ParameterizedSparqlString pss = new ParameterizedSparqlString("""
            PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>
            SELECT DISTINCT ?s ?slabel WHERE {
              ?s rdfs:label ?slabel .
              FILTER(CONTAINS(LCASE(STR(?slabel)), LCASE(?needle)))
              FILTER(!sameTerm(?s, ?source))
            }
            LIMIT ?maxRows
            """);
        pss.setLiteral("needle", label);
        pss.setIri("source", query.sourceUri() == null ? "urn:rdf-forge:sentinel:none" : query.sourceUri());
        pss.setLiteral("maxRows", limit);

        QueryResult result;
        try {
            result = triplestoreService.executeQuery(query.triplestoreId(), pss.toString(), query.graph());
        } catch (Exception e) {
            log.warn("LocalDuplicateMatcher query failed: {}", e.getMessage());
            return List.of();
        }

        List<MatchCandidate> out = new ArrayList<>();
        for (Map<String, RdfValue> binding : result.bindings()) {
            RdfValue sValue = binding.get("s");
            RdfValue labelValue = binding.get("slabel");
            if (sValue == null) continue;
            String target = sValue.value();
            String targetLabel = labelValue == null ? "" : labelValue.value();

            double similarity = similarity(label, targetLabel);
            double confidence = Math.max(0.5, similarity);
            MatchPredicate pred = similarity >= 0.99 ? MatchPredicate.EXACT_MATCH : MatchPredicate.CLOSE_MATCH;

            Map<String, Object> evidence = new LinkedHashMap<>();
            evidence.put("sourceLabel", label);
            evidence.put("targetLabel", targetLabel);
            evidence.put("similarity", similarity);
            evidence.put("strategy", "levenshtein");

            out.add(new MatchCandidate(
                query.sourceUri(),
                target,
                pred,
                confidence,
                MatcherSource.LOCAL_DUPLICATE,
                id(),
                evidence
            ));
        }
        return out;
    }

    /**
     * Compute Levenshtein-based similarity in [0,1]. Returns 1.0 if labels are
     * equal (case-insensitive, trimmed).
     */
    static double similarity(String a, String b) {
        if (a == null || b == null) return 0.0;
        String x = a.trim().toLowerCase(Locale.ROOT);
        String y = b.trim().toLowerCase(Locale.ROOT);
        if (x.isEmpty() && y.isEmpty()) return 1.0;
        if (x.equals(y)) return 1.0;
        int distance = levenshtein(x, y);
        int longest = Math.max(x.length(), y.length());
        if (longest == 0) return 1.0;
        return 1.0 - ((double) distance / longest);
    }

    static int levenshtein(String a, String b) {
        int[] prev = new int[b.length() + 1];
        int[] curr = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) prev[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            curr[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(Math.min(curr[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] tmp = prev; prev = curr; curr = tmp;
        }
        return prev[b.length()];
    }
}
