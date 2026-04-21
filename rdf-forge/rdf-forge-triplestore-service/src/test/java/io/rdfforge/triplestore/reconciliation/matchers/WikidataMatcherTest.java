package io.rdfforge.triplestore.reconciliation.matchers;

import io.rdfforge.common.security.AuthUser;
import io.rdfforge.triplestore.reconciliation.Matcher;
import io.rdfforge.triplestore.reconciliation.MatchCandidateEntity.MatchPredicate;
import io.rdfforge.triplestore.reconciliation.MatchCandidateEntity.MatcherSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the real {@link WikidataMatcher} parser to prove it:
 *   - stays quiet (returns empty) when disabled,
 *   - reports itself disabled in {@link Matcher#enabled()} truthfully,
 *   - converts a realistic Wikidata wbsearchentities response payload into
 *     {@link Matcher.MatchCandidate} records with the expected predicate and
 *     evidence fields.
 *
 * <p>The parser is called directly via reflection so no HTTP mock is required.
 * The {@code match()} path is exercised indirectly through the disabled-gate
 * test — HTTP fixture-based tests can be added later without touching this
 * contract.
 */
class WikidataMatcherTest {

    private static final AuthUser USER = new AuthUser(UUID.randomUUID(), "u@x", Set.of());

    @Test
    @DisplayName("Disabled matcher: enabled()==false, supports()==false, match()==[]")
    void disabledMatcherDoesNothing() {
        WikidataMatcher m = new WikidataMatcher(false,
            "https://www.wikidata.org/w/api.php", "en", 10);
        assertThat(m.enabled()).isFalse();
        assertThat(m.isEnabled()).isFalse();
        Matcher.MatchQuery q = new Matcher.MatchQuery(
            UUID.randomUUID(), null, "Bern", Set.of(), 5);
        assertThat(m.supports(q)).isFalse();
        assertThat(m.match(q, USER)).isEmpty();
        assertThat(m.description()).contains("disabled");
        assertThat(m.capabilities()).contains("external-authority");
    }

    @Test
    @DisplayName("Enabled matcher advertises itself as available and supports non-blank labels")
    void enabledMatcherAdvertisesSelfCorrectly() {
        WikidataMatcher m = new WikidataMatcher(true,
            "https://www.wikidata.org/w/api.php", "en", 10);
        assertThat(m.enabled()).isTrue();
        assertThat(m.description()).doesNotContain("disabled");
        assertThat(m.supports(new Matcher.MatchQuery(UUID.randomUUID(), null, "Bern", Set.of(), 5)))
                .isTrue();
        assertThat(m.supports(new Matcher.MatchQuery(UUID.randomUUID(), null, "   ", Set.of(), 5)))
                .isFalse();
    }

    @Test
    @DisplayName("Parser converts a realistic wbsearchentities payload to candidates")
    void parsesRealisticPayload() throws Exception {
        WikidataMatcher m = new WikidataMatcher(true,
            "https://www.wikidata.org/w/api.php", "en", 10);
        String sourceUri = "urn:rdf-forge:source:city:bern";
        // Trimmed-down but realistic wbsearchentities response.
        String body = "{\"search\":[" +
                "{\"id\":\"Q70\",\"concepturi\":\"http://www.wikidata.org/entity/Q70\"," +
                " \"label\":\"Bern\",\"description\":\"capital of Switzerland\"}," +
                "{\"id\":\"Q1\",\"concepturi\":\"http://www.wikidata.org/entity/Q1\"," +
                " \"label\":\"Bernn\",\"description\":\"misspelled placeholder\"}" +
                "]}";
        Method parse = WikidataMatcher.class.getDeclaredMethod("parse",
                String.class, String.class, String.class);
        parse.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<Matcher.MatchCandidate> out = (List<Matcher.MatchCandidate>)
                parse.invoke(m, body, "Bern", sourceUri);

        assertThat(out).hasSize(2);
        Matcher.MatchCandidate exact = out.get(0);
        assertThat(exact.sourceUri()).isEqualTo(sourceUri);
        assertThat(exact.targetUri()).isEqualTo("http://www.wikidata.org/entity/Q70");
        assertThat(exact.predicate()).isEqualTo(MatchPredicate.EXACT_MATCH);
        assertThat(exact.source()).isEqualTo(MatcherSource.EXTERNAL_AUTHORITY);
        assertThat(exact.matcherName()).isEqualTo("wikidata");
        assertThat(exact.evidence())
            .containsEntry("qid", "Q70")
            .containsEntry("label", "Bern")
            .containsEntry("matchType", "exact-label");

        Matcher.MatchCandidate close = out.get(1);
        assertThat(close.predicate()).isEqualTo(MatchPredicate.CLOSE_MATCH);
        assertThat(close.confidence()).isLessThan(exact.confidence());
    }

    @Test
    @DisplayName("Empty or malformed response yields empty list, not exception")
    void malformedPayloadGivesEmptyList() throws Exception {
        WikidataMatcher m = new WikidataMatcher(true,
            "https://www.wikidata.org/w/api.php", "en", 10);
        Method parse = WikidataMatcher.class.getDeclaredMethod("parse",
                String.class, String.class, String.class);
        parse.setAccessible(true);
        assertThat((List<?>) parse.invoke(m, "{\"search\":[]}", "x", "s")).isEmpty();
        assertThat((List<?>) parse.invoke(m, "not-json", "x", "s")).isEmpty();
        assertThat((List<?>) parse.invoke(m, "{}", "x", "s")).isEmpty();
    }
}
