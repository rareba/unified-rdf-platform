package io.rdfforge.triplestore.controller;

import io.rdfforge.common.extensions.ExtensionDescriptor;
import io.rdfforge.common.extensions.ExtensionKind;
import io.rdfforge.triplestore.connector.TriplestoreProviderRegistry;
import io.rdfforge.triplestore.reconciliation.Matcher;
import io.rdfforge.triplestore.reconciliation.MatcherRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Proves that {@code GET /api/v1/extensions/matchers} reflects the actual
 * {@link MatcherRegistry} state — both enabled and disabled matchers appear,
 * and their {@code available} flag mirrors {@link Matcher#enabled()}.
 *
 * <p>Before stabilization the endpoint was hardcoded to return an empty list,
 * which let the catalog lie about what was actually built and wired.
 */
class ExtensionsControllerMatcherCatalogTest {

    private ExtensionsController controller;
    private MatcherRegistry registry;

    @BeforeEach
    void setUp() {
        TriplestoreProviderRegistry providerRegistry = mock(TriplestoreProviderRegistry.class);
        when(providerRegistry.getAvailableProviders()).thenReturn(List.of());
        registry = mock(MatcherRegistry.class);
        controller = new ExtensionsController(providerRegistry, registry);
    }

    @Test
    @DisplayName("Returns every registered matcher, enabled or not, sorted by id")
    void listsAllMatchersWithTruthfulAvailableFlag() {
        Matcher enabledA = stubMatcher("alpha-matcher", "Alpha", true, "Alpha description",
                List.of("local-graph"));
        Matcher disabled = stubMatcher("wikidata", "Wikidata", false,
                "Wikidata matcher — disabled. Set rdf-forge.matchers.wikidata.enabled=true",
                List.of("external-authority"));
        Matcher enabledM = stubMatcher("manual", "Manual", true, "Manual Entry", List.of("manual"));
        when(registry.getAll()).thenReturn(List.of(disabled, enabledA, enabledM));

        ResponseEntity<List<ExtensionDescriptor>> resp = controller.listMatchers();
        assertThat(resp.getStatusCode().value()).isEqualTo(200);

        List<ExtensionDescriptor> body = resp.getBody();
        assertThat(body).isNotNull();
        assertThat(body).extracting(ExtensionDescriptor::id)
                .containsExactly("alpha-matcher", "manual", "wikidata");
        assertThat(body).extracting(ExtensionDescriptor::kind)
                .containsOnly(ExtensionKind.MATCHER);

        ExtensionDescriptor wd = body.stream().filter(d -> d.id().equals("wikidata")).findFirst().orElseThrow();
        assertThat(wd.available()).isFalse();
        assertThat(wd.description()).contains("disabled");
        assertThat(wd.capabilities()).contains("external-authority");

        ExtensionDescriptor alpha = body.stream().filter(d -> d.id().equals("alpha-matcher")).findFirst().orElseThrow();
        assertThat(alpha.available()).isTrue();
        assertThat(alpha.description()).isEqualTo("Alpha description");
    }

    @Test
    @DisplayName("Empty registry produces empty list — not a hardcoded placeholder")
    void emptyRegistryStillReturnsEmpty() {
        when(registry.getAll()).thenReturn(List.of());
        ResponseEntity<List<ExtensionDescriptor>> resp = controller.listMatchers();
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isEmpty();
    }

    private static Matcher stubMatcher(String id, String name, boolean enabled,
                                       String description, List<String> capabilities) {
        return new Matcher() {
            @Override public String id() { return id; }
            @Override public String displayName() { return name; }
            @Override public boolean enabled() { return enabled; }
            @Override public String description() { return description; }
            @Override public List<String> capabilities() { return capabilities; }
            @Override public boolean supports(MatchQuery query) { return enabled; }
            @Override public List<MatchCandidate> match(MatchQuery query,
                                                       io.rdfforge.common.security.AuthUser user) {
                return List.of();
            }
        };
    }

    // Keep UUID import linked so IDEs don't prune it as an unused import once
    // future tests reference MatchQuery with a projectId argument.
    @SuppressWarnings("unused") private void keepUuidImport(UUID u) {}
    @SuppressWarnings("unused") private void keepMapImport(Map<?, ?> m) {}
}
