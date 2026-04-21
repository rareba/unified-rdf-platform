package io.rdfforge.triplestore.reconciliation;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Persistent record of a match candidate surfaced by a {@link Matcher}.
 * See Phase 8 — Link Discovery / Reconciliation.
 */
@Entity
@Table(
    name = "match_candidates",
    uniqueConstraints = @UniqueConstraint(
        name = "match_candidates_dedupe_unique",
        columnNames = {"project_id", "source_uri", "target_uri", "predicate"})
)
public class MatchCandidateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(name = "source_uri", nullable = false, length = 2000)
    private String sourceUri;

    @Column(name = "target_uri", nullable = false, length = 2000)
    private String targetUri;

    @Enumerated(EnumType.STRING)
    @Column(name = "predicate", nullable = false, length = 32)
    private MatchPredicate predicate;

    @Column(name = "confidence", nullable = false)
    private double confidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 32)
    private MatcherSource source;

    @Column(name = "matcher_name", nullable = false, length = 128)
    private String matcherName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private MatchStatus status = MatchStatus.PENDING;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence", columnDefinition = "jsonb")
    private Map<String, Object> evidence;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (status == null) status = MatchStatus.PENDING;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    public enum MatchPredicate {
        SAME_AS, EXACT_MATCH, CLOSE_MATCH, RELATED_MATCH, BROADER, NARROWER;

        /** Best-effort RDF IRI for each predicate — used when writing approved links. */
        public String rdfIri() {
            return switch (this) {
                case SAME_AS       -> "http://www.w3.org/2002/07/owl#sameAs";
                case EXACT_MATCH   -> "http://www.w3.org/2004/02/skos/core#exactMatch";
                case CLOSE_MATCH   -> "http://www.w3.org/2004/02/skos/core#closeMatch";
                case RELATED_MATCH -> "http://www.w3.org/2004/02/skos/core#relatedMatch";
                case BROADER       -> "http://www.w3.org/2004/02/skos/core#broadMatch";
                case NARROWER      -> "http://www.w3.org/2004/02/skos/core#narrowMatch";
            };
        }
    }

    public enum MatcherSource { LOCAL_DUPLICATE, MANUAL, EXTERNAL_AUTHORITY }

    public enum MatchStatus { PENDING, APPROVED, REJECTED, ARCHIVED }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getProjectId() { return projectId; }
    public void setProjectId(UUID projectId) { this.projectId = projectId; }

    public String getSourceUri() { return sourceUri; }
    public void setSourceUri(String sourceUri) { this.sourceUri = sourceUri; }

    public String getTargetUri() { return targetUri; }
    public void setTargetUri(String targetUri) { this.targetUri = targetUri; }

    public MatchPredicate getPredicate() { return predicate; }
    public void setPredicate(MatchPredicate predicate) { this.predicate = predicate; }

    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }

    public MatcherSource getSource() { return source; }
    public void setSource(MatcherSource source) { this.source = source; }

    public String getMatcherName() { return matcherName; }
    public void setMatcherName(String matcherName) { this.matcherName = matcherName; }

    public MatchStatus getStatus() { return status; }
    public void setStatus(MatchStatus status) { this.status = status; }

    public Map<String, Object> getEvidence() { return evidence; }
    public void setEvidence(Map<String, Object> evidence) { this.evidence = evidence; }

    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }

    public UUID getApprovedBy() { return approvedBy; }
    public void setApprovedBy(UUID approvedBy) { this.approvedBy = approvedBy; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public Instant getDecidedAt() { return decidedAt; }
    public void setDecidedAt(Instant decidedAt) { this.decidedAt = decidedAt; }
}
