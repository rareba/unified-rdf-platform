package io.rdfforge.triplestore.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "triplestore_connections")
public class TriplestoreConnectionEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "project_id")
    private UUID projectId;
    
    @Column(nullable = false)
    private String name;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TriplestoreType type;
    
    @Column(nullable = false)
    private String url;
    
    @Column(name = "default_graph")
    private String defaultGraph;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "auth_type")
    private AuthType authType = AuthType.NONE;
    
    @Column(name = "auth_config", columnDefinition = "jsonb")
    @Convert(converter = JsonMapConverter.class)
    private Map<String, Object> authConfig;
    
    @Column(name = "is_default")
    private Boolean isDefault = false;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "health_status")
    private HealthStatus healthStatus = HealthStatus.UNKNOWN;
    
    @Column(name = "last_health_check")
    private Instant lastHealthCheck;
    
    @Column(name = "created_by")
    private UUID createdBy;
    
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
    
    public enum TriplestoreType {
        FUSEKI, STARDOG, GRAPHDB, NEPTUNE, VIRTUOSO, BLAZEGRAPH
    }
    
    public enum AuthType {
        NONE, BASIC, API_KEY, OAUTH2
    }
    
    public enum HealthStatus {
        HEALTHY, UNHEALTHY, UNKNOWN
    }
    
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    
    public UUID getProjectId() { return projectId; }
    public void setProjectId(UUID projectId) { this.projectId = projectId; }
    
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    
    public TriplestoreType getType() { return type; }
    public void setType(TriplestoreType type) { this.type = type; }
    
    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }
    
    public String getDefaultGraph() { return defaultGraph; }
    public void setDefaultGraph(String defaultGraph) { this.defaultGraph = defaultGraph; }
    
    public AuthType getAuthType() { return authType; }
    public void setAuthType(AuthType authType) { this.authType = authType; }
    
    public Map<String, Object> getAuthConfig() { return authConfig; }
    public void setAuthConfig(Map<String, Object> authConfig) { this.authConfig = authConfig; }
    
    public Boolean getIsDefault() { return isDefault; }
    public void setIsDefault(Boolean isDefault) { this.isDefault = isDefault; }
    
    public HealthStatus getHealthStatus() { return healthStatus; }
    public void setHealthStatus(HealthStatus healthStatus) { this.healthStatus = healthStatus; }
    
    public Instant getLastHealthCheck() { return lastHealthCheck; }
    public void setLastHealthCheck(Instant lastHealthCheck) { this.lastHealthCheck = lastHealthCheck; }
    
    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
    
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
