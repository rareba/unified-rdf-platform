package io.rdfforge.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TriplestoreConnection {
    private UUID id;
    private UUID projectId;
    private String name;
    private TriplestoreType type;
    private String url;
    private String defaultGraph;
    private AuthType authType;
    private Map<String, String> authConfig;
    private boolean defaultConnection;
    private HealthStatus healthStatus;
    private Instant lastHealthCheck;
    private UUID createdBy;
    private Instant createdAt;

    public enum TriplestoreType {
        FUSEKI,
        STARDOG,
        GRAPHDB,
        NEPTUNE,
        VIRTUOSO,
        BLAZEGRAPH,
        GENERIC_SPARQL
    }

    public enum AuthType {
        NONE,
        BASIC,
        API_KEY,
        OAUTH2,
        BEARER_TOKEN
    }

    public enum HealthStatus {
        HEALTHY,
        UNHEALTHY,
        UNKNOWN
    }
}
