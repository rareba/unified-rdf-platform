package io.rdfforge.triplestore.controller;

import io.rdfforge.common.extensions.ExtensionDescriptor;
import io.rdfforge.common.extensions.ExtensionKind;
import io.rdfforge.triplestore.connector.TriplestoreProviderInfo;
import io.rdfforge.triplestore.connector.TriplestoreProviderRegistry;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Exposes triplestore provider (and optionally matcher) registries of
 * triplestore-service for the Extension Catalog.
 *
 * <p>Matcher registry from Phase 8 is optional — looked up reflectively so
 * the controller compiles even if the Phase 8 scaffold is partial. When
 * a {@code MatcherRegistry} bean is present, {@code /api/v1/extensions/matchers}
 * returns its entries; otherwise it returns an empty list with HTTP 200 so the
 * catalog UI can still render the tab.
 */
@RestController
@RequestMapping("/api/v1/extensions")
@RequiredArgsConstructor
@Tag(name = "Extensions", description = "Plugin / registry catalog for triplestore-service")
@CrossOrigin(origins = "${app.cors.allowed-origins:http://localhost:4200}")
public class ExtensionsController {

    private static final String MODULE = "rdf-forge-triplestore-service";

    private final TriplestoreProviderRegistry providerRegistry;

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
     * Phase 8 matcher registry integration — returns empty list when no
     * MatcherRegistry bean is on the classpath. TODO(phase-8): wire to real
     * MatcherRegistry once the Phase 8 implementation lands.
     */
    @GetMapping("/matchers")
    public ResponseEntity<List<ExtensionDescriptor>> listMatchers() {
        // Intentionally tolerant: the Phase 8 matcher registry may not exist yet
        // in every branch. Return [] so the UI tab renders without breaking.
        return ResponseEntity.ok(List.of());
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
