package io.rdfforge.pipeline.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * PROV-inspired lineage graph for a project (or sub-graph around a single
 * resource). The shape is stable from Phase 6 onwards — edges and node kinds
 * may be extended but existing ones won't change.
 */
public record LineageDto(
    UUID projectId,
    List<Node> nodes,
    List<Edge> edges
) {
    /**
     * A node in the lineage graph. {@code id} is prefixed by kind for UI
     * stability: {@code "uuid:data-<uuid>"}, {@code "uuid:mapping-<uuid>"} …
     */
    public record Node(
        String id,
        NodeKind kind,
        String label,
        Instant updatedAt,
        Map<String, Object> attributes
    ) {}

    /** Directed edge from {@code from} to {@code to}. */
    public record Edge(
        String from,
        String to,
        EdgeKind kind,
        Map<String, Object> attributes
    ) {}

    public enum NodeKind {
        PROJECT,
        DATA_SOURCE,
        MAPPING,
        ONTOLOGY,
        SHAPE,
        PIPELINE,
        JOB,
        TRIPLESTORE,
        RELEASE
    }

    public enum EdgeKind {
        /** mapping USED_BY data source (mapping reads from source). */
        USED_BY,
        /** pipeline PRODUCED a cube/job output. */
        PRODUCED,
        /** release VALIDATED_BY a validation suite. */
        VALIDATED_BY,
        /** release DERIVED_FROM a mapping/ontology/etc. version. */
        DERIVED_FROM,
        /** resource BELONGS_TO project (structural edge). */
        BELONGS_TO,
        /** resource REFERENCES another resource (generic dependency). */
        REFERENCES
    }
}
