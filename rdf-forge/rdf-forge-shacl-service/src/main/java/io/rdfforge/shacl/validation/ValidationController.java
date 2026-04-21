package io.rdfforge.shacl.validation;

import io.rdfforge.common.security.AuthUser;
import io.rdfforge.common.security.CurrentUser;
import io.rdfforge.shacl.validation.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * HTTP surface for the Phase 5 Validation Cockpit.
 *
 * <p>All endpoints are gated by {@link CurrentUser}; suite-level actions
 * additionally enforce owner-or-admin authorization inside
 * {@link ValidationService}.
 */
@RestController
@RequestMapping("/api/v1/validation")
@RequiredArgsConstructor
@Tag(name = "Validation Cockpit",
    description = "Project-scoped SHACL + SPARQL + cube-profile validation suites")
@CrossOrigin(origins = "${app.cors.allowed-origins:http://localhost:4200}")
public class ValidationController {

    private final ValidationService validationService;

    // ----- Suites ---------------------------------------------------------------

    @GetMapping("/suites")
    @Operation(summary = "List validation suites for a project")
    public ResponseEntity<List<ValidationSuiteDto>> listSuites(
            @RequestParam UUID projectId,
            @CurrentUser AuthUser user) {
        return ResponseEntity.ok(validationService.listSuites(projectId));
    }

    @PostMapping("/suites")
    @Operation(summary = "Create a new validation suite")
    public ResponseEntity<ValidationSuiteDto> createSuite(
            @Valid @RequestBody ValidationSuiteCreateRequest req,
            @CurrentUser AuthUser user) {
        ValidationSuiteDto created = validationService.createSuite(req, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/suites/{id}")
    @Operation(summary = "Get a validation suite by id")
    public ResponseEntity<ValidationSuiteDto> getSuite(
            @PathVariable UUID id,
            @CurrentUser AuthUser user) {
        return ResponseEntity.ok(validationService.getSuite(id, user));
    }

    @PutMapping("/suites/{id}")
    @Operation(summary = "Update a validation suite")
    public ResponseEntity<ValidationSuiteDto> updateSuite(
            @PathVariable UUID id,
            @Valid @RequestBody ValidationSuiteUpdateRequest req,
            @CurrentUser AuthUser user) {
        return ResponseEntity.ok(validationService.updateSuite(id, req, user));
    }

    @DeleteMapping("/suites/{id}")
    @Operation(summary = "Delete a validation suite")
    public ResponseEntity<Void> deleteSuite(
            @PathVariable UUID id,
            @CurrentUser AuthUser user) {
        validationService.deleteSuite(id, user);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/suites/{id}/run")
    @Operation(summary = "Execute a validation suite and persist a run")
    public ResponseEntity<ValidationRunDto> run(
            @PathVariable UUID id,
            @Valid @RequestBody ValidationRunRequest request,
            @CurrentUser AuthUser user) {
        return ResponseEntity.ok(validationService.run(id, request, user));
    }

    @PostMapping("/projects/{projectId}/validate-all")
    @Operation(summary = "Execute every suite in the project sequentially")
    public ResponseEntity<List<ValidationRunDto>> validateAll(
            @PathVariable UUID projectId,
            @Valid @RequestBody ValidationRunRequest request,
            @CurrentUser AuthUser user) {
        return ResponseEntity.ok(validationService.validateAll(projectId, request, user));
    }

    // ----- Runs & Issues --------------------------------------------------------

    @GetMapping("/runs")
    @Operation(summary = "List historical runs for a suite")
    public ResponseEntity<List<ValidationRunDto>> runs(
            @RequestParam UUID suiteId,
            @RequestParam(defaultValue = "20") int limit,
            @CurrentUser AuthUser user) {
        return ResponseEntity.ok(validationService.history(suiteId, limit));
    }

    @GetMapping("/runs/{id}")
    @Operation(summary = "Get a single run by id")
    public ResponseEntity<ValidationRunDto> getRun(
            @PathVariable UUID id,
            @CurrentUser AuthUser user) {
        return ResponseEntity.ok(validationService.getRun(id));
    }

    @GetMapping("/runs/{id}/issues")
    @Operation(summary = "List issues for a run, optionally filtered by severity")
    public ResponseEntity<List<ValidationIssueDto>> issues(
            @PathVariable UUID id,
            @RequestParam(required = false) ValidationSeverity severity,
            @RequestParam(defaultValue = "100") int limit,
            @CurrentUser AuthUser user) {
        return ResponseEntity.ok(validationService.issues(id, severity, limit));
    }
}
