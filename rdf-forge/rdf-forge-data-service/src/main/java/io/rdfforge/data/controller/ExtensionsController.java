package io.rdfforge.data.controller;

import io.rdfforge.common.extensions.ExtensionDescriptor;
import io.rdfforge.common.extensions.ExtensionKind;
import io.rdfforge.data.format.DataFormatInfo;
import io.rdfforge.data.format.DataFormatRegistry;
import io.rdfforge.data.storage.StorageProviderInfo;
import io.rdfforge.data.storage.StorageProviderRegistry;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Exposes format and storage registries of data-service for the Extension
 * Catalog. Endpoints:
 * <ul>
 *   <li>GET /api/v1/extensions/formats</li>
 *   <li>GET /api/v1/extensions/storage-providers</li>
 * </ul>
 * Gateway routes /api/v1/extensions/** to data-service by URL-pattern overlap.
 */
@RestController
@RequestMapping("/api/v1/extensions")
@RequiredArgsConstructor
@Tag(name = "Extensions", description = "Plugin / registry catalog for data-service")
@CrossOrigin(origins = "${app.cors.allowed-origins:http://localhost:4200}")
public class ExtensionsController {

    private static final String MODULE = "rdf-forge-data-service";

    private final DataFormatRegistry formatRegistry;
    private final StorageProviderRegistry storageRegistry;

    @GetMapping("/formats")
    public ResponseEntity<List<ExtensionDescriptor>> listFormats() {
        List<ExtensionDescriptor> out = new ArrayList<>();
        for (DataFormatInfo info : formatRegistry.getAvailableFormats()) {
            Map<String, String> params = info.options() == null ? Map.of()
                : info.options().entrySet().stream()
                    .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> describeOption(e.getValue()),
                        (a, b) -> a,
                        LinkedHashMap::new));
            List<String> caps = new ArrayList<>();
            if (info.capabilities() != null) caps.addAll(info.capabilities());
            if (info.supportsPreview()) caps.add("preview");
            if (info.supportsAnalysis()) caps.add("analyze");
            if (info.supportsStreaming()) caps.add("streaming");
            if (info.fileExtensions() != null) {
                info.fileExtensions().forEach(ext -> caps.add("ext:" + ext));
            }
            out.add(new ExtensionDescriptor(
                info.format(),
                ExtensionKind.FORMAT,
                info.displayName(),
                "1.0",
                info.description() == null ? "" : info.description(),
                caps,
                params,
                MODULE,
                null,
                info.available()
            ));
        }
        return ResponseEntity.ok(out);
    }

    @GetMapping("/storage-providers")
    public ResponseEntity<List<ExtensionDescriptor>> listStorageProviders() {
        List<ExtensionDescriptor> out = new ArrayList<>();
        for (StorageProviderInfo info : storageRegistry.getAvailableProviders()) {
            Map<String, String> params = info.configFields() == null ? Map.of()
                : info.configFields().entrySet().stream()
                    .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> describeConfig(e.getValue()),
                        (a, b) -> a,
                        LinkedHashMap::new));
            List<String> caps = new ArrayList<>();
            if (info.capabilities() != null) caps.addAll(info.capabilities());
            if (info.vendor() != null && !info.vendor().isBlank()) caps.add("vendor:" + info.vendor());
            out.add(new ExtensionDescriptor(
                info.type(),
                ExtensionKind.STORAGE_PROVIDER,
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

    private static String describeOption(DataFormatInfo.FormatOption opt) {
        StringBuilder sb = new StringBuilder();
        sb.append(opt.type() == null ? "string" : opt.type());
        if (opt.required()) sb.append(" (required)");
        if (opt.defaultValue() != null) sb.append(" default=").append(opt.defaultValue());
        if (opt.description() != null && !opt.description().isBlank()) {
            sb.append(" — ").append(opt.description());
        }
        return sb.toString();
    }

    private static String describeConfig(StorageProviderInfo.ConfigField f) {
        StringBuilder sb = new StringBuilder();
        sb.append(f.type() == null ? "string" : f.type());
        if (f.required()) sb.append(" (required)");
        if (f.sensitive()) sb.append(" (sensitive)");
        if (f.description() != null && !f.description().isBlank()) {
            sb.append(" — ").append(f.description());
        }
        return sb.toString();
    }

    private static String emptyToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
