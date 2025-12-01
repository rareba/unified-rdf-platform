package io.rdfforge.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Dimension {
    private UUID id;
    private UUID projectId;
    private String uri;
    private String name;
    private String description;
    private DimensionType type;
    private HierarchyType hierarchyType;
    private String content;
    private Integer version;
    private UUID createdBy;
    private Instant createdAt;
    private Instant updatedAt;

    public enum DimensionType {
        TEMPORAL,
        GEO,
        KEY,
        MEASURE,
        ATTRIBUTE
    }

    public enum HierarchyType {
        SKOS,
        CUSTOM,
        FLAT
    }
}
