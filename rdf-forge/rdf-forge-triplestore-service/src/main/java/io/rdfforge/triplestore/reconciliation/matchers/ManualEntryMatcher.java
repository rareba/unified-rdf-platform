package io.rdfforge.triplestore.reconciliation.matchers;

import io.rdfforge.common.security.AuthUser;
import io.rdfforge.triplestore.reconciliation.Matcher;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Marker matcher that lets UI code distinguish "user-entered" candidates from
 * matcher-generated ones. Does not produce matches itself.
 */
@Component
public class ManualEntryMatcher implements Matcher {

    @Override public String id() { return "manual"; }

    @Override public String displayName() { return "Manual Entry"; }

    @Override public String description() {
        return "Allows reviewers to enter match candidates by hand; does not auto-suggest.";
    }

    @Override public List<String> capabilities() {
        return List.of("manual", "reviewer-driven");
    }

    @Override public boolean supports(MatchQuery query) { return false; }

    @Override public List<MatchCandidate> match(MatchQuery query, AuthUser user) { return List.of(); }
}
