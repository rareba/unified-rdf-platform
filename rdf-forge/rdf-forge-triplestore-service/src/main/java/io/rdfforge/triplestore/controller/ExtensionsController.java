package io.rdfforge.triplestore.controller;

import io.rdfforge.common.extensions.ExtensionDescriptor;
import io.rdfforge.common.extensions.ExtensionKind;
import io.rdfforge.triplestore.connector.TriplestoreProviderInfo;
import io.rdfforge.triplestore.connector.TriplestoreProviderRegistry;
import io.rdfforge.triplestore.reconciliation.Matcher;
import io.rdfforge.triplestore.reconciliation.MatcherRegistry;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Exposes the triplestore-service registries (triplestore providers and
 * reconciliation matchers) to the Extension Catalog.
 *
 * <p>{@code /api/v1/extensions/matchers} reflects the actual {@link MatcherRegistry}
 * state — every registered {@link Matcher} bean is reported with {@code available}
 * set from its {@link Matcher#enabled()} flag. Disabled matchers (e.g. Wikidata
 * when its property gate is not set) are still listed so operators can see what
 * is available to switch on.
 */
@RestController
@RequestMapping("/api/v1/extensions")
@RequiredArgsConstructor
@Tag(name = "Extensions", description = "Plugin / registry catalog for triplestore-service")
@CrossOrigin(origins = "${app.cors.allowed-origins:http://localhost:4200}")
public class ExtensionsController {

    private static final String MODULE = "rdf-forge-triplestore-service";

    private final TriplestoreProviderRegistry providerRegistry;
    private final MatcherRegistry matcherRegistry;

    @GetMapping("/triplestore-providers")
    public ResponseEntity<List<ExtensionDescriptor>> listTriplestoreProviders() {
        List<ExtensionDescriptor> out = new ArrayList<>();
        for (TriplestoreProviderInfo info : providerRegistry.getAvailableProviders()) {
            Map<String, String> params = info.configFields() == null ? Map.of()
                : info.configFields().entrySet().stream()
                    .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> describeField(e.getValue()),
                        (a, b) -> a,
                        LinkedHashMap::new));
            List<String> caps = new ArrayList<>();
            if (info.capabilities() != null) caps.addAll(info.capabilities());
            if (info.vendor() != null && !info.vendor().isBlank()) caps.add("vendor:" + info.vendor());
            if (info.supportedRdfFormats() != null) {
                info.supportedRdfFormats().forEach(f -> caps.add("format:" + f));
            }
            out.add(new ExtensionDescriptor(
                info.type(),
                ExtensionKind.TRIPLESTORE_PROVIDER,
                info.displayName(),
                "1.0",
                info.description() == null ? "" : info.description(),
                caps,
                params,
                MODULE,
                emptyToNull(info.documentationUrl()),
                true
            ));
        }
        return ResponseEntity.ok(out);
    }

    /**
     * Matcher catalog. Every registered {@link Matcher} bean is surfaced —
     * whether enabled or not — so the operator can see the full inventory.
     * {@code available} reflects {@link Matcher#enabled()} truthfully;
     * description/capabilities are read from the matcher instance.
     */
    @GetMapping("/matchers")
    public ResponseEntity<List<ExtensionDescriptor>> listMatchers() {
        List<ExtensionDescriptor> out = matcherRegistry.getAll().stream()
            .sorted(Comparator.comparing(Matcher::id))
            .map(m -> new ExtensionDescriptor(
                m.id(),
                ExtensionKind.MATCHER,
                m.displayName(),
                "1.0",
                m.description() == null ? "" : m.description(),
                m.capabilities(),
                Map.of(),
                MODULE,
                null,
                m.enabled()
            ))
            .toList();
        return ResponseEntity.ok(out);
    }

    private static String describeField(TriplestoreProviderInfo.ConfigField f) {
        StringBuilder sb = new StringBuilder();
        sb.append(f.type() == null ? "string" : f.type());
        if (f.required()) sb.append(" (required)");
        if (f.description() != null && !f.description().isBlank()) {
            sb.append(" — ").append(f.description());
        }
        return sb.toString();
    }

    private static String emptyToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
