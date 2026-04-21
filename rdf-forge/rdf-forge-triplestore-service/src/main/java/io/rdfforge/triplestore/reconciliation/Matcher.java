package io.rdfforge.triplestore.reconciliation;

import io.rdfforge.common.security.AuthUser;
import io.rdfforge.triplestore.reconciliation.MatchCandidateEntity.MatchPredicate;
import io.rdfforge.triplestore.reconciliation.MatchCandidateEntity.MatcherSource;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * SPI for match candidate providers. Register concrete implementations as
 * Spring beans to have them auto-discovered by {@link MatcherRegistry}.
 */
public interface Matcher {

    String id();

    String displayName();

    default boolean enabled() { return true; }

    /** Short description for the extension catalog. Override for detail. */
    default String description() { return ""; }

    /** Capability tags surfaced in the extension catalog. */
    default java.util.List<String> capabilities() { return java.util.List.of(); }

    boolean supports(MatchQuery query);

    /**
     * Produce match candidates for the given query. MUST NOT persist — that is
     * the responsibility of {@link MatchCandidateService}.
     */
    java.util.List<MatchCandidate> match(MatchQuery query, AuthUser user);

    /** Input to a matcher. */
    record MatchQuery(
            UUID projectId,
            String sourceUri,
            String label,
            Set<String> types,
            int limit,
            UUID triplestoreId,
            String graph
    ) {
        public MatchQuery(UUID projectId, String sourceUri, String label, Set<String> types, int limit) {
            this(projectId, sourceUri, label, types, limit, null, null);
        }
    }

    /** Output DTO — not persisted. */
    record MatchCandidate(
            String sourceUri,
            String targetUri,
            MatchPredicate predicate,
            double confidence,
            MatcherSource source,
            String matcherName,
            Map<String, Object> evidence
    ) {}
}
