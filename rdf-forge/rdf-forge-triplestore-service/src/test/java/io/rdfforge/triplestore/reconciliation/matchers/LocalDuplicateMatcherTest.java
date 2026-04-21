package io.rdfforge.triplestore.reconciliation.matchers;

import io.rdfforge.common.security.AuthUser;
import io.rdfforge.triplestore.connector.TriplestoreConnector.QueryResult;
import io.rdfforge.triplestore.connector.TriplestoreConnector.RdfValue;
import io.rdfforge.triplestore.reconciliation.Matcher;
import io.rdfforge.triplestore.reconciliation.MatchCandidateEntity.MatchPredicate;
import io.rdfforge.triplestore.service.TriplestoreService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LocalDuplicateMatcherTest {

    @Mock private TriplestoreService triplestoreService;

    @Test
    void match_returnsCandidatesWithSimilarityEvidence() {
        LocalDuplicateMatcher matcher = new LocalDuplicateMatcher(triplestoreService);
        UUID tsId = UUID.randomUUID();
        Matcher.MatchQuery q = new Matcher.MatchQuery(
            UUID.randomUUID(),
            "http://example.org/source",
            "Paris",
            Set.of(),
            10,
            tsId,
            null
        );

        QueryResult result = new QueryResult(
            List.of("s", "slabel"),
            List.of(
                Map.of(
                    "s", new RdfValue("uri", "http://example.org/target", null, null),
                    "slabel", new RdfValue("literal", "Paris", null, null)
                )
            ),
            7L
        );
        when(triplestoreService.executeQuery(eq(tsId), anyString(), any())).thenReturn(result);

        List<Matcher.MatchCandidate> candidates = matcher.match(q, new AuthUser(UUID.randomUUID(), null, Set.of()));

        assertEquals(1, candidates.size());
        Matcher.MatchCandidate c = candidates.get(0);
        assertEquals("http://example.org/target", c.targetUri());
        assertEquals(MatchPredicate.EXACT_MATCH, c.predicate());
        assertTrue(c.confidence() >= 0.9);
        assertNotNull(c.evidence());
        assertEquals("levenshtein", c.evidence().get("strategy"));
    }

    @Test
    void match_closeMatchForNearLabel() {
        LocalDuplicateMatcher matcher = new LocalDuplicateMatcher(triplestoreService);
        UUID tsId = UUID.randomUUID();
        Matcher.MatchQuery q = new Matcher.MatchQuery(
            UUID.randomUUID(), "http://example.org/src", "Paris", Set.of(), 5, tsId, null);

        QueryResult result = new QueryResult(
            List.of("s", "slabel"),
            List.of(Map.of(
                "s", new RdfValue("uri", "http://example.org/target", null, null),
                "slabel", new RdfValue("literal", "Pariz", null, null)
            )),
            1L
        );
        when(triplestoreService.executeQuery(eq(tsId), anyString(), any())).thenReturn(result);

        List<Matcher.MatchCandidate> candidates = matcher.match(q, new AuthUser(UUID.randomUUID(), null, Set.of()));
        assertEquals(1, candidates.size());
        assertEquals(MatchPredicate.CLOSE_MATCH, candidates.get(0).predicate());
    }

    @Test
    void supports_requiresLabelAndTriplestoreId() {
        LocalDuplicateMatcher matcher = new LocalDuplicateMatcher(triplestoreService);
        assertFalse(matcher.supports(new Matcher.MatchQuery(
            UUID.randomUUID(), "http://s", null, Set.of(), 10, UUID.randomUUID(), null)));
        assertFalse(matcher.supports(new Matcher.MatchQuery(
            UUID.randomUUID(), "http://s", "Paris", Set.of(), 10, null, null)));
        assertTrue(matcher.supports(new Matcher.MatchQuery(
            UUID.randomUUID(), "http://s", "Paris", Set.of(), 10, UUID.randomUUID(), null)));
    }

    @Test
    void similarity_identicalLabels_returnsOne() {
        assertEquals(1.0, LocalDuplicateMatcher.similarity("Paris", "paris"), 1e-9);
    }

    @Test
    void similarity_differentLabels_returnsLessThanOne() {
        double sim = LocalDuplicateMatcher.similarity("Paris", "London");
        assertTrue(sim < 1.0);
        assertTrue(sim >= 0.0);
    }
}
