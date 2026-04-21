package io.rdfforge.pipeline.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

/**
 * Create a new Release draft.
 *
 * <p>{@code manifestRefs} carries the asset ids to bundle. Any field may be
 * null/empty — releases are allowed to omit asset classes (e.g. a pure
 * ontology release with no data/mapping).
 */
public record ReleaseCreateRequest(
    @NotBlank @Size(max = 64) String version,
    @NotBlank @Size(max = 255) String name,
    String notes,
    @NotNull ManifestRefs manifestRefs
) {
    /**
     * References to the assets this release bundles. Everything is optional —
     * a release may include just ontologies, just mappings, or any mix.
     */
    public record ManifestRefs(
        List<UUID> dataSources,
        List<UUID> mappings,
        List<UUID> shapes,
        List<UUID> ontologies,
        UUID triplestoreId,
        List<UUID> validationSuiteIds
    ) {}
}
