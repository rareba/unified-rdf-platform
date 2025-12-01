package io.rdfforge.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Cube {
    private UUID id;
    private UUID projectId;
    private String uri;
    private String name;
    private String description;
    private UUID sourceDataId;
    private UUID pipelineId;
    private UUID shapeId;
    private CubeMetadata metadata;
    private UUID triplestoreId;
    private String graphUri;
    private Long observationCount;
    private Instant lastPublished;
    private UUID createdBy;
    private Instant createdAt;
    private Instant updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CubeMetadata {
        private String title;
        private String publisher;
        private String license;
        private String contactEmail;
        private TemporalCoverage temporalCoverage;
        private List<String> keywords;
        private List<DimensionMapping> dimensions;
        private List<MeasureMapping> measures;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TemporalCoverage {
        private Instant start;
        private Instant end;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DimensionMapping {
        private String columnName;
        private String dimensionUri;
        private String dimensionType;
        private UUID sharedDimensionId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MeasureMapping {
        private String columnName;
        private String measureUri;
        private String datatype;
        private String unit;
    }
}
