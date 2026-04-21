package io.rdfforge.shacl.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Project-scoped ontology / vocabulary document.
 *
 * <p>Stores the serialized RDF content (Turtle, RDF/XML, JSON-LD, etc.) along with
 * metadata such as base namespace, recommended prefix, and version counter.
 * Name must be unique within a project.
 */
@Entity
@Table(name = "ontologies",
       uniqueConstraints = @UniqueConstraint(
           name = "uq_ontologies_project_name",
           columnNames = {"project_id", "name"}
       ),
       indexes = {
           @Index(name = "idx_ontologies_project_id", columnList = "project_id"),
           @Index(name = "idx_ontologies_created_by", columnList = "created_by")
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OntologyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** Base IRI for this ontology (e.g. http://example.org/schema/). */
    @Column(nullable = false, length = 1000)
    private String namespace;

    /** Recommended prefix for the base namespace. */
    @Column(length = 64)
    private String prefix;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RdfFormat format;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private Integer version;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (version == null) version = 1;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    /** RDF serialization format stored alongside the ontology content. */
    public enum RdfFormat {
        TURTLE,
        RDF_XML,
        JSON_LD,
        N_TRIPLES,
        N_QUADS,
        TRIG
    }
}
