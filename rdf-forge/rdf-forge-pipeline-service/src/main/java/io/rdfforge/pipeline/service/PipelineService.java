package io.rdfforge.pipeline.service;

import io.rdfforge.common.exception.PipelineValidationException;
import io.rdfforge.common.exception.ResourceNotFoundException;
import io.rdfforge.common.model.Pipeline;
import io.rdfforge.common.model.PipelineStep;
import io.rdfforge.engine.operation.OperationRegistry;
import io.rdfforge.pipeline.entity.PipelineEntity;
import io.rdfforge.pipeline.repository.PipelineRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class PipelineService {
    private final PipelineRepository pipelineRepository;
    private final OperationRegistry operationRegistry;
    private final ObjectMapper jsonMapper = new ObjectMapper();
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    @Transactional
    public Pipeline create(Pipeline pipeline) {
        validate(pipeline);
        
        PipelineEntity entity = toEntity(pipeline);
        entity = pipelineRepository.save(entity);
        
        log.info("Created pipeline: {} ({})", entity.getName(), entity.getId());
        return toModel(entity);
    }

    @Transactional(readOnly = true)
    public Pipeline getById(UUID id) {
        PipelineEntity entity = pipelineRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Pipeline", id.toString()));
        return toModel(entity);
    }

    @Transactional(readOnly = true)
    public Page<Pipeline> list(UUID projectId, Pageable pageable) {
        Page<PipelineEntity> entities = pipelineRepository.findByProjectId(projectId, pageable);
        return entities.map(this::toModel);
    }

    @Transactional(readOnly = true)
    public Page<Pipeline> search(UUID projectId, String query, Pageable pageable) {
        Page<PipelineEntity> entities = pipelineRepository.searchByProjectId(projectId, query, pageable);
        return entities.map(this::toModel);
    }

    @Transactional
    public Pipeline update(UUID id, Pipeline pipeline) {
        PipelineEntity existing = pipelineRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Pipeline", id.toString()));
        
        validate(pipeline);
        
        existing.setName(pipeline.getName());
        existing.setDescription(pipeline.getDescription());
        existing.setDefinition(pipeline.getDefinition());
        existing.setDefinitionFormat(pipeline.getDefinitionFormat().name());
        existing.setVariables(pipeline.getVariables());
        existing.setTags(pipeline.getTags() != null ? pipeline.getTags().toArray(new String[0]) : null);
        existing.setVersion(existing.getVersion() + 1);
        
        existing = pipelineRepository.save(existing);
        log.info("Updated pipeline: {} ({})", existing.getName(), existing.getId());
        return toModel(existing);
    }

    @Transactional
    public void delete(UUID id) {
        if (!pipelineRepository.existsById(id)) {
            throw new ResourceNotFoundException("Pipeline", id.toString());
        }
        pipelineRepository.deleteById(id);
        log.info("Deleted pipeline: {}", id);
    }

    @Transactional
    public Pipeline duplicate(UUID id, String newName) {
        Pipeline original = getById(id);
        
        Pipeline copy = Pipeline.builder()
            .projectId(original.getProjectId())
            .name(newName != null ? newName : original.getName() + " (copy)")
            .description(original.getDescription())
            .definitionFormat(original.getDefinitionFormat())
            .definition(original.getDefinition())
            .variables(new HashMap<>(original.getVariables()))
            .tags(new ArrayList<>(original.getTags()))
            .template(false)
            .build();
        
        return create(copy);
    }

    @Transactional(readOnly = true)
    public List<Pipeline> getTemplates() {
        return pipelineRepository.findByIsTemplateTrue().stream()
            .map(this::toModel)
            .toList();
    }

    public ValidationResult validate(Pipeline pipeline) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (pipeline.getName() == null || pipeline.getName().isBlank()) {
            errors.add("Pipeline name is required");
        }

        if (pipeline.getDefinition() == null || pipeline.getDefinition().isBlank()) {
            errors.add("Pipeline definition is required");
        }

        try {
            List<PipelineStep> steps = parseDefinition(pipeline.getDefinition(), pipeline.getDefinitionFormat());
            
            if (steps.isEmpty()) {
                errors.add("Pipeline must have at least one step");
            }

            Set<String> stepIds = new HashSet<>();
            for (PipelineStep step : steps) {
                if (stepIds.contains(step.getId())) {
                    errors.add("Duplicate step ID: " + step.getId());
                }
                stepIds.add(step.getId());

                if (!operationRegistry.get(step.getOperationType()).isPresent()) {
                    errors.add("Unknown operation type: " + step.getOperationType() + " in step " + step.getId());
                }

                if (step.getInputConnections() != null) {
                    for (String inputId : step.getInputConnections()) {
                        if (!stepIds.contains(inputId)) {
                            if (!steps.stream().anyMatch(s -> s.getId().equals(inputId))) {
                                errors.add("Step " + step.getId() + " references unknown input: " + inputId);
                            }
                        }
                    }
                }
            }

            if (!hasCircularDependencies(steps)) {
            } else {
                errors.add("Pipeline contains circular dependencies");
            }

        } catch (Exception e) {
            errors.add("Failed to parse pipeline definition: " + e.getMessage());
        }

        if (!errors.isEmpty()) {
            throw new PipelineValidationException(String.join("; ", errors));
        }

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
            ObjectMapper mapper = format == Pipeline.DefinitionFormat.YAML ? yamlMapper : jsonMapper;
            Map<String, Object> parsed = mapper.readValue(definition, new TypeReference<>() {});
            
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> stepsData = (List<Map<String, Object>>) parsed.get("steps");
            
            if (stepsData == null) {
                return Collections.emptyList();
            }

            List<PipelineStep> steps = new ArrayList<>();
            for (Map<String, Object> stepData : stepsData) {
                @SuppressWarnings("unchecked")
                PipelineStep step = PipelineStep.builder()
                    .id((String) stepData.get("id"))
                    .operationType((String) stepData.get("operation"))
                    .name((String) stepData.get("name"))
                    .parameters((Map<String, Object>) stepData.get("parameters"))
                    .inputConnections((List<String>) stepData.get("inputs"))
                    .outputConnections((List<String>) stepData.get("outputs"))
                    .build();
                steps.add(step);
            }
            return steps;
        } catch (Exception e) {
            throw new PipelineValidationException("Failed to parse pipeline: " + e.getMessage(), e);
        }
    }

    private PipelineEntity toEntity(Pipeline model) {
        return PipelineEntity.builder()
            .id(model.getId())
            .projectId(model.getProjectId())
            .name(model.getName())
            .description(model.getDescription())
            .definitionFormat(model.getDefinitionFormat() != null ? model.getDefinitionFormat().name() : "YAML")
            .definition(model.getDefinition())
            .variables(model.getVariables())
            .tags(model.getTags() != null ? model.getTags().toArray(new String[0]) : null)
            .version(model.getVersion())
            .isTemplate(model.isTemplate())
            .createdBy(model.getCreatedBy())
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
            .variables(entity.getVariables())
            .tags(entity.getTags() != null ? Arrays.asList(entity.getTags()) : Collections.emptyList())
            .version(entity.getVersion())
            .template(entity.getIsTemplate())
            .createdBy(entity.getCreatedBy())
            .createdAt(entity.getCreatedAt())
            .updatedAt(entity.getUpdatedAt())
            .build();
    }

    public record ValidationResult(boolean valid, List<String> errors, List<String> warnings) {}
}
