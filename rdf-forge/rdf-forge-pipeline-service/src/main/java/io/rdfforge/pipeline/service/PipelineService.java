package io.rdfforge.pipeline.service;

import io.rdfforge.common.audit.AuditLogService;
import io.rdfforge.common.exception.PipelineValidationException;
import io.rdfforge.common.exception.ResourceNotFoundException;
import io.rdfforge.common.model.Pipeline;
import io.rdfforge.common.model.PipelineStep;
import io.rdfforge.common.util.PipelineDefinitionParser;
import io.rdfforge.engine.operation.OperationRegistry;
import io.rdfforge.pipeline.entity.PipelineEntity;
import io.rdfforge.pipeline.repository.PipelineRepository;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Service for managing pipelines.
 * Provides CRUD operations with comprehensive logging and audit support.
 */
@Slf4j
@Service
public class PipelineService {

    private final PipelineRepository pipelineRepository;
    private final OperationRegistry operationRegistry;
    private final AuditLogService auditLogService;

    @org.springframework.beans.factory.annotation.Autowired
    public PipelineService(PipelineRepository pipelineRepository,
                           OperationRegistry operationRegistry,
                           @org.springframework.beans.factory.annotation.Autowired(required = false) AuditLogService auditLogService) {
        this.pipelineRepository = pipelineRepository;
        this.operationRegistry = operationRegistry;
        this.auditLogService = auditLogService;
    }

    /**
     * Create a new pipeline.
     */
    @Transactional
    public Pipeline create(Pipeline pipeline) {
        long startTime = System.currentTimeMillis();
        log.info("Creating pipeline: name={}, projectId={}", pipeline.getName(), pipeline.getProjectId());
        
        validate(pipeline);

        // Check for duplicate name in the same project
        if (pipeline.getProjectId() != null && pipeline.getName() != null) {
            pipelineRepository.findByProjectIdAndName(pipeline.getProjectId(), pipeline.getName())
                .ifPresent(existing -> {
                    log.warn("Pipeline creation failed: Duplicate name '{}' in project {}", 
                        pipeline.getName(), pipeline.getProjectId());
                    throw new PipelineValidationException(
                        "Pipeline with name '" + pipeline.getName() + "' already exists in this project"
                    );
                });
        }

        PipelineEntity entity = toEntity(pipeline);
        entity.setCreatedAt(Instant.now());

        try {
            entity = pipelineRepository.save(entity);
        } catch (DataIntegrityViolationException e) {
            log.error("Failed to create pipeline due to data integrity violation: {}", e.getMessage());
            throw new PipelineValidationException("A pipeline with this name already exists");
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info("Created pipeline: {} ({}) by user: {} in {}ms", 
            entity.getName(), entity.getId(), entity.getCreatedBy(), duration);
        
        // Audit log
        if (auditLogService != null) {
            auditLogService.logCreate("Pipeline", entity.getId().toString(), toModel(entity),
                "Pipeline created: " + entity.getName());
        }
        
        return toModel(entity);
    }

    /**
     * Get pipeline by ID.
     */
    @Transactional(readOnly = true)
    public Pipeline getById(UUID id) {
        log.debug("Fetching pipeline by ID: {}", id);
        
        PipelineEntity entity = pipelineRepository.findById(id)
            .orElseThrow(() -> {
                log.warn("Pipeline not found: {}", id);
                return new ResourceNotFoundException("Pipeline", id.toString());
            });
        
        log.debug("Found pipeline: {} ({})", entity.getName(), id);
        return toModel(entity);
    }

    /**
     * List pipelines with optional project filter.
     */
    @Transactional(readOnly = true)
    public Page<Pipeline> list(UUID projectId, Pageable pageable) {
        long startTime = System.currentTimeMillis();
        log.debug("Listing pipelines: projectId={}, page={}, size={}", 
            projectId, pageable.getPageNumber(), pageable.getPageSize());
        
        Page<PipelineEntity> entities = pipelineRepository.findAllByOptionalProjectId(projectId, pageable);
        Page<Pipeline> result = entities.map(this::toModel);
        
        long duration = System.currentTimeMillis() - startTime;
        log.debug("Listed {} pipelines in {}ms", result.getNumberOfElements(), duration);
        
        // Audit log for listing
        if (auditLogService != null && result.hasContent()) {
            auditLogService.logList("Pipeline", "Listed pipelines for project: " + projectId,
                result.getNumberOfElements());
        }
        
        return result;
    }

    /**
     * Search pipelines by query string.
     */
    @Transactional(readOnly = true)
    public Page<Pipeline> search(UUID projectId, String query, Pageable pageable) {
        long startTime = System.currentTimeMillis();
        log.debug("Searching pipelines: projectId={}, query={}", projectId, query);
        
        Page<PipelineEntity> entities = pipelineRepository.searchByOptionalProjectId(projectId, query, pageable);
        Page<Pipeline> result = entities.map(this::toModel);
        
        long duration = System.currentTimeMillis() - startTime;
        log.debug("Found {} pipelines matching '{}' in {}ms", result.getNumberOfElements(), query, duration);
        
        return result;
    }

    /**
     * Update an existing pipeline.
     */
    @Transactional
    public Pipeline update(UUID id, Pipeline pipeline) {
        long startTime = System.currentTimeMillis();
        log.info("Updating pipeline: id={}", id);
        
        PipelineEntity existing = pipelineRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Pipeline", id.toString()));

        // Store before values for audit
        Pipeline beforeValues = toModel(existing);

        validate(pipeline);

        // Check for duplicate name when renaming (excluding current pipeline)
        if (pipeline.getName() != null && !pipeline.getName().equals(existing.getName())) {
            UUID projectId = existing.getProjectId();
            if (projectId != null) {
                pipelineRepository.findByProjectIdAndName(projectId, pipeline.getName())
                    .ifPresent(duplicate -> {
                        if (!duplicate.getId().equals(id)) {
                            log.warn("Pipeline update failed: Duplicate name '{}' in project {}", 
                                pipeline.getName(), projectId);
                            throw new PipelineValidationException(
                                "Pipeline with name '" + pipeline.getName() + "' already exists in this project"
                            );
                        }
                    });
            }
        }

        existing.setName(pipeline.getName());
        existing.setDescription(pipeline.getDescription());
        existing.setDefinition(pipeline.getDefinition());
        existing.setDefinitionFormat(pipeline.getDefinitionFormat().name());
        existing.setVariables(pipeline.getVariables());
        existing.setTags(pipeline.getTags());
        existing.setVersion(existing.getVersion() + 1);
        existing.setUpdatedAt(Instant.now());

        try {
            existing = pipelineRepository.save(existing);
        } catch (DataIntegrityViolationException e) {
            log.error("Failed to update pipeline due to data integrity violation: {}", e.getMessage());
            throw new PipelineValidationException("A pipeline with this name already exists");
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info("Updated pipeline: {} ({}) by user: {} in {}ms", 
            existing.getName(), existing.getId(), existing.getUpdatedBy(), duration);
        
        // Audit log
        Pipeline afterValues = toModel(existing);
        if (auditLogService != null) {
            auditLogService.logUpdate("Pipeline", id.toString(), beforeValues, afterValues,
                "Pipeline updated: " + existing.getName());
        }
        
        return afterValues;
    }

    /**
     * Delete a pipeline.
     */
    @Transactional
    public void delete(UUID id) {
        log.info("Deleting pipeline: id={}", id);
        
        PipelineEntity existing = pipelineRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Pipeline", id.toString()));

        String pipelineName = existing.getName();
        Pipeline beforeValues = toModel(existing);
        
        pipelineRepository.deleteById(id);
        
        log.info("Deleted pipeline: {} ({}) by user request", pipelineName, id);
        
        // Audit log
        if (auditLogService != null) {
            auditLogService.logDelete("Pipeline", id.toString(), beforeValues,
                "Pipeline deleted: " + pipelineName);
        }
    }

    /**
     * Get all template pipelines.
     */
    @Transactional(readOnly = true)
    public List<Pipeline> getTemplates() {
        log.debug("Fetching template pipelines");
        
        List<Pipeline> templates = pipelineRepository.findByIsTemplateTrue().stream()
            .map(this::toModel)
            .toList();
        
        log.debug("Found {} template pipelines", templates.size());
        return templates;
    }

    /**
     * Validate a pipeline definition.
     */
    @Transactional(readOnly = true)
    public ValidationResult validate(Pipeline pipeline) {
        long startTime = System.currentTimeMillis();
        log.debug("Validating pipeline: name={}", pipeline.getName());
        
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // Name validation
        if (pipeline.getName() == null || pipeline.getName().isBlank()) {
            errors.add("Pipeline name is required");
        } else if (pipeline.getName().length() > 255) {
            errors.add("Pipeline name must not exceed 255 characters");
        } else if (!pipeline.getName().matches("^[a-zA-Z0-9\\s\\-_\\.]+$")) {
            errors.add("Pipeline name contains invalid characters. Only alphanumeric, spaces, hyphens, underscores, and periods are allowed");
        }

        // Definition validation
        if (pipeline.getDefinition() == null || pipeline.getDefinition().isBlank()) {
            errors.add("Pipeline definition is required");
        } else if (pipeline.getDefinition().length() > 100000) {
            errors.add("Pipeline definition must not exceed 100KB");
        }

        // Parse and validate structure
        if (pipeline.getDefinition() != null && !pipeline.getDefinition().isBlank()) {
            try {
                List<PipelineStep> steps = parseDefinition(pipeline.getDefinition(), pipeline.getDefinitionFormat());

                if (steps.isEmpty()) {
                    errors.add("Pipeline must have at least one step");
                }

                Set<String> stepIds = new HashSet<>();
                Set<String> undefinedStepIds = new HashSet<>();

                // First pass: collect all step IDs
                for (PipelineStep step : steps) {
                    if (step.getId() == null || step.getId().isBlank()) {
                        errors.add("All steps must have a non-empty ID");
                        continue;
                    }
                    if (stepIds.contains(step.getId())) {
                        errors.add("Duplicate step ID: " + step.getId());
                    }
                    stepIds.add(step.getId());
                }

                // Second pass: validate operations and connections
                for (PipelineStep step : steps) {
                    if (step.getOperationType() == null || step.getOperationType().isBlank()) {
                        errors.add("Step " + step.getId() + " must have an operation type");
                    } else if (!operationRegistry.get(step.getOperationType()).isPresent()) {
                        errors.add("Unknown operation type: '" + step.getOperationType() + "' in step '" + step.getId() + "'. " +
                                  "Available operations can be found at /api/v1/pipelines/operations");
                    }

                    // Validate input connections
                    if (step.getInputConnections() != null && !step.getInputConnections().isEmpty()) {
                        for (String inputId : step.getInputConnections()) {
                            if (!stepIds.contains(inputId)) {
                                undefinedStepIds.add(inputId);
                            }
                        }
                    }
                }

                // Report undefined connections
                for (String undefinedId : undefinedStepIds) {
                    errors.add("Pipeline references undefined step ID: " + undefinedId);
                }

                // Check for circular dependencies
                if (hasCircularDependencies(steps)) {
                    errors.add("Pipeline contains circular dependencies between steps");
                }

            } catch (PipelineValidationException e) {
                errors.add("Failed to parse pipeline definition: " + e.getMessage());
            } catch (Exception e) {
                log.error("Unexpected error validating pipeline definition", e);
                errors.add("Failed to parse pipeline definition: " + e.getMessage());
            }
        }

        if (!errors.isEmpty()) {
            log.warn("Pipeline validation failed with {} errors: {}", errors.size(), String.join("; ", errors));
            throw new PipelineValidationException(String.join("; ", errors));
        }

        long duration = System.currentTimeMillis() - startTime;
        log.debug("Pipeline validation completed in {}ms", duration);
        
        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }

    private boolean hasCircularDependencies(List<PipelineStep> steps) {
        Map<String, Set<String>> graph = new HashMap<>();
        for (PipelineStep step : steps) {
            graph.put(step.getId(), new HashSet<>(
                step.getInputConnections() != null ? step.getInputConnections() : Collections.emptyList()
            ));
        }

        Set<String> visited = new HashSet<>();
        Set<String> recursionStack = new HashSet<>();

        for (String stepId : graph.keySet()) {
            if (hasCycle(stepId, graph, visited, recursionStack)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasCycle(String node, Map<String, Set<String>> graph, 
                             Set<String> visited, Set<String> recursionStack) {
        if (recursionStack.contains(node)) return true;
        if (visited.contains(node)) return false;

        visited.add(node);
        recursionStack.add(node);

        for (String neighbor : graph.getOrDefault(node, Collections.emptySet())) {
            if (hasCycle(neighbor, graph, visited, recursionStack)) {
                return true;
            }
        }

        recursionStack.remove(node);
        return false;
    }

    private List<PipelineStep> parseDefinition(String definition, Pipeline.DefinitionFormat format) {
        try {
            return PipelineDefinitionParser.parse(definition, format);
        } catch (PipelineDefinitionParser.PipelineParseException e) {
            throw new PipelineValidationException("Failed to parse pipeline: " + e.getMessage(), e);
        }
    }

    /**
     * Duplicate an existing pipeline.
     */
    @Transactional
    public Pipeline duplicate(UUID id, String newName) {
        long startTime = System.currentTimeMillis();
        log.info("Duplicating pipeline: sourceId={}, newName={}", id, newName);
        
        Pipeline original = getById(id);
        
        Pipeline copy = Pipeline.builder()
            .projectId(original.getProjectId())
            .name(newName != null ? newName : original.getName() + " (copy)")
            .description(original.getDescription())
            .definitionFormat(original.getDefinitionFormat())
            .definition(original.getDefinition())
            .variables(new HashMap<>(original.getVariables()))
            .template(false)
            .build();
        
        Pipeline result = create(copy);
        
        long duration = System.currentTimeMillis() - startTime;
        log.info("Duplicated pipeline: {} -> {} in {}ms", id, result.getId(), duration);
        
        return result;
    }

    // Entity <-> Model conversion methods

    private PipelineEntity toEntity(Pipeline model) {
        return PipelineEntity.builder()
            .id(model.getId())
            .projectId(model.getProjectId())
            .name(model.getName())
            .description(model.getDescription())
            .definitionFormat(model.getDefinitionFormat() != null ? model.getDefinitionFormat().name() : "YAML")
            .definition(model.getDefinition())
            .variables(model.getVariables())
            .tags(model.getTags())
            .version(model.getVersion())
            .isTemplate(model.isTemplate())
            .createdBy(model.getCreatedBy())
            .updatedBy(model.getUpdatedBy())
            .build();
    }

    private Pipeline toModel(PipelineEntity entity) {
        return Pipeline.builder()
            .id(entity.getId())
            .projectId(entity.getProjectId())
            .name(entity.getName())
            .description(entity.getDescription())
            .definitionFormat(Pipeline.DefinitionFormat.valueOf(entity.getDefinitionFormat()))
            .definition(entity.getDefinition())
            .variables(sanitizeVariables(entity.getVariables()))
            .tags(entity.getTags())
            .version(entity.getVersion())
            .template(entity.getIsTemplate())
            .createdBy(entity.getCreatedBy())
            .updatedBy(entity.getUpdatedBy())
            .createdAt(entity.getCreatedAt())
            .updatedAt(entity.getUpdatedAt())
            .build();
    }

    private static final Set<String> SENSITIVE_VARIABLE_KEYS = Set.of(
        "accessToken", "access_token", "password", "secret", "apiKey", "api_key", "token"
    );

    /**
     * Sanitize pipeline variables by redacting values of known sensitive keys
     * before returning through API responses.
     */
    private Map<String, Object> sanitizeVariables(Map<String, Object> variables) {
        if (variables == null || variables.isEmpty()) {
            return variables;
        }
        Map<String, Object> sanitized = new HashMap<>(variables);
        for (String key : sanitized.keySet()) {
            if (SENSITIVE_VARIABLE_KEYS.stream().anyMatch(s -> key.toLowerCase().contains(s.toLowerCase()))) {
                sanitized.put(key, "***REDACTED***");
            }
        }
        return sanitized;
    }

    public record ValidationResult(boolean valid, List<String> errors, List<String> warnings) {}
}
