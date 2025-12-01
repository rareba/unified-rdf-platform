package io.rdfforge.pipeline.controller;

import io.rdfforge.common.model.Pipeline;
import io.rdfforge.engine.operation.OperationRegistry;
import io.rdfforge.pipeline.service.PipelineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/pipelines")
@RequiredArgsConstructor
@Tag(name = "Pipelines", description = "Pipeline management API")
@CrossOrigin(origins = "*")
public class PipelineController {
    private final PipelineService pipelineService;
    private final OperationRegistry operationRegistry;

    @PostMapping
    @Operation(summary = "Create a new pipeline")
    public ResponseEntity<Pipeline> create(@Valid @RequestBody Pipeline pipeline) {
        Pipeline created = pipelineService.create(pipeline);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    @Operation(summary = "List pipelines")
    public ResponseEntity<Page<Pipeline>> list(
            @RequestParam(required = false) UUID projectId,
            @RequestParam(required = false) String search,
            Pageable pageable) {
        Page<Pipeline> pipelines;
        if (search != null && !search.isBlank()) {
            pipelines = pipelineService.search(projectId, search, pageable);
        } else {
            pipelines = pipelineService.list(projectId, pageable);
        }
        return ResponseEntity.ok(pipelines);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get pipeline by ID")
    public ResponseEntity<Pipeline> getById(@PathVariable UUID id) {
        Pipeline pipeline = pipelineService.getById(id);
        return ResponseEntity.ok(pipeline);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update pipeline")
    public ResponseEntity<Pipeline> update(@PathVariable UUID id, @Valid @RequestBody Pipeline pipeline) {
        Pipeline updated = pipelineService.update(id, pipeline);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete pipeline")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        pipelineService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/duplicate")
    @Operation(summary = "Duplicate a pipeline")
    public ResponseEntity<Pipeline> duplicate(
            @PathVariable UUID id,
            @RequestParam(required = false) String newName) {
        Pipeline duplicated = pipelineService.duplicate(id, newName);
        return ResponseEntity.status(HttpStatus.CREATED).body(duplicated);
    }

    @PostMapping("/validate")
    @Operation(summary = "Validate a pipeline definition")
    public ResponseEntity<PipelineService.ValidationResult> validate(@RequestBody Pipeline pipeline) {
        PipelineService.ValidationResult result = pipelineService.validate(pipeline);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/templates")
    @Operation(summary = "List pipeline templates")
    public ResponseEntity<List<Pipeline>> getTemplates() {
        List<Pipeline> templates = pipelineService.getTemplates();
        return ResponseEntity.ok(templates);
    }

    @GetMapping("/operations")
    @Operation(summary = "List available operations")
    public ResponseEntity<Map<io.rdfforge.engine.operation.Operation.OperationType, 
            List<OperationRegistry.OperationInfo>>> getOperations() {
        return ResponseEntity.ok(operationRegistry.getCatalog());
    }

    @GetMapping("/operations/{operationId}")
    @Operation(summary = "Get operation details")
    public ResponseEntity<OperationRegistry.OperationInfo> getOperation(@PathVariable String operationId) {
        return operationRegistry.get(operationId)
            .map(op -> ResponseEntity.ok(new OperationRegistry.OperationInfo(
                op.getId(), op.getName(), op.getDescription(), op.getType(), op.getParameters()
            )))
            .orElse(ResponseEntity.notFound().build());
    }
}
