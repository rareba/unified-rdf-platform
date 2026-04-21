package io.rdfforge.pipeline.controller;

import io.rdfforge.common.extensions.ExtensionDescriptor;
import io.rdfforge.common.extensions.ExtensionKind;
import io.rdfforge.engine.operation.Operation;
import io.rdfforge.engine.operation.OperationRegistry;
import io.rdfforge.pipeline.destination.DestinationInfo;
import io.rdfforge.pipeline.destination.DestinationRegistry;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Exposes the operation and destination registries of pipeline-service
 * as unified {@link ExtensionDescriptor}s for the Extension Catalog UI.
 *
 * <p>The auth-service MetaController fans out to this endpoint along with the
 * analogous endpoints on the other backend services. Gateway route is the
 * existing pipeline-service predicate (adds {@code /api/v1/extensions/**}).
 */
@RestController
@RequestMapping("/api/v1/extensions")
@RequiredArgsConstructor
@Tag(name = "Extensions", description = "Plugin / registry catalog for pipeline-service")
@CrossOrigin(origins = "${app.cors.allowed-origins:http://localhost:4200}")
public class ExtensionsController {

    private static final String PIPELINE_MODULE = "rdf-forge-pipeline-service";
    private static final String ENGINE_MODULE = "rdf-forge-engine";

    private final OperationRegistry operationRegistry;
    private final DestinationRegistry destinationRegistry;

    /**
     * Returns every operation kind registered in the in-process
     * {@link OperationRegistry}. OperationRegistry lives in rdf-forge-engine but
     * is instantiated inside pipeline-service's application context, so this
     * controller owns the "operations" extension kind for catalog purposes.
     */
    @GetMapping("/operations")
    public ResponseEntity<List<ExtensionDescriptor>> listOperations() {
        List<ExtensionDescriptor> descriptors = new ArrayList<>();
        for (OperationRegistry.OperationInfo info : flatten(operationRegistry.getCatalog())) {
            Map<String, String> params = info.parameters() == null ? Map.of()
                : info.parameters().entrySet().stream()
                    .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> describeParameter(e.getValue()),
                        (a, b) -> a,
                        LinkedHashMap::new));

            String version = info.plugin() != null ? info.plugin().version() : "1.0";
            String docUrl = info.plugin() != null ? emptyToNull(info.plugin().documentation()) : null;
            List<String> capabilities = new ArrayList<>();
            capabilities.add(info.type().name().toLowerCase());
            if (info.plugin() != null) {
                if (info.plugin().builtIn()) capabilities.add("built-in");
                if (info.plugin().tags() != null) capabilities.addAll(info.plugin().tags());
            }

            descriptors.add(new ExtensionDescriptor(
                info.id(),
                ExtensionKind.OPERATION,
                info.name(),
                version,
                info.description(),
                capabilities,
                params,
                ENGINE_MODULE,
                docUrl,
                true
            ));
        }
        return ResponseEntity.ok(descriptors);
    }

    @GetMapping("/destinations")
    public ResponseEntity<List<ExtensionDescriptor>> listDestinations() {
        List<ExtensionDescriptor> out = new ArrayList<>();
        for (DestinationInfo info : destinationRegistry.getAvailableDestinations()) {
            Map<String, String> params = info.configFields() == null ? Map.of()
                : info.configFields().entrySet().stream()
                    .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        e -> describeField(e.getValue().type(), e.getValue().description(), e.getValue().required()),
                        (a, b) -> a,
                        LinkedHashMap::new));
            List<String> caps = new ArrayList<>();
            caps.add(info.category());
            if (info.capabilities() != null) caps.addAll(info.capabilities());
            if (info.supportedFormats() != null) {
                info.supportedFormats().forEach(f -> caps.add("format:" + f));
            }
            out.add(new ExtensionDescriptor(
                info.type(),
                ExtensionKind.DESTINATION,
                info.displayName(),
                "1.0",
                info.description(),
                caps,
                params,
                PIPELINE_MODULE,
                null,
                true
            ));
        }
        return ResponseEntity.ok(out);
    }

    private static List<OperationRegistry.OperationInfo> flatten(
            Map<Operation.OperationType, List<OperationRegistry.OperationInfo>> catalog) {
        List<OperationRegistry.OperationInfo> all = new ArrayList<>();
        catalog.values().forEach(all::addAll);
        return all;
    }

    private static String describeParameter(Operation.ParameterSpec spec) {
        StringBuilder sb = new StringBuilder();
        sb.append(spec.type() != null ? spec.type().getSimpleName() : "String");
        if (spec.required()) sb.append(" (required)");
        if (spec.defaultValue() != null) sb.append(" default=").append(spec.defaultValue());
        if (spec.description() != null && !spec.description().isBlank()) {
            sb.append(" — ").append(spec.description());
        }
        return sb.toString();
    }

    private static String describeField(String type, String description, boolean required) {
        StringBuilder sb = new StringBuilder();
        sb.append(type == null ? "string" : type);
        if (required) sb.append(" (required)");
        if (description != null && !description.isBlank()) sb.append(" — ").append(description);
        return sb.toString();
    }

    private static String emptyToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
