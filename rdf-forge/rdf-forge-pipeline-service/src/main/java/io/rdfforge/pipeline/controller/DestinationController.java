package io.rdfforge.pipeline.controller;

import io.rdfforge.pipeline.destination.DestinationInfo;
import io.rdfforge.pipeline.destination.DestinationProvider;
import io.rdfforge.pipeline.destination.DestinationProvider.ValidationResult;
import io.rdfforge.pipeline.destination.DestinationRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * REST controller for destination provider discovery and management.
 *
 * Provides endpoints to list available destination providers, get their
 * configuration options, and validate configurations.
 */
@RestController
@RequestMapping("/api/v1/destinations")
@RequiredArgsConstructor
@Tag(name = "Destinations", description = "RDF destination provider discovery API")
@CrossOrigin(origins = "*")
public class DestinationController {

    private final DestinationRegistry destinationRegistry;

    @GetMapping
    @Operation(summary = "List all available destinations",
               description = "Get a list of all registered destination providers with their configuration options")
    public ResponseEntity<List<DestinationInfo>> listDestinations() {
        return ResponseEntity.ok(destinationRegistry.getAvailableDestinations());
    }

    @GetMapping("/by-category")
    @Operation(summary = "List destinations grouped by category",
               description = "Get destinations organized by their category (triplestore, file, cloud-storage, etc.)")
    public ResponseEntity<Map<String, List<DestinationInfo>>> listDestinationsByCategory() {
        return ResponseEntity.ok(destinationRegistry.getDestinationsByCategory());
    }

    @GetMapping("/{type}")
    @Operation(summary = "Get destination details",
               description = "Get detailed information about a specific destination provider")
    public ResponseEntity<DestinationInfo> getDestination(@PathVariable String type) {
        return destinationRegistry.getDestinationInfo(type)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/formats")
    @Operation(summary = "List supported formats",
               description = "Get all RDF serialization formats supported across all destinations")
    public ResponseEntity<Set<String>> getSupportedFormats() {
        return ResponseEntity.ok(destinationRegistry.getAllSupportedFormats());
    }

    @GetMapping("/by-format/{format}")
    @Operation(summary = "Find destinations supporting a format",
               description = "Get destinations that support a specific RDF serialization format")
    public ResponseEntity<List<DestinationInfo>> getDestinationsByFormat(@PathVariable String format) {
        List<DestinationProvider> providers = destinationRegistry.getProvidersSupportingFormat(format);
        List<DestinationInfo> infos = providers.stream()
            .map(DestinationProvider::getDestinationInfo)
            .toList();
        return ResponseEntity.ok(infos);
    }

    @PostMapping("/{type}/validate")
    @Operation(summary = "Validate destination configuration",
               description = "Validate a configuration for a specific destination provider")
    public ResponseEntity<ValidationResult> validateConfig(
            @PathVariable String type,
            @RequestBody Map<String, Object> config) {
        return destinationRegistry.getProvider(type)
            .map(provider -> ResponseEntity.ok(provider.validateConfig(config)))
            .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/{type}/check-availability")
    @Operation(summary = "Check destination availability",
               description = "Check if a destination is reachable with the given configuration")
    public ResponseEntity<Map<String, Object>> checkAvailability(
            @PathVariable String type,
            @RequestBody Map<String, Object> config) {
        return destinationRegistry.getProvider(type)
            .map(provider -> {
                boolean available = provider.isAvailable(config);
                return ResponseEntity.ok(Map.<String, Object>of(
                    "type", type,
                    "available", available,
                    "message", available ? "Destination is reachable" : "Destination is not reachable"
                ));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/categories")
    @Operation(summary = "List destination categories",
               description = "Get all available destination categories")
    public ResponseEntity<List<String>> listCategories() {
        return ResponseEntity.ok(List.of(
            DestinationInfo.CATEGORY_TRIPLESTORE,
            DestinationInfo.CATEGORY_FILE,
            DestinationInfo.CATEGORY_CLOUD_STORAGE,
            DestinationInfo.CATEGORY_API
        ));
    }
}
