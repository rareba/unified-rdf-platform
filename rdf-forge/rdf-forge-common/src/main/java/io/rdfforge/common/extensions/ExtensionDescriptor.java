package io.rdfforge.common.extensions;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Unified metadata record for an RDF Forge extension — any plugin-style
 * capability that services expose through a registry (operations, data formats,
 * storage providers, destinations, triplestore providers, matchers, validators,
 * cube profiles).
 *
 * <p>Each backend service owns one or more registries (see {@code rdf-forge-engine},
 * {@code rdf-forge-data-service}, etc.). To allow the UI and the meta-catalog in
 * {@code auth-service} to list every registered extension uniformly, each
 * service's {@code ExtensionsController} maps its native descriptor (e.g.
 * {@code DataFormatInfo}) to this common shape.
 *
 * <p>Fields:
 * <ul>
 *   <li>{@code id} — stable identifier unique within its kind (e.g. "csv")</li>
 *   <li>{@code kind} — one of {@link ExtensionKind}</li>
 *   <li>{@code name} — human-readable display name</li>
 *   <li>{@code version} — semantic version when known, else "1.0"</li>
 *   <li>{@code description} — short user-facing description</li>
 *   <li>{@code capabilities} — opaque feature tags, e.g. ["preview","parse"]</li>
 *   <li>{@code parameters} — parameter name -> brief type/desc string</li>
 *   <li>{@code providedBy} — Spring boot module name, e.g. "rdf-forge-data-service"</li>
 *   <li>{@code docUrl} — optional link to external documentation</li>
 *   <li>{@code available} — false for advertised-but-not-yet-implemented stubs</li>
 * </ul>
 *
 * <p>The record is designed to be Jackson-serialisable with no extra work.
 */
public record ExtensionDescriptor(
        String id,
        ExtensionKind kind,
        String name,
        String version,
        String description,
        List<String> capabilities,
        Map<String, String> parameters,
        String providedBy,
        String docUrl,
        boolean available
) {

    public ExtensionDescriptor {
        capabilities = capabilities == null ? List.of() : List.copyOf(capabilities);
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        version = version == null || version.isBlank() ? "1.0" : version;
    }

    /** Convenience factory for fully-available extensions. */
    public static ExtensionDescriptor of(String id,
                                         ExtensionKind kind,
                                         String name,
                                         String description,
                                         List<String> capabilities,
                                         Map<String, String> parameters,
                                         String providedBy) {
        return new ExtensionDescriptor(id, kind, name, "1.0", description,
                capabilities == null ? Collections.emptyList() : capabilities,
                parameters == null ? Collections.emptyMap() : parameters,
                providedBy, null, true);
    }
}
