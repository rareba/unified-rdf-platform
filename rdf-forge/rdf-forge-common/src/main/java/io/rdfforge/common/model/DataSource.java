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
public class DataSource {
    private UUID id;
    private UUID projectId;
    private String name;
    private String originalFilename;
    private DataFormat format;
    private Long sizeBytes;
    private StorageType storageType;
    private String storagePath;
    private DataSourceMetadata metadata;
    private UUID uploadedBy;
    private Instant uploadedAt;

    public enum DataFormat {
        CSV,
        TSV,
        JSON,
        JSON_LD,
        PARQUET,
        XML,
        XLSX,
        TURTLE,
        NTRIPLES,
        RDF_XML,
        UNKNOWN
    }

    public enum StorageType {
        S3,
        LOCAL,
        URL
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DataSourceMetadata {
        private Long rowCount;
        private List<ColumnInfo> columns;
        private List<Map<String, Object>> sampleData;
        private String encoding;
        private String delimiter;
        private boolean hasHeader;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ColumnInfo {
        private String name;
        private String inferredType;
        private boolean nullable;
        private Long distinctCount;
        private List<Object> sampleValues;
    }
}
