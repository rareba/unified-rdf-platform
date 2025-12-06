package io.rdfforge.pipeline.gitsync.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Manifest file for configuration repository.
 * This file tracks all configurations and their versions.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConfigManifest {
    private String version;
    private String projectId;
    private String projectName;
    private Instant exportedAt;
    private String exportedBy;
    private List<ConfigEntry> pipelines;
    private List<ConfigEntry> shapes;
    private Map<String, Object> settings;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConfigEntry {
        private String id;
        private String name;
        private String path;
        private String format;
        private Integer version;
        private String checksum;
        private Instant modifiedAt;
    }
}
