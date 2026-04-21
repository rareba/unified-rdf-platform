package io.rdfforge.triplestore.dto;

import io.rdfforge.triplestore.entity.SavedQueryEntity;
import io.rdfforge.triplestore.entity.SavedQueryEntity.QueryType;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * DTOs for the Phase 7 SPARQL Workbench endpoints.
 */
public final class SavedQueryDtos {

    private SavedQueryDtos() {}

    public record SavedQueryDto(
            UUID id,
            UUID projectId,
            String name,
            String description,
            QueryType type,
            String queryText,
            Map<String, Object> parameters,
            List<String> tags,
            UUID createdBy,
            Instant createdAt,
            Instant updatedAt,
            Integer runCount,
            Instant lastRun
    ) {
        public static SavedQueryDto from(SavedQueryEntity e) {
            return new SavedQueryDto(
                    e.getId(),
                    e.getProjectId(),
                    e.getName(),
                    e.getDescription(),
                    e.getType(),
                    e.getQueryText(),
                    e.getParameters(),
                    e.getTags(),
                    e.getCreatedBy(),
                    e.getCreatedAt(),
                    e.getUpdatedAt(),
                    e.getRunCount(),
                    e.getLastRun()
            );
        }
    }

    public record SavedQueryCreateRequest(
            UUID projectId,
            String name,
            String description,
            QueryType type,
            String queryText,
            Map<String, Object> parameters,
            List<String> tags
    ) {}

    public record SavedQueryUpdateRequest(
            String name,
            String description,
            QueryType type,
            String queryText,
            Map<String, Object> parameters,
            List<String> tags
    ) {}

    /**
     * Request for running a query. When a saved query is run via /{id}/run, the
     * {@code queryText} is ignored. For inline /run the queryText is required.
     */
    public record SavedQueryRunRequest(
            String queryText,
            UUID triplestoreId,
            String graph,
            Map<String, Object> parameters
    ) {}

    /**
     * Polymorphic result envelope. Only one of {@code bindings}, {@code askResult},
     * or {@code rdf} will be populated based on the query type.
     */
    public record SavedQueryRunResponse(
            QueryType type,
            List<String> variables,
            List<Map<String, Object>> bindings,
            Boolean askResult,
            String rdf,
            String rdfFormat,
            long durationMs,
            Instant executedAt
    ) {}
}
