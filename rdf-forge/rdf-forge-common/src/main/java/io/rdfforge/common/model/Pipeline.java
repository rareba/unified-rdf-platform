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
public class Pipeline {
    private UUID id;
    private UUID projectId;
    private String name;
    private String description;
    private DefinitionFormat definitionFormat;
    private String definition;
    private Map<String, Object> variables;
    private List<String> tags;
    private Integer version;
    private boolean template;
    private UUID createdBy;
    private Instant createdAt;
    private Instant updatedAt;

    public enum DefinitionFormat {
        YAML,
        TURTLE,
        JSON
    }
}
