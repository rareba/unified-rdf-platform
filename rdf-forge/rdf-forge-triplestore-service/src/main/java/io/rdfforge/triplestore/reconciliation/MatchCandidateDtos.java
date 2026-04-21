package io.rdfforge.triplestore.reconciliation;

import io.rdfforge.triplestore.reconciliation.MatchCandidateEntity.MatchPredicate;
import io.rdfforge.triplestore.reconciliation.MatchCandidateEntity.MatchStatus;
import io.rdfforge.triplestore.reconciliation.MatchCandidateEntity.MatcherSource;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * DTOs for the Phase 8 reconciliation endpoints.
 */
public final class MatchCandidateDtos {

    private MatchCandidateDtos() {}

    public record MatchCandidateDto(
            UUID id,
            UUID projectId,
            String sourceUri,
            String targetUri,
            MatchPredicate predicate,
            double confidence,
            MatcherSource source,
            String matcherName,
            MatchStatus status,
            Map<String, Object> evidence,
            UUID createdBy,
            UUID approvedBy,
            Instant createdAt,
            Instant updatedAt,
            Instant decidedAt
    ) {
        public static MatchCandidateDto from(MatchCandidateEntity e) {
            return new MatchCandidateDto(
                e.getId(), e.getProjectId(), e.getSourceUri(), e.getTargetUri(),
                e.getPredicate(), e.getConfidence(), e.getSource(), e.getMatcherName(),
                e.getStatus(), e.getEvidence(), e.getCreatedBy(), e.getApprovedBy(),
                e.getCreatedAt(), e.getUpdatedAt(), e.getDecidedAt()
            );
        }
    }

    public record SuggestRequest(
            UUID projectId,
            String sourceUri,
            String label,
            Set<String> types,
            Integer limit,
            UUID triplestoreId,
            String graph,
            /** Optional subset of matcher ids to run; empty means "all enabled". */
            Set<String> matcherIds
    ) {}

    public record ManualCandidateRequest(
            UUID projectId,
            String sourceUri,
            String targetUri,
            MatchPredicate predicate,
            Double confidence,
            Map<String, Object> evidence
    ) {}

    public record MatchStatsDto(
            UUID projectId,
            long pending,
            long approved,
            long rejected,
            long archived,
            Map<String, Long> byPredicate,
            Map<String, Long> byMatcher
    ) {}

    public record ListFilter(
            MatchStatus status,
            MatchPredicate predicate,
            String matcher,
            String search
    ) {
        public static ListFilter of(MatchStatus s, MatchPredicate p, String m, String q) {
            return new ListFilter(s, p, m, q);
        }
    }

    public record SuggestResponse(
            int persisted,
            int duplicatesSkipped,
            List<MatchCandidateDto> candidates
    ) {}
}
