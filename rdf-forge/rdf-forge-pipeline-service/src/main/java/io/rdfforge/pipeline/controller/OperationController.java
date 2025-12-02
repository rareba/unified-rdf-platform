package io.rdfforge.pipeline.controller;

import io.rdfforge.engine.operation.OperationRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1/operations")
@RequiredArgsConstructor
@Tag(name = "Operations", description = "Operation discovery API")
@CrossOrigin(origins = "*")
public class OperationController {
    private final OperationRegistry operationRegistry;

    @GetMapping
    @Operation(summary = "List available operations")
    public ResponseEntity<List<OperationRegistry.OperationInfo>> list() {
        // Flatten the catalog for the frontend
        List<OperationRegistry.OperationInfo> allOps = new ArrayList<>();
        operationRegistry.getCatalog().values().forEach(allOps::addAll);
        return ResponseEntity.ok(allOps);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get operation details")
    public ResponseEntity<OperationRegistry.OperationInfo> getById(@PathVariable String id) {
        return operationRegistry.get(id)
            .map(op -> ResponseEntity.ok(new OperationRegistry.OperationInfo(
                op.getId(), op.getName(), op.getDescription(), op.getType(), op.getParameters()
            )))
            .orElse(ResponseEntity.notFound().build());
    }
}
