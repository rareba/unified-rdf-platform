package io.rdfforge.auth.controller;

import io.rdfforge.common.extensions.ExtensionDescriptor;
import io.rdfforge.common.extensions.ExtensionKind;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Meta-endpoint that aggregates every backend service's
 * {@code /api/v1/extensions/*} response into a single unified list, so the
 * Extension Catalog UI needs one HTTP call instead of one per service.
 *
 * <p>Fan-out is best-effort and tolerant: if any backend is unreachable, its
 * descriptors are skipped, logged, and an {@code X-Partial-Errors} response
 * header advertises the failure count. The frontend can still render whatever
 * did succeed.
 *
 * <p>This controller is hosted in auth-service because it has the lowest
 * domain entanglement (no business entities, stateless adapter to Keycloak),
 * and the gateway already routes /api/v1/admin/** here — we reuse that by
 * mounting under {@code /api/v1/admin/extensions}. Fan-out URLs are injected
 * via {@code META_SERVICE_*} env vars so the same config works in local
 * docker-compose and in Kubernetes.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/extensions")
@Tag(name = "Extension Catalog", description = "Aggregated plugin catalog across services")
@CrossOrigin(origins = "${app.cors.allowed-origins:http://localhost:4200}")
public class MetaController {

    private final RestTemplate restTemplate;
    private final Map<ExtensionKind, List<String>> endpointsByKind = new ConcurrentHashMap<>();

    public MetaController(RestTemplate metaRestTemplate,
                          @Value("${meta.services.pipeline-url:http://rdf-forge-pipeline-service:8001}") String pipelineUrl,
                          @Value("${meta.services.data-url:http://rdf-forge-data-service:8004}") String dataUrl,
                          @Value("${meta.services.triplestore-url:http://rdf-forge-triplestore-service:8006}") String triplestoreUrl,
                          @Value("${meta.services.shacl-url:http://rdf-forge-shacl-service:8002}") String shaclUrl) {
        this.restTemplate = metaRestTemplate;
        this.endpointsByKind.put(ExtensionKind.OPERATION,
                List.of(pipelineUrl + "/api/v1/extensions/operations"));
        this.endpointsByKind.put(ExtensionKind.FORMAT,
                List.of(dataUrl + "/api/v1/extensions/formats"));
        this.endpointsByKind.put(ExtensionKind.STORAGE_PROVIDER,
                List.of(dataUrl + "/api/v1/extensions/storage-providers"));
        this.endpointsByKind.put(ExtensionKind.DESTINATION,
                List.of(pipelineUrl + "/api/v1/extensions/destinations"));
        this.endpointsByKind.put(ExtensionKind.TRIPLESTORE_PROVIDER,
                List.of(triplestoreUrl + "/api/v1/extensions/triplestore-providers"));
        this.endpointsByKind.put(ExtensionKind.MATCHER,
                List.of(triplestoreUrl + "/api/v1/extensions/matchers"));
        this.endpointsByKind.put(ExtensionKind.VALIDATOR,
                List.of(shaclUrl + "/api/v1/extensions/validators"));
        this.endpointsByKind.put(ExtensionKind.CUBE_PROFILE,
                List.of(shaclUrl + "/api/v1/extensions/cube-profiles"));
    }

    /**
     * Aggregate catalog across every backend service.
     * Returns 200 even on partial failure; the {@code X-Partial-Errors} header
     * reports how many endpoints could not be contacted.
     */
    @GetMapping
    @Operation(summary = "Fan-out list of every registered extension across services")
    public ResponseEntity<List<ExtensionDescriptor>> listAll(
            @RequestParam(value = "kind", required = false) ExtensionKind kind) {
        List<ExtensionDescriptor> aggregated = new ArrayList<>();
        int failed = 0;
        Set<Map.Entry<ExtensionKind, List<String>>> entries = kind == null
                ? endpointsByKind.entrySet()
                : Set.of(Map.entry(kind, endpointsByKind.getOrDefault(kind, List.of())));
        for (Map.Entry<ExtensionKind, List<String>> entry : entries) {
            for (String url : entry.getValue()) {
                try {
                    ResponseEntity<List<ExtensionDescriptor>> resp = restTemplate.exchange(
                        url, HttpMethod.GET, null,
                        new ParameterizedTypeReference<List<ExtensionDescriptor>>() {});
                    if (resp.getBody() != null) aggregated.addAll(resp.getBody());
                } catch (RestClientException ex) {
                    failed++;
                    log.warn("Meta-fan-out failed for {} ({}): {}", entry.getKey(), url, ex.getMessage());
                }
            }
        }
        return ResponseEntity.ok()
                .header("X-Partial-Errors", Integer.toString(failed))
                .body(aggregated);
    }

    /**
     * Summary showing counts per kind — useful for dashboards.
     */
    @GetMapping("/summary")
    @Operation(summary = "Count of registered extensions per kind")
    public ResponseEntity<Map<ExtensionKind, Integer>> summary() {
        Map<ExtensionKind, Integer> out = new EnumMap<>(ExtensionKind.class);
        for (Map.Entry<ExtensionKind, List<String>> entry : endpointsByKind.entrySet()) {
            int count = 0;
            for (String url : entry.getValue()) {
                try {
                    ResponseEntity<List<ExtensionDescriptor>> resp = restTemplate.exchange(
                        url, HttpMethod.GET, null,
                        new ParameterizedTypeReference<List<ExtensionDescriptor>>() {});
                    if (resp.getBody() != null) count += resp.getBody().size();
                } catch (RestClientException ex) {
                    log.debug("summary fan-out failed for {}: {}", url, ex.getMessage());
                }
            }
            out.put(entry.getKey(), count);
        }
        return ResponseEntity.ok(out);
    }
}
