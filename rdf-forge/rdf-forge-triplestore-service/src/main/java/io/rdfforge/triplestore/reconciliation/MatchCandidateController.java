package io.rdfforge.triplestore.reconciliation;

import io.rdfforge.common.security.AuthUser;
import io.rdfforge.common.security.CurrentUser;
import io.rdfforge.triplestore.reconciliation.MatchCandidateDtos.ListFilter;
import io.rdfforge.triplestore.reconciliation.MatchCandidateDtos.ManualCandidateRequest;
import io.rdfforge.triplestore.reconciliation.MatchCandidateDtos.MatchCandidateDto;
import io.rdfforge.triplestore.reconciliation.MatchCandidateDtos.MatchStatsDto;
import io.rdfforge.triplestore.reconciliation.MatchCandidateDtos.SuggestRequest;
import io.rdfforge.triplestore.reconciliation.MatchCandidateDtos.SuggestResponse;
import io.rdfforge.triplestore.reconciliation.MatchCandidateEntity.MatchPredicate;
import io.rdfforge.triplestore.reconciliation.MatchCandidateEntity.MatchStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reconciliation")
@Tag(name = "Reconciliation", description = "Link discovery / reconciliation (Phase 8)")
@CrossOrigin(origins = "${app.cors.allowed-origins:http://localhost:4200}")
public class MatchCandidateController {

    private final MatchCandidateService service;

    public MatchCandidateController(MatchCandidateService service) {
        this.service = service;
    }

    @PostMapping("/candidates/suggest")
    @Operation(summary = "Suggest matches", description = "Invoke registered matchers for a source URI and persist candidates as PENDING")
    public ResponseEntity<SuggestResponse> suggest(
            @RequestBody SuggestRequest request,
            @CurrentUser AuthUser user
    ) {
        return ResponseEntity.ok(service.suggest(request, user));
    }

    @GetMapping("/candidates")
    @Operation(summary = "List candidates")
    public ResponseEntity<List<MatchCandidateDto>> list(
            @RequestParam UUID projectId,
            @RequestParam(required = false) MatchStatus status,
            @RequestParam(required = false) MatchPredicate predicate,
            @RequestParam(required = false) String matcher,
            @RequestParam(required = false) String search,
            @CurrentUser AuthUser user
    ) {
        return ResponseEntity.ok(service.list(projectId, ListFilter.of(status, predicate, matcher, search), user));
    }

    @GetMapping("/candidates/{id}")
    @Operation(summary = "Get candidate")
    public ResponseEntity<MatchCandidateDto> get(@PathVariable UUID id, @CurrentUser AuthUser user) {
        return ResponseEntity.ok(service.get(id, user));
    }

    @PostMapping("/candidates/{id}/approve")
    @Operation(summary = "Approve candidate")
    public ResponseEntity<MatchCandidateDto> approve(@PathVariable UUID id, @CurrentUser AuthUser user) {
        return ResponseEntity.ok(service.approve(id, user));
    }

    @PostMapping("/candidates/{id}/reject")
    @Operation(summary = "Reject candidate")
    public ResponseEntity<MatchCandidateDto> reject(@PathVariable UUID id, @CurrentUser AuthUser user) {
        return ResponseEntity.ok(service.reject(id, user));
    }

    @PostMapping("/candidates/manual")
    @Operation(summary = "Create manual candidate", description = "Create a user-asserted match — goes straight to APPROVED")
    public ResponseEntity<MatchCandidateDto> manual(
            @RequestBody ManualCandidateRequest request,
            @CurrentUser AuthUser user
    ) {
        return ResponseEntity.ok(service.manual(request, user));
    }

    @GetMapping("/matchers")
    @Operation(summary = "List registered matchers")
    public ResponseEntity<List<MatcherRegistry.MatcherInfo>> matchers(@CurrentUser AuthUser user) {
        return ResponseEntity.ok(service.listMatchers(user));
    }

    @GetMapping("/stats")
    @Operation(summary = "Reconciliation stats", description = "Counts by status/predicate/matcher for a project")
    public ResponseEntity<MatchStatsDto> stats(
            @RequestParam UUID projectId,
            @CurrentUser AuthUser user
    ) {
        return ResponseEntity.ok(service.stats(projectId, user));
    }
}
