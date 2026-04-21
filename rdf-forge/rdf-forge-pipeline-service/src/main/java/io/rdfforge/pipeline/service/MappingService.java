package io.rdfforge.pipeline.service;

import io.rdfforge.common.exception.MappingRuleException;
import io.rdfforge.common.exception.PipelineValidationException;
import io.rdfforge.common.exception.ResourceNotFoundException;
import io.rdfforge.common.security.AuthUser;
import io.rdfforge.engine.mapping.MappingExecutor;
import io.rdfforge.engine.mapping.MappingRuleSpec;
import io.rdfforge.engine.mapping.MappingSpec;
import io.rdfforge.engine.mapping.UriTemplateEngine;
import io.rdfforge.pipeline.dto.*;
import io.rdfforge.pipeline.entity.MappingEntity;
import io.rdfforge.pipeline.entity.MappingRule;
import io.rdfforge.pipeline.entity.MappingType;
import io.rdfforge.pipeline.entity.ProjectEntity;
import io.rdfforge.pipeline.entity.SourceType;
import io.rdfforge.pipeline.repository.MappingRepository;
import io.rdfforge.pipeline.repository.ProjectRepository;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * CRUD + preview + explain for {@link MappingEntity}. Authorization piggybacks
 * on project ownership: only the project owner (or an admin) may list or
 * mutate mappings belonging to that project. This matches how the Project
 * Workspace treats every other resource — a mapping is a child of a project.
 *
 * <p>Preview/explain delegate to the engine's {@link MappingExecutor}. The
 * service caps sample sizes via
 * {@code rdf-forge.mapping.preview.max-rows} /
 * {@code rdf-forge.mapping.explain.max-rows} to avoid ever running full data
 * sources through the synchronous preview path.
 */
@Slf4j
@Service
public class MappingService {

    private final MappingRepository mappingRepository;
    private final ProjectRepository projectRepository;
    private final MappingExecutor mappingExecutor;

    @Value("${rdf-forge.mapping.preview.max-rows:50}")
    private int previewMaxRows;

    @Value("${rdf-forge.mapping.explain.max-rows:5}")
    private int explainMaxRows;

    public MappingService(MappingRepository mappingRepository,
                          ProjectRepository projectRepository,
                          MappingExecutor mappingExecutor) {
        this.mappingRepository = mappingRepository;
        this.projectRepository = projectRepository;
        this.mappingExecutor = mappingExecutor;
    }

    @Transactional
    public MappingDto create(MappingCreateRequest req, AuthUser user) {
        requireAuthenticated(user);
        ProjectEntity project = loadProjectForWrite(req.projectId(), user);
        validateName(req.name());

        if (mappingRepository.existsByProjectIdAndName(project.getId(), req.name())) {
            throw new PipelineValidationException(
                "A mapping named '" + req.name() + "' already exists in this project");
        }

        List<MappingRule> rules = req.rules() == null ? List.of() : req.rules();
        validateRuleSet(rules, null);

        MappingEntity entity = MappingEntity.builder()
            .projectId(project.getId())
            .name(req.name())
            .description(req.description())
            .sourceType(req.sourceType())
            .sourceConfig(req.sourceConfig())
            .targetNamespace(req.targetNamespace())
            .targetOntologies(req.targetOntologies())
            .rules(new ArrayList<>(rules))
            .mappingType(req.mappingType() == null ? MappingType.GENERIC : req.mappingType())
            .version(1)
            .createdBy(user.id())
            .build();

        try {
            entity = mappingRepository.save(entity);
        } catch (DataIntegrityViolationException e) {
            // Race with another transaction inserting the same (projectId, name).
            log.warn("Duplicate mapping insert for project={} name={}", project.getId(), req.name());
            throw new PipelineValidationException(
                "A mapping named '" + req.name() + "' already exists in this project");
        }

        log.info("Created mapping: id={} project={} owner={}", entity.getId(), project.getId(), user.id());
        return toDto(entity);
    }

    @Transactional
    public MappingDto update(UUID id, MappingUpdateRequest req, AuthUser user) {
        requireAuthenticated(user);
        MappingEntity existing = findOrThrow(id);
        ProjectEntity project = loadProjectForWrite(existing.getProjectId(), user);

        if (req.name() != null) {
            validateName(req.name());
            if (!existing.getName().equals(req.name())
                && mappingRepository.existsByProjectIdAndNameAndIdNot(project.getId(), req.name(), id)) {
                throw new PipelineValidationException(
                    "A mapping named '" + req.name() + "' already exists in this project");
            }
            existing.setName(req.name());
        }
        if (req.description() != null) existing.setDescription(req.description());
        if (req.sourceType() != null) existing.setSourceType(req.sourceType());
        if (req.sourceConfig() != null) existing.setSourceConfig(req.sourceConfig());
        if (req.targetNamespace() != null) existing.setTargetNamespace(req.targetNamespace());
        if (req.targetOntologies() != null) existing.setTargetOntologies(req.targetOntologies());
        if (req.rules() != null) {
            validateRuleSet(req.rules(), null);
            existing.setRules(new ArrayList<>(req.rules()));
            existing.setVersion(existing.getVersion() + 1);
        }

        try {
            existing = mappingRepository.save(existing);
        } catch (DataIntegrityViolationException e) {
            throw new PipelineValidationException(
                "A mapping with that name already exists in this project");
        }
        return toDto(existing);
    }

    @Transactional(readOnly = true)
    public MappingDto findById(UUID id, AuthUser user) {
        requireAuthenticated(user);
        MappingEntity existing = findOrThrow(id);
        loadProjectForRead(existing.getProjectId(), user);
        return toDto(existing);
    }

    @Transactional(readOnly = true)
    public List<MappingDto> listByProject(UUID projectId, AuthUser user) {
        requireAuthenticated(user);
        loadProjectForRead(projectId, user);
        return mappingRepository.findByProjectIdOrderByUpdatedAtDesc(projectId).stream()
            .map(this::toDto)
            .toList();
    }

    @Transactional
    public void delete(UUID id, AuthUser user) {
        requireAuthenticated(user);
        MappingEntity existing = findOrThrow(id);
        loadProjectForWrite(existing.getProjectId(), user);
        mappingRepository.delete(existing);
        log.info("Deleted mapping: id={} by={}", id, user.id());
    }

    @Transactional(readOnly = true)
    public MappingValidationResponse validate(UUID id, MappingValidationRequest req, AuthUser user) {
        MappingDto dto = findById(id, user);
        List<MappingValidationResponse.ValidationIssue> issues =
            validateRuleSetInternal(dto.rules(), req == null ? null : req.availableColumns());
        return new MappingValidationResponse(issues.isEmpty(), issues);
    }

    @Transactional(readOnly = true)
    public MappingPreviewResponse preview(UUID id, MappingPreviewRequest req, AuthUser user) {
        MappingEntity entity = findOrThrow(id);
        loadProjectForRead(entity.getProjectId(), user);

        List<Map<String, Object>> rows = resolveRows(req == null ? null : req.sourceRows(),
                                                     req == null ? null : req.sampleLimit(),
                                                     previewMaxRows);
        int totalSourceRows = req == null || req.sourceRows() == null ? 0 : req.sourceRows().size();
        int sampleSize = rows.size();

        MappingSpec spec = toSpec(entity);
        List<MappingExecutor.RowResult> results = mappingExecutor.executeLenient(spec, rows);

        List<TripleDto> triples = new ArrayList<>();
        for (MappingExecutor.RowResult rr : results) {
            for (Triple t : rr.triples()) {
                triples.add(toTripleDto(t));
            }
        }
        return new MappingPreviewResponse(triples, sampleSize, totalSourceRows);
    }

    @Transactional(readOnly = true)
    public ExplainResponse explain(UUID id, ExplainRequest req, AuthUser user) {
        MappingEntity entity = findOrThrow(id);
        loadProjectForRead(entity.getProjectId(), user);

        List<Map<String, Object>> allRows = req == null ? null : req.sourceRows();
        List<Map<String, Object>> rows;
        if (allRows == null || allRows.isEmpty()) {
            rows = List.of();
        } else if (req.sourceRowIndex() != null) {
            int idx = req.sourceRowIndex();
            if (idx < 0 || idx >= allRows.size()) {
                throw new PipelineValidationException(
                    "sourceRowIndex out of bounds: " + idx + " of " + allRows.size());
            }
            rows = List.of(allRows.get(idx));
        } else {
            int limit = Math.min(
                req.sampleLimit() == null ? explainMaxRows : Math.min(req.sampleLimit(), explainMaxRows),
                allRows.size());
            rows = allRows.subList(0, limit);
        }

        MappingSpec spec = toSpec(entity);
        List<MappingExecutor.RowResult> results = mappingExecutor.executeLenient(spec, rows);

        List<ExplainResponse.RowExplain> out = new ArrayList<>();
        int baseRowIndex = (req != null && req.sourceRowIndex() != null) ? req.sourceRowIndex() : 0;
        for (int i = 0; i < results.size(); i++) {
            MappingExecutor.RowResult rr = results.get(i);
            List<ExplainResponse.TripleExplain> ts = new ArrayList<>();
            for (MappingExecutor.TripleTrace trace : rr.traces()) {
                List<ExplainResponse.TransformStep> steps = new ArrayList<>();
                if (trace.transforms() != null) {
                    for (var step : trace.transforms()) {
                        steps.add(new ExplainResponse.TransformStep(
                            step.type(), step.input(), step.output(), step.params()));
                    }
                }
                ts.add(new ExplainResponse.TripleExplain(
                    toTripleDto(trace.triple()),
                    new ExplainResponse.ExplainTrace(
                        trace.ruleId(), trace.ruleType(), trace.source(), trace.target(),
                        trace.uriTemplateUsed(), trace.sourceValue(), steps, trace.finalValue()
                    )
                ));
            }
            out.add(new ExplainResponse.RowExplain(baseRowIndex + i, rr.row(), ts));
        }
        return new ExplainResponse(out);
    }

    /**
     * Build a CUBE-type mapping pre-populated with qb:Observation +
     * cube:observedBy rules. Used by future cube wizard / import paths. The
     * existing cube flow (CubeController / CubeService in dimension-service)
     * is untouched — this is an additive surface.
     */
    @Transactional
    public MappingDto createCubeTemplate(UUID projectId, String name, String cubeUri,
                                         List<MappingRule> dimensionsAndMeasures, AuthUser user) {
        requireAuthenticated(user);
        ProjectEntity project = loadProjectForWrite(projectId, user);
        if (mappingRepository.existsByProjectIdAndName(projectId, name)) {
            throw new PipelineValidationException(
                "A mapping named '" + name + "' already exists in this project");
        }

        List<MappingRule> rules = new ArrayList<>();
        rules.add(new MappingRule(
            "cube-subject",
            MappingRule.RuleType.FIXED_URI,
            null,
            null,
            cubeUri + "/observation/${rowIndex}",
            null, null, null
        ));
        rules.add(new MappingRule(
            "observed-by",
            MappingRule.RuleType.CONSTANT,
            cubeUri,
            "https://cube.link/observedBy",
            null,
            null, null, null
        ));
        if (dimensionsAndMeasures != null) rules.addAll(dimensionsAndMeasures);
        validateRuleSet(rules, null);

        MappingEntity entity = MappingEntity.builder()
            .projectId(project.getId())
            .name(name)
            .description("Cube observation template generated from existing cube metadata")
            .sourceType(SourceType.CSV)
            .sourceConfig(Map.of("delimiter", ",", "header", true))
            .targetNamespace(cubeUri.endsWith("/") ? cubeUri : cubeUri + "/")
            .targetOntologies(Map.of("prefixes", Map.of(
                "cube", "https://cube.link/",
                "qb", "http://purl.org/linked-data/cube#"
            )))
            .rules(rules)
            .mappingType(MappingType.CUBE)
            .version(1)
            .createdBy(user.id())
            .build();

        entity = mappingRepository.save(entity);
        return toDto(entity);
    }

    // ────────────────────────── helpers ──────────────────────────

    private List<Map<String, Object>> resolveRows(List<Map<String, Object>> rows, Integer requested, int cap) {
        if (rows == null || rows.isEmpty()) return List.of();
        int limit = requested == null ? cap : Math.max(0, Math.min(requested, cap));
        return limit >= rows.size() ? rows : rows.subList(0, limit);
    }

    private MappingSpec toSpec(MappingEntity entity) {
        List<MappingRuleSpec> ruleSpecs = new ArrayList<>();
        for (MappingRule r : entity.getRules() == null ? List.<MappingRule>of() : entity.getRules()) {
            ruleSpecs.add(new MappingRuleSpec(
                r.id(),
                r.type() == null ? null : MappingRuleSpec.RuleType.valueOf(r.type().name()),
                r.source(), r.target(), r.uriTemplate(), r.datatype(), r.language(), r.transform()
            ));
        }
        String baseUri = entity.getTargetNamespace();
        if (baseUri == null || baseUri.isBlank()) baseUri = "urn:rdf-forge:mapping:" + entity.getId() + ":";
        return new MappingSpec(entity.getId().toString(), baseUri, ruleSpecs);
    }

    private TripleDto toTripleDto(Triple t) {
        Node s = t.getSubject();
        Node p = t.getPredicate();
        Node o = t.getObject();
        String subject = s.isBlank() ? "_:" + s.getBlankNodeLabel() : s.getURI();
        String predicate = p.getURI();
        String object;
        TripleDto.ObjectType kind;
        String datatype = null;
        String language = null;
        if (o.isURI()) {
            object = o.getURI();
            kind = TripleDto.ObjectType.URI;
        } else if (o.isBlank()) {
            object = "_:" + o.getBlankNodeLabel();
            kind = TripleDto.ObjectType.BNODE;
        } else {
            object = o.getLiteralLexicalForm();
            kind = TripleDto.ObjectType.LITERAL;
            if (o.getLiteralDatatypeURI() != null && !o.getLiteralDatatypeURI().isBlank()) {
                datatype = o.getLiteralDatatypeURI();
            }
            if (o.getLiteralLanguage() != null && !o.getLiteralLanguage().isBlank()) {
                language = o.getLiteralLanguage();
            }
        }
        return new TripleDto(subject, predicate, object, kind, datatype, language);
    }

    /**
     * Surface-level rule-set validation: unique ids, non-empty target for
     * literal/constant rules, URI templates have parseable placeholder syntax.
     * Null-safe — null/empty ruleset is accepted so an empty scaffold mapping
     * can be created before the user adds rules.
     */
    private void validateRuleSet(List<MappingRule> rules, List<String> availableColumns) {
        List<MappingValidationResponse.ValidationIssue> issues =
            validateRuleSetInternal(rules, availableColumns);
        if (!issues.isEmpty()) {
            MappingValidationResponse.ValidationIssue first = issues.get(0);
            throw new PipelineValidationException(
                "Invalid rule '" + first.ruleId() + "': " + first.message());
        }
    }

    private List<MappingValidationResponse.ValidationIssue> validateRuleSetInternal(
            List<MappingRule> rules, List<String> availableColumns) {
        List<MappingValidationResponse.ValidationIssue> issues = new ArrayList<>();
        if (rules == null) return issues;
        Set<String> seen = new HashSet<>();
        Set<String> cols = availableColumns == null ? null : new HashSet<>(availableColumns);
        for (MappingRule r : rules) {
            if (r.id() == null || r.id().isBlank()) {
                issues.add(new MappingValidationResponse.ValidationIssue(
                    null, "id", "MISSING_ID", "Rule id is required"));
                continue;
            }
            if (!seen.add(r.id())) {
                issues.add(new MappingValidationResponse.ValidationIssue(
                    r.id(), "id", "DUPLICATE_ID", "Duplicate rule id: " + r.id()));
            }
            if (r.type() == null) {
                issues.add(new MappingValidationResponse.ValidationIssue(
                    r.id(), "type", "MISSING_TYPE", "Rule type is required"));
                continue;
            }
            switch (r.type()) {
                case COLUMN_TO_LITERAL, COLUMN_TO_URI -> {
                    if (r.source() == null || r.source().isBlank()) {
                        issues.add(new MappingValidationResponse.ValidationIssue(
                            r.id(), "source", "MISSING_SOURCE", "source column is required"));
                    } else if (cols != null && !cols.contains(r.source())) {
                        issues.add(new MappingValidationResponse.ValidationIssue(
                            r.id(), "source", "UNKNOWN_COLUMN",
                            "source column '" + r.source() + "' not found in source"));
                    }
                    if (r.type() == MappingRule.RuleType.COLUMN_TO_LITERAL
                        && (r.target() == null || r.target().isBlank())) {
                        issues.add(new MappingValidationResponse.ValidationIssue(
                            r.id(), "target", "MISSING_TARGET", "target predicate is required"));
                    }
                }
                case CONSTANT -> {
                    if (r.target() == null || r.target().isBlank()) {
                        issues.add(new MappingValidationResponse.ValidationIssue(
                            r.id(), "target", "MISSING_TARGET", "target predicate is required"));
                    }
                }
                case FIXED_URI, NESTED -> {
                    if ((r.uriTemplate() == null || r.uriTemplate().isBlank())
                        && (r.source() == null || r.source().isBlank())) {
                        issues.add(new MappingValidationResponse.ValidationIssue(
                            r.id(), "uriTemplate", "MISSING_TEMPLATE",
                            "Either uriTemplate or source is required for " + r.type()));
                    }
                }
            }
            if (r.uriTemplate() != null && !r.uriTemplate().isBlank()) {
                try {
                    UriTemplateEngine.renderOrNull(r.uriTemplate(), Map.of("baseUri", "urn:sanity:"));
                } catch (MappingRuleException e) {
                    issues.add(new MappingValidationResponse.ValidationIssue(
                        r.id(), "uriTemplate", "INVALID_TEMPLATE", e.getMessage()));
                }
            }
        }
        return issues;
    }

    private ProjectEntity loadProjectForRead(UUID projectId, AuthUser user) {
        ProjectEntity project = projectRepository.findById(projectId)
            .orElseThrow(() -> new ResourceNotFoundException("Project", projectId.toString()));
        requireOwnerOrAdmin(project, user, "read");
        return project;
    }

    private ProjectEntity loadProjectForWrite(UUID projectId, AuthUser user) {
        ProjectEntity project = projectRepository.findById(projectId)
            .orElseThrow(() -> new ResourceNotFoundException("Project", projectId.toString()));
        requireOwnerOrAdmin(project, user, "write");
        return project;
    }

    private MappingEntity findOrThrow(UUID id) {
        return mappingRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Mapping", id.toString()));
    }

    private static void requireOwnerOrAdmin(ProjectEntity project, AuthUser user, String action) {
        if (user.isAdmin()) return;
        if (!Objects.equals(project.getCreatedBy(), user.id())) {
            throw new AccessDeniedException("Not authorized to " + action + " mappings in this project");
        }
    }

    private static void requireAuthenticated(AuthUser user) {
        if (user == null || user.isAnonymous()) {
            throw new AccessDeniedException("Authentication required");
        }
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new PipelineValidationException("Mapping name is required");
        }
        if (name.length() > 255) {
            throw new PipelineValidationException("Mapping name must not exceed 255 characters");
        }
    }

    private MappingDto toDto(MappingEntity e) {
        return new MappingDto(
            e.getId(),
            e.getProjectId(),
            e.getName(),
            e.getDescription(),
            e.getSourceType(),
            e.getSourceConfig(),
            e.getTargetNamespace(),
            e.getTargetOntologies(),
            e.getRules() == null ? List.of() : List.copyOf(e.getRules()),
            e.getMappingType(),
            e.getVersion(),
            e.getCreatedBy(),
            e.getCreatedAt(),
            e.getUpdatedAt()
        );
    }
}
