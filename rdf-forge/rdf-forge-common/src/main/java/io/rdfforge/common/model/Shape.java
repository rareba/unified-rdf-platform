package io.rdfforge.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Shape {
    private UUID id;
    private UUID projectId;
    private String uri;
    private String name;
    private String description;
    private String targetClass;
    private ContentFormat contentFormat;
    private String content;
    private boolean template;
    private String category;
    private List<String> tags;
    private Integer version;
    private UUID createdBy;
    private Instant createdAt;
    private Instant updatedAt;

    public enum ContentFormat {
        TURTLE,
        JSON_LD,
        RDF_XML
    }
}
