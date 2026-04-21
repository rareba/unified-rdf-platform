package io.rdfforge.triplestore.service;

import io.rdfforge.common.exception.ResourceNotFoundException;
import io.rdfforge.common.security.AuthUser;
import io.rdfforge.triplestore.connector.TriplestoreConnector;
import io.rdfforge.triplestore.connector.TriplestoreConnector.QueryResult;
import io.rdfforge.triplestore.connector.TriplestoreConnector.RdfValue;
import io.rdfforge.triplestore.dto.SavedQueryDtos.SavedQueryCreateRequest;
import io.rdfforge.triplestore.dto.SavedQueryDtos.SavedQueryDto;
import io.rdfforge.triplestore.dto.SavedQueryDtos.SavedQueryRunRequest;
import io.rdfforge.triplestore.dto.SavedQueryDtos.SavedQueryRunResponse;
import io.rdfforge.triplestore.dto.SavedQueryDtos.SavedQueryUpdateRequest;
import io.rdfforge.triplestore.entity.SavedQueryEntity;
import io.rdfforge.triplestore.entity.SavedQueryEntity.QueryType;
import io.rdfforge.triplestore.entity.TriplestoreConnectionEntity;
import io.rdfforge.triplestore.repository.SavedQueryRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.query.ParameterizedSparqlString;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.update.UpdateFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for the Phase 7 SPARQL Workbench. Manages saved SPARQL queries and
 * delegates execution to {@link TriplestoreService}.
 *
 * <p><b>Security — SPARQL injection:</b> all user-supplied parameter substitutions
 * go through Jena's {@link ParameterizedSparqlString}. We never concatenate
 * user input into the query text.
 */
@Service
@Transactional
@Slf4j
public class SavedQueryService {

    private final SavedQueryRepository repository;
    private final TriplestoreService triplestoreService;

    public SavedQueryService(SavedQueryRepository repository, TriplestoreService triplestoreService) {
        this.repository = repository;
        this.triplestoreService = triplestoreService;
    }

    // ==================== CRUD ====================

    @Transactional(readOnly = true)
    public List<SavedQueryDto> list(UUID projectId, List<String> tagFilter, AuthUser user) {
        requireAuthenticated(user);
        List<SavedQueryEntity> all = repository.findByProjectIdOrderByNameAsc(projectId);
        return all.stream()
                .filter(e -> matchesTags(e, tagFilter))
                .map(SavedQueryDto::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public SavedQueryDto get(UUID id, AuthUser user) {
        SavedQueryEntity entity = requireReadable(id, user);
        return SavedQueryDto.from(entity);
    }

    public SavedQueryDto create(SavedQueryCreateRequest request, AuthUser user) {
        requireAuthenticated(user);
        if (request.projectId() == null) {
            throw new IllegalArgumentException("projectId is required");
        }
        if (request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        if (request.queryText() == null || request.queryText().isBlank()) {
            throw new IllegalArgumentException("queryText is required");
        }
        if (repository.existsByProjectIdAndName(request.projectId(), request.name())) {
            throw new IllegalArgumentException(
                "A saved query with name '" + request.name() + "' already exists in this project");
        }

        QueryType type = request.type() != null ? request.type() : inferQueryType(request.queryText());
        validateQueryParsable(request.queryText(), type);

        SavedQueryEntity entity = new SavedQueryEntity();
        entity.setProjectId(request.projectId());
        entity.setName(request.name());
        entity.setDescription(request.description());
        entity.setType(type);
        entity.setQueryText(request.queryText());
        entity.setParameters(request.parameters());
        entity.setTags(request.tags());
        entity.setCreatedBy(user.id());
        entity.setRunCount(0);

        return SavedQueryDto.from(repository.save(entity));
    }

    public SavedQueryDto update(UUID id, SavedQueryUpdateRequest request, AuthUser user) {
        SavedQueryEntity entity = requireWritable(id, user);

        if (request.name() != null && !request.name().equals(entity.getName())) {
            boolean clash = repository.findByProjectIdAndName(entity.getProjectId(), request.name())
                    .filter(other -> !other.getId().equals(id))
                    .isPresent();
            if (clash) {
                throw new IllegalArgumentException(
                    "A saved query with name '" + request.name() + "' already exists in this project");
            }
            entity.setName(request.name());
        }
        if (request.description() != null) entity.setDescription(request.description());
        if (request.type() != null) entity.setType(request.type());
        if (request.queryText() != null) {
            validateQueryParsable(request.queryText(), entity.getType());
            entity.setQueryText(request.queryText());
        }
        if (request.parameters() != null) entity.setParameters(request.parameters());
        if (request.tags() != null) entity.setTags(request.tags());

        return SavedQueryDto.from(repository.save(entity));
    }

    public void delete(UUID id, AuthUser user) {
        requireWritable(id, user);
        repository.deleteById(id);
    }

    // ==================== Execution ====================

    /**
     * Run a saved query — substitute parameters, execute, bump counters.
     */
    public SavedQueryRunResponse run(UUID queryId, SavedQueryRunRequest request, AuthUser user) {
        SavedQueryEntity entity = requireReadable(queryId, user);
        if (request.triplestoreId() == null) {
            throw new IllegalArgumentException("triplestoreId is required");
        }
        requireTriplestoreAccess(request.triplestoreId(), user);

        String substituted = substituteParameters(entity.getQueryText(), request.parameters());
        SavedQueryRunResponse response = executeSubstituted(
            substituted, entity.getType(), request.triplestoreId(), request.graph());

        entity.setRunCount((entity.getRunCount() == null ? 0 : entity.getRunCount()) + 1);
        entity.setLastRun(Instant.now());
        repository.save(entity);

        return response;
    }

    /**
     * Run an ad-hoc (unsaved) query from the Workbench UI. Still subject to
     * triplestore ownership auth.
     */
    public SavedQueryRunResponse runInline(SavedQueryRunRequest request, AuthUser user) {
        requireAuthenticated(user);
        if (request.queryText() == null || request.queryText().isBlank()) {
            throw new IllegalArgumentException("queryText is required");
        }
        if (request.triplestoreId() == null) {
            throw new IllegalArgumentException("triplestoreId is required");
        }
        requireTriplestoreAccess(request.triplestoreId(), user);

        QueryType type = inferQueryType(request.queryText());
        String substituted = substituteParameters(request.queryText(), request.parameters());
        return executeSubstituted(substituted, type, request.triplestoreId(), request.graph());
    }

    // ==================== Internal ====================

    /**
     * Substitute parameters into a SPARQL query using Jena's
     * {@link ParameterizedSparqlString} — safe against SPARQL injection.
     *
     * <p>Parameter spec example: {@code { "label": { "type": "literal", "default": "Paris" } }}.
     * At call time, if the user supplies a value for {@code label}, we bind
     * it; otherwise we use the {@code default}. Types:
     * <ul>
     *   <li>{@code uri} — bound as an IRI</li>
     *   <li>{@code literal} / {@code string} — bound as a plain literal</li>
     *   <li>{@code number} — parsed as long or double</li>
     * </ul>
     */
    String substituteParameters(String queryText, Map<String, Object> supplied) {
        if (queryText == null) return null;
        ParameterizedSparqlString pss = new ParameterizedSparqlString(queryText);
        if (supplied == null) return pss.toString();

        for (Map.Entry<String, Object> e : supplied.entrySet()) {
            String name = e.getKey();
            Object raw = e.getValue();
            if (name == null || raw == null) continue;

            String type = null;
            Object value = raw;
            if (raw instanceof Map<?, ?> map) {
                Object t = map.get("type");
                if (t != null) type = t.toString().toLowerCase(Locale.ROOT);
                value = map.get("value");
                if (value == null) value = map.get("default");
            }

            if (value == null) continue;
            String s = value.toString();

            if ("uri".equals(type) || "iri".equals(type)) {
                pss.setIri(name, s);
            } else if ("number".equals(type)) {
                try {
                    if (s.contains(".")) {
                        pss.setLiteral(name, Double.parseDouble(s));
                    } else {
                        pss.setLiteral(name, Long.parseLong(s));
                    }
                } catch (NumberFormatException nfe) {
                    pss.setLiteral(name, s);
                }
            } else {
                // literal / string / unknown — always escape via setLiteral
                pss.setLiteral(name, s);
            }
        }
        return pss.toString();
    }

    private SavedQueryRunResponse executeSubstituted(String queryText, QueryType type, UUID triplestoreId, String graph) {
        Instant start = Instant.now();
        long t0 = System.currentTimeMillis();

        if (type == QueryType.UPDATE) {
            // Validate parse, then delegate to TriplestoreService update path.
            UpdateFactory.create(queryText);
            triplestoreService.executeUpdate(triplestoreId, queryText, graph);
            long duration = System.currentTimeMillis() - t0;
            return new SavedQueryRunResponse(type, null, null, null, null, null, duration, start);
        }

        QueryResult result = triplestoreService.executeQuery(triplestoreId, queryText, graph);
        long duration = System.currentTimeMillis() - t0;

        switch (type) {
            case ASK -> {
                boolean ask = false;
                if (!result.bindings().isEmpty()) {
                    RdfValue v = result.bindings().get(0).get("result");
                    if (v != null) ask = Boolean.parseBoolean(v.value());
                }
                return new SavedQueryRunResponse(type, null, null, ask, null, null, duration, start);
            }
            case SELECT -> {
                List<Map<String, Object>> bindings = convertBindings(result.bindings());
                return new SavedQueryRunResponse(type, result.variables(), bindings, null, null, null, duration, start);
            }
            case CONSTRUCT, DESCRIBE -> {
                // AbstractSparqlConnector doesn't yet support these — flatten bindings and also
                // return the raw query text as a hint. Construct/describe can still be exported
                // via the triplestore's export endpoint. Mark as follow-up in TODO.
                List<Map<String, Object>> bindings = convertBindings(result.bindings());
                return new SavedQueryRunResponse(type, result.variables(), bindings, null, null, null, duration, start);
            }
            default -> throw new IllegalStateException("Unreachable query type: " + type);
        }
    }

    private List<Map<String, Object>> convertBindings(List<Map<String, RdfValue>> raw) {
        List<Map<String, Object>> out = new ArrayList<>(raw.size());
        for (Map<String, RdfValue> row : raw) {
            Map<String, Object> converted = new LinkedHashMap<>();
            for (var e : row.entrySet()) {
                RdfValue v = e.getValue();
                if (v == null) continue;
                Map<String, Object> cell = new LinkedHashMap<>();
                cell.put("type", v.type());
                cell.put("value", v.value());
                if (v.datatype() != null) cell.put("datatype", v.datatype());
                if (v.language() != null) cell.put("language", v.language());
                converted.put(e.getKey(), cell);
            }
            out.add(converted);
        }
        return out;
    }

    /** Parse query to fail-fast on malformed text before persisting. */
    private void validateQueryParsable(String text, QueryType type) {
        try {
            if (type == QueryType.UPDATE) {
                UpdateFactory.create(text);
            } else {
                QueryFactory.create(text);
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid SPARQL " + type + ": " + e.getMessage());
        }
    }

    static QueryType inferQueryType(String text) {
        if (text == null) return QueryType.SELECT;
        // Try to parse as query first, then update
        try {
            Query q = QueryFactory.create(text);
            if (q.isAskType()) return QueryType.ASK;
            if (q.isConstructType()) return QueryType.CONSTRUCT;
            if (q.isDescribeType()) return QueryType.DESCRIBE;
            return QueryType.SELECT;
        } catch (Exception ignored) {
            try {
                UpdateFactory.create(text);
                return QueryType.UPDATE;
            } catch (Exception ignoredToo) {
                // Fall back to naive textual scan
                String upper = text.toUpperCase(Locale.ROOT).trim();
                if (upper.startsWith("ASK"))        return QueryType.ASK;
                if (upper.startsWith("CONSTRUCT"))  return QueryType.CONSTRUCT;
                if (upper.startsWith("DESCRIBE"))   return QueryType.DESCRIBE;
                if (upper.contains("INSERT") || upper.contains("DELETE") || upper.contains("DROP") || upper.contains("LOAD") || upper.contains("CLEAR")) {
                    return QueryType.UPDATE;
                }
                return QueryType.SELECT;
            }
        }
    }

    private static boolean matchesTags(SavedQueryEntity e, List<String> filter) {
        if (filter == null || filter.isEmpty()) return true;
        if (e.getTags() == null) return false;
        for (String t : filter) {
            if (!e.getTags().contains(t)) return false;
        }
        return true;
    }

    // ==================== Authorization ====================

    private void requireAuthenticated(AuthUser user) {
        if (user == null || user.isAnonymous()) {
            throw new AccessDeniedException("Authentication required");
        }
    }

    private SavedQueryEntity requireReadable(UUID id, AuthUser user) {
        requireAuthenticated(user);
        SavedQueryEntity e = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SavedQuery", id.toString()));
        if (user.isAdmin()) return e;
        UUID owner = e.getCreatedBy();
        if (owner == null || !owner.equals(user.id())) {
            throw new AccessDeniedException("Not authorized to read this saved query");
        }
        return e;
    }

    private SavedQueryEntity requireWritable(UUID id, AuthUser user) {
        requireAuthenticated(user);
        SavedQueryEntity e = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("SavedQuery", id.toString()));
        if (user.isAdmin()) return e;
        UUID owner = e.getCreatedBy();
        if (owner == null || !owner.equals(user.id())) {
            throw new AccessDeniedException("Not authorized to modify this saved query");
        }
        return e;
    }

    /** Mirror of TriplestoreController ownership rules for the endpoint used by the run call. */
    private void requireTriplestoreAccess(UUID triplestoreId, AuthUser user) {
        requireAuthenticated(user);
        Optional<TriplestoreConnectionEntity> opt = triplestoreService.getConnection(triplestoreId);
        TriplestoreConnectionEntity conn = opt.orElseThrow(
                () -> new ResourceNotFoundException("TriplestoreConnection", triplestoreId.toString()));
        if (user.isAdmin()) return;
        UUID owner = conn.getCreatedBy();
        if (owner == null || !owner.equals(user.id())) {
            throw new AccessDeniedException("Not authorized to use this triplestore connection");
        }
    }

    /** Exposed for test access only — do not use from other services. */
    List<String> asTagList(Object tags) {
        if (tags instanceof List<?> list) {
            return list.stream().map(Object::toString).toList();
        }
        return Collections.emptyList();
    }
}
