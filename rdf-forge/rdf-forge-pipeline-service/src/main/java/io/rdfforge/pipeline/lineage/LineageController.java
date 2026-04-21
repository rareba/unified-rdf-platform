package io.rdfforge.pipeline.lineage;

import io.rdfforge.common.security.AuthUser;
import io.rdfforge.common.security.CurrentUser;
import io.rdfforge.pipeline.dto.LineageDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Lineage / provenance graph endpoints (Phase 6).
 *
 * <p>Returns a PROV-inspired {@link LineageDto} for either a whole project
 * or a single-resource subgraph. Authorization is enforced in
 * {@link LineageService} against the project owner.
 */
@RestController
@RequestMapping("/api/v1/lineage")
@RequiredArgsConstructor
@Tag(name = "Lineage", description = "Project-scoped provenance / lineage graphs")
@CrossOrigin(origins = "${app.cors.allowed-origins:http://localhost:4200}")
public class LineageController {

    private final LineageService lineageService;

    @GetMapping("/project/{projectId}")
    @Operation(summary = "Full project lineage graph")
    public ResponseEntity<LineageDto> project(
            @PathVariable UUID projectId,
            @CurrentUser AuthUser user) {
        return ResponseEntity.ok(lineageService.forProject(projectId, user));
    }

    @GetMapping("/resource/{kind}/{id}")
    @Operation(summary = "Lineage sub-graph for a single resource (one hop in each direction)")
    public ResponseEntity<LineageDto> resource(
            @PathVariable String kind,
            @PathVariable UUID id,
            @CurrentUser AuthUser user) {
        return ResponseEntity.ok(lineageService.forResource(kind, id, user));
    }
}
