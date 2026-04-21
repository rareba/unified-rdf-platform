package io.rdfforge.shacl.validation;

import io.rdfforge.common.exception.ResourceNotFoundException;
import io.rdfforge.common.exception.RdfForgeException;
import io.rdfforge.common.model.ValidationReport;
import io.rdfforge.common.security.AuthUser;
import io.rdfforge.engine.shacl.ShaclValidator;
import io.rdfforge.shacl.entity.ShapeEntity;
import io.rdfforge.shacl.repository.ShapeRepository;
import io.rdfforge.shacl.service.ProfileValidationService;
import io.rdfforge.shacl.validation.ValidationSuiteEntity.ReleaseGate;
import io.rdfforge.shacl.validation.ValidationSuiteEntity.RuleType;
import io.rdfforge.shacl.validation.ValidationSuiteEntity.SuiteRule;
import io.rdfforge.shacl.validation.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.StringWriter;
import java.time.Instant;
import java.util.*;

/**
 * Phase 5 — Validation Cockpit executor.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>CRUD on {@link ValidationSuiteEntity} with owner-or-admin authz,</li>
 *   <li>Running a suite against a target named graph — producing a
 *       {@link ValidationRunEntity} and one {@link ValidationIssueEntity}
 *       per finding,</li>
 *   <li>Serving read-side queries (history, issues, latest),</li>
 *   <li>Applying the suite's {@link ReleaseGate} to decide whether the run
 *       blocks a publish — exposed as {@link #evaluateGate(UUID)} for the
 *       future release-factory (phase 6).</li>
 * </ul>
 *
 * <p>The executor is synchronous for v1 — acceptable for small suites on
 * small graphs. Async runs + live progress push are tracked as phase 5.1.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ValidationService {

    private final ValidationSuiteRepository suiteRepository;
    private final ValidationRunRepository runRepository;
    private final ValidationIssueRepository issueRepository;
    private final ShapeRepository shapeRepository;
    private final ShaclValidator shaclValidator;
    private final ProfileValidationService profileValidationService;
    private final TargetDataResolver targetDataResolver;

    // ===== Suite CRUD ===========================================================

    @Transactional(readOnly = true)
    public List<ValidationSuiteDto> listSuites(UUID projectId) {
        if (projectId == null) {
            throw new IllegalArgumentException("projectId is required");
        }
        return suiteRepository.findByProjectIdOrderByNameAsc(projectId).stream()
            .map(ValidationSuiteDto::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public ValidationSuiteDto getSuite(UUID id, AuthUser user) {
        ValidationSuiteEntity suite = loadSuite(id);
        requireOwnerOrAdmin(suite, user, "read");
        return ValidationSuiteDto.from(suite);
    }

    @Transactional
    public ValidationSuiteDto createSuite(ValidationSuiteCreateRequest req, AuthUser user) {
        if (suiteRepository.existsByProjectIdAndName(req.projectId(), req.name())) {
            throw new RdfForgeException(
                "A validation suite named '" + req.name() + "' already exists in this project");
        }
        ValidationSuiteEntity entity = ValidationSuiteEntity.builder()
            .projectId(req.projectId())
            .name(req.name())
            .description(req.description())
            .rules(req.rules() == null ? new ArrayList<>() : new ArrayList<>(req.rules()))
            .gate(req.gate() == null ? ReleaseGate.FAIL_ON_ERROR : req.gate())
            .createdBy(user.id())
            .build();
        normaliseRuleIds(entity);
        entity = suiteRepository.save(entity);
        log.info("Created validation suite '{}' ({}) in project {}",
            entity.getName(), entity.getId(), entity.getProjectId());
        return ValidationSuiteDto.from(entity);
    }

    @Transactional
    public ValidationSuiteDto updateSuite(UUID id, ValidationSuiteUpdateRequest req, AuthUser user) {
        ValidationSuiteEntity suite = loadSuite(id);
        requireOwnerOrAdmin(suite, user, "update");
        suite.setName(req.name());
        suite.setDescription(req.description());
        suite.setRules(req.rules() == null ? new ArrayList<>() : new ArrayList<>(req.rules()));
        if (req.gate() != null) {
            suite.setGate(req.gate());
        }
        normaliseRuleIds(suite);
        suite = suiteRepository.save(suite);
        return ValidationSuiteDto.from(suite);
    }

    @Transactional
    public void deleteSuite(UUID id, AuthUser user) {
        ValidationSuiteEntity suite = loadSuite(id);
        requireOwnerOrAdmin(suite, user, "delete");
        suiteRepository.delete(suite);
    }

    // ===== Run execution ========================================================

    @Transactional
    public ValidationRunDto run(UUID suiteId, ValidationRunRequest request, AuthUser user) {
        ValidationSuiteEntity suite = loadSuite(suiteId);
        requireOwnerOrAdmin(suite, user, "run");

        long started = System.currentTimeMillis();
        ValidationRunEntity run = ValidationRunEntity.builder()
            .suiteId(suite.getId())
            .projectId(suite.getProjectId())
            .status(ValidationStatus.RUNNING)
            .ranBy(user.id())
            .context(buildRunContext(request))
            .build();
        run = runRepository.save(run);

        List<ValidationIssueEntity> issues = new ArrayList<>();
        ValidationStatus finalStatus;
        String summary;

        try {
            Model targetData = targetDataResolver.resolve(request);
            for (SuiteRule rule : suite.getRules()) {
                issues.addAll(executeRule(rule, targetData, run.getId()));
            }
            finalStatus = deriveStatusFromGate(suite.getGate(), issues);
            summary = buildSummary(suite, issues);
        } catch (Exception e) {
            log.error("Validation suite {} failed to execute", suite.getId(), e);
            finalStatus = ValidationStatus.ERRORED;
            summary = "Run failed: " + e.getMessage();
            issues.add(ValidationIssueEntity.builder()
                .runId(run.getId())
                .ruleId("__executor__")
                .severity(ValidationSeverity.FATAL)
                .message(summary)
                .build());
        }

        // Persist issues & finalise the run record.
        if (!issues.isEmpty()) {
            issueRepository.saveAll(issues);
        }
        Map<ValidationSeverity, Long> bySeverity = tallyBySeverity(issues);
        run.setStatus(finalStatus);
        run.setDurationMs(System.currentTimeMillis() - started);
        run.setIssueCount(issues.size());
        run.setErrorCount(bySeverity.getOrDefault(ValidationSeverity.ERROR, 0L).intValue());
        run.setWarningCount(bySeverity.getOrDefault(ValidationSeverity.WARNING, 0L).intValue());
        run.setInfoCount(bySeverity.getOrDefault(ValidationSeverity.INFO, 0L).intValue());
        run.setFatalCount(bySeverity.getOrDefault(ValidationSeverity.FATAL, 0L).intValue());
        run.setSummary(summary);
        run = runRepository.save(run);

        return ValidationRunDto.from(run);
    }

    /**
     * Executes every suite in the project sequentially. Returns one run DTO
     * per suite; suites that fail individually do not abort the batch.
     */
    @Transactional
    public List<ValidationRunDto> validateAll(UUID projectId, ValidationRunRequest request, AuthUser user) {
        List<ValidationSuiteEntity> suites = suiteRepository.findByProjectIdOrderByNameAsc(projectId);
        List<ValidationRunDto> runs = new ArrayList<>();
        for (ValidationSuiteEntity suite : suites) {
            try {
                runs.add(run(suite.getId(), request, user));
            } catch (AccessDeniedException e) {
                // Skip suites the user cannot access rather than aborting the batch.
                log.info("Skipping suite {} during validate-all: {}", suite.getId(), e.getMessage());
            } catch (Exception e) {
                log.error("Suite {} failed during validate-all", suite.getId(), e);
            }
        }
        return runs;
    }

    // ===== Read-side queries ====================================================

    @Transactional(readOnly = true)
    public List<ValidationRunDto> history(UUID suiteId, int limit) {
        Pageable p = PageRequest.of(0, Math.max(1, Math.min(limit, 200)));
        return runRepository.findBySuiteIdOrderByRanAtDesc(suiteId, p).stream()
            .map(ValidationRunDto::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public ValidationRunDto getRun(UUID runId) {
        ValidationRunEntity run = runRepository.findById(runId)
            .orElseThrow(() -> new ResourceNotFoundException("ValidationRun", runId.toString()));
        return ValidationRunDto.from(run);
    }

    @Transactional(readOnly = true)
    public List<ValidationIssueDto> issues(UUID runId, ValidationSeverity severity, int limit) {
        Pageable p = PageRequest.of(0, Math.max(1, Math.min(limit, 1000)));
        List<ValidationIssueEntity> rows = severity == null
            ? issueRepository.findByRunId(runId, p)
            : issueRepository.findByRunIdAndSeverity(runId, severity, p);
        return rows.stream().map(ValidationIssueDto::from).toList();
    }

    // ===== Release gate =========================================================

    /**
     * Apply the suite's gate policy to the issues of a run. Consumed by the
     * future release-factory (phase 6). Not currently exposed via HTTP.
     */
    @Transactional(readOnly = true)
    public GateResult evaluateGate(UUID runId) {
        ValidationRunEntity run = runRepository.findById(runId)
            .orElseThrow(() -> new ResourceNotFoundException("ValidationRun", runId.toString()));
        ValidationSuiteEntity suite = loadSuite(run.getSuiteId());
        ReleaseGate gate = suite.getGate() == null ? ReleaseGate.FAIL_ON_ERROR : suite.getGate();

        if (gate == ReleaseGate.DISABLED || gate == ReleaseGate.WARN_ONLY) {
            return new GateResult(true, List.of());
        }
        ValidationSeverity threshold = switch (gate) {
            case FAIL_ON_WARNING -> ValidationSeverity.WARNING;
            case FAIL_ON_ERROR   -> ValidationSeverity.ERROR;
            case FAIL_ON_FATAL   -> ValidationSeverity.FATAL;
            default              -> ValidationSeverity.ERROR;
        };
        List<ValidationIssueDto> blocking = issueRepository.findByRunId(run.getId(),
                PageRequest.of(0, 1000)).stream()
            .filter(i -> i.getSeverity() != null && i.getSeverity().atLeast(threshold))
            .map(ValidationIssueDto::from)
            .toList();
        return new GateResult(blocking.isEmpty(), blocking);
    }

    // ===== Internals ============================================================

    private ValidationSuiteEntity loadSuite(UUID id) {
        return suiteRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("ValidationSuite", id.toString()));
    }

    private static void requireOwnerOrAdmin(ValidationSuiteEntity suite, AuthUser user, String action) {
        if (user == null || user.isAnonymous()) {
            throw new AccessDeniedException("Authentication required");
        }
        if (user.isAdmin()) return;
        UUID owner = suite.getCreatedBy();
        if (owner == null || !owner.equals(user.id())) {
            throw new AccessDeniedException(
                "Not authorized to " + action + " this validation suite");
        }
    }

    private static void normaliseRuleIds(ValidationSuiteEntity suite) {
        if (suite.getRules() == null) return;
        Set<String> seen = new HashSet<>();
        for (SuiteRule rule : suite.getRules()) {
            if (rule.getId() == null || rule.getId().isBlank() || !seen.add(rule.getId())) {
                String next = "rule-" + UUID.randomUUID().toString().substring(0, 8);
                rule.setId(next);
                seen.add(next);
            }
            if (rule.getSeverity() == null) {
                rule.setSeverity(ValidationSeverity.ERROR);
            }
        }
    }

    private Map<String, Object> buildRunContext(ValidationRunRequest request) {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("triggered_by", request.triggeredBy() == null ? "manual" : request.triggeredBy());
        if (request.targetGraph() != null) ctx.put("target_graph", request.targetGraph());
        if (request.targetTriplestoreId() != null) {
            ctx.put("target_triplestore_id", request.targetTriplestoreId().toString());
        }
        return ctx;
    }

    private List<ValidationIssueEntity> executeRule(SuiteRule rule, Model targetData, UUID runId) {
        if (rule.getType() == null) {
            return List.of(issueFromExecutorError(runId, rule, "Rule has no type"));
        }
        try {
            return switch (rule.getType()) {
                case SHACL_SHAPE   -> executeShaclShape(rule, targetData, runId);
                case SPARQL_ASK    -> executeSparqlAsk(rule, targetData, runId);
                case SPARQL_SELECT -> executeSparqlSelect(rule, targetData, runId);
                case CUBE_PROFILE  -> executeCubeProfile(rule, targetData, runId);
            };
        } catch (Exception e) {
            log.warn("Rule {} ({}) threw during execution: {}", rule.getId(), rule.getType(), e.getMessage());
            return List.of(issueFromExecutorError(runId, rule,
                "Rule execution error: " + e.getMessage()));
        }
    }

    private List<ValidationIssueEntity> executeShaclShape(SuiteRule rule, Model targetData, UUID runId) {
        if (rule.getResourceRef() == null || rule.getResourceRef().isBlank()) {
            return List.of(issueFromExecutorError(runId, rule, "SHACL rule has no shape reference"));
        }
        UUID shapeId;
        try {
            shapeId = UUID.fromString(rule.getResourceRef());
        } catch (IllegalArgumentException e) {
            return List.of(issueFromExecutorError(runId, rule,
                "SHACL rule resourceRef must be a shape UUID: " + rule.getResourceRef()));
        }
        ShapeEntity shape = shapeRepository.findById(shapeId)
            .orElseThrow(() -> new ResourceNotFoundException("Shape", shapeId.toString()));

        ValidationReport report = shaclValidator.validate(targetData, shape.getContent());
        return mapShaclResults(report, rule, runId);
    }

    private List<ValidationIssueEntity> executeCubeProfile(SuiteRule rule, Model targetData, UUID runId) {
        if (rule.getResourceRef() == null || rule.getResourceRef().isBlank()) {
            return List.of(issueFromExecutorError(runId, rule, "CUBE_PROFILE rule has no profile id"));
        }
        if (!profileValidationService.isProfileAvailable(rule.getResourceRef())) {
            return List.of(issueFromExecutorError(runId, rule,
                "Unknown cube profile: " + rule.getResourceRef()));
        }
        String turtle = serialiseToTurtle(targetData);
        ValidationReport report = profileValidationService.validateAgainstProfile(
            turtle, "TURTLE", rule.getResourceRef());
        return mapShaclResults(report, rule, runId);
    }

    private List<ValidationIssueEntity> executeSparqlAsk(SuiteRule rule, Model targetData, UUID runId) {
        if (rule.getResourceRef() == null || rule.getResourceRef().isBlank()) {
            return List.of(issueFromExecutorError(runId, rule, "SPARQL_ASK rule has no query"));
        }
        Query query = QueryFactory.create(rule.getResourceRef());
        if (!query.isAskType()) {
            return List.of(issueFromExecutorError(runId, rule,
                "SPARQL_ASK rule requires an ASK query"));
        }
        try (QueryExecution qe = QueryExecutionFactory.create(query, targetData)) {
            boolean holds = qe.execAsk();
            if (holds) {
                return List.of();
            }
            return List.of(ValidationIssueEntity.builder()
                .runId(runId)
                .ruleId(rule.getId())
                .severity(rule.getSeverity() == null ? ValidationSeverity.ERROR : rule.getSeverity())
                .message(rule.getName() == null
                    ? "ASK rule returned false"
                    : "ASK rule '" + rule.getName() + "' returned false")
                .sourcePath(null)
                .details(Map.of("ruleType", "SPARQL_ASK", "ask", false))
                .build());
        }
    }

    private List<ValidationIssueEntity> executeSparqlSelect(SuiteRule rule, Model targetData, UUID runId) {
        if (rule.getResourceRef() == null || rule.getResourceRef().isBlank()) {
            return List.of(issueFromExecutorError(runId, rule, "SPARQL_SELECT rule has no query"));
        }
        Query query = QueryFactory.create(rule.getResourceRef());
        if (!query.isSelectType()) {
            return List.of(issueFromExecutorError(runId, rule,
                "SPARQL_SELECT rule requires a SELECT query"));
        }
        List<ValidationIssueEntity> issues = new ArrayList<>();
        try (QueryExecution qe = QueryExecutionFactory.create(query, targetData)) {
            ResultSet rs = qe.execSelect();
            List<String> vars = rs.getResultVars();
            while (rs.hasNext()) {
                QuerySolution sol = rs.next();
                Map<String, Object> bindings = new HashMap<>();
                String resourceUri = null;
                String sourcePath = null;
                String msg = null;
                for (String var : vars) {
                    RDFNode node = sol.get(var);
                    if (node == null) continue;
                    String asString = nodeToString(node);
                    bindings.put(var, asString);
                    if (resourceUri == null && "resource".equalsIgnoreCase(var) && node.isURIResource()) {
                        resourceUri = asString;
                    }
                    if (sourcePath == null && "path".equalsIgnoreCase(var)) {
                        sourcePath = asString;
                    }
                    if (msg == null && ("message".equalsIgnoreCase(var) || "msg".equalsIgnoreCase(var))) {
                        msg = asString;
                    }
                }
                if (resourceUri == null) {
                    // Fall back to the first URI binding we find.
                    for (String var : vars) {
                        RDFNode n = sol.get(var);
                        if (n != null && n.isURIResource()) {
                            resourceUri = n.asResource().getURI();
                            break;
                        }
                    }
                }
                issues.add(ValidationIssueEntity.builder()
                    .runId(runId)
                    .ruleId(rule.getId())
                    .severity(rule.getSeverity() == null ? ValidationSeverity.ERROR : rule.getSeverity())
                    .resourceUri(resourceUri)
                    .message(msg != null ? msg
                        : ("SELECT rule '" + Optional.ofNullable(rule.getName()).orElse(rule.getId())
                           + "' matched a row"))
                    .sourcePath(sourcePath)
                    .details(Map.of("ruleType", "SPARQL_SELECT", "bindings", bindings))
                    .build());
            }
        }
        return issues;
    }

    private List<ValidationIssueEntity> mapShaclResults(ValidationReport report, SuiteRule rule, UUID runId) {
        if (report == null || report.getResults() == null || report.getResults().isEmpty()) {
            return List.of();
        }
        List<ValidationIssueEntity> issues = new ArrayList<>();
        ValidationSeverity ruleSeverity = rule.getSeverity() == null
            ? ValidationSeverity.ERROR : rule.getSeverity();

        for (ValidationReport.ValidationResult r : report.getResults()) {
            // Use rule severity as baseline; downgrade to WARNING/INFO if SHACL itself did.
            ValidationSeverity sev = ruleSeverity;
            if (r.getSeverity() == ValidationReport.ValidationResult.Severity.WARNING
                    && ruleSeverity.atLeast(ValidationSeverity.WARNING)) {
                sev = ValidationSeverity.WARNING;
            } else if (r.getSeverity() == ValidationReport.ValidationResult.Severity.INFO) {
                sev = ValidationSeverity.INFO;
            }
            Map<String, Object> details = new HashMap<>();
            details.put("ruleType", rule.getType().name());
            if (r.getSourceConstraintComponent() != null) {
                details.put("sourceConstraintComponent", r.getSourceConstraintComponent());
            }
            if (r.getSourceShape() != null) {
                details.put("sourceShape", r.getSourceShape());
            }
            if (r.getValue() != null) {
                details.put("value", r.getValue());
            }
            issues.add(ValidationIssueEntity.builder()
                .runId(runId)
                .ruleId(rule.getId())
                .severity(sev)
                .resourceUri(r.getFocusNode())
                .message(r.getMessage() == null
                    ? "SHACL constraint violation"
                    : r.getMessage())
                .sourcePath(r.getResultPath())
                .details(details)
                .build());
        }
        return issues;
    }

    private ValidationStatus deriveStatusFromGate(ReleaseGate gate, List<ValidationIssueEntity> issues) {
        ReleaseGate effective = gate == null ? ReleaseGate.FAIL_ON_ERROR : gate;
        if (issues.isEmpty()) {
            return ValidationStatus.PASSED;
        }
        ValidationSeverity worst = issues.stream()
            .map(ValidationIssueEntity::getSeverity)
            .filter(Objects::nonNull)
            .max(Comparator.naturalOrder())
            .orElse(ValidationSeverity.INFO);

        return switch (effective) {
            case DISABLED, WARN_ONLY -> ValidationStatus.PASSED;
            case FAIL_ON_WARNING     -> worst.atLeast(ValidationSeverity.WARNING)
                                            ? ValidationStatus.FAILED : ValidationStatus.PASSED;
            case FAIL_ON_ERROR       -> worst.atLeast(ValidationSeverity.ERROR)
                                            ? ValidationStatus.FAILED : ValidationStatus.PASSED;
            case FAIL_ON_FATAL       -> worst == ValidationSeverity.FATAL
                                            ? ValidationStatus.FAILED : ValidationStatus.PASSED;
        };
    }

    private Map<ValidationSeverity, Long> tallyBySeverity(List<ValidationIssueEntity> issues) {
        Map<ValidationSeverity, Long> map = new EnumMap<>(ValidationSeverity.class);
        for (ValidationIssueEntity i : issues) {
            ValidationSeverity sev = i.getSeverity() == null ? ValidationSeverity.ERROR : i.getSeverity();
            map.merge(sev, 1L, Long::sum);
        }
        return map;
    }

    private String buildSummary(ValidationSuiteEntity suite, List<ValidationIssueEntity> issues) {
        if (issues.isEmpty()) {
            return "Suite '" + suite.getName() + "' passed (" + suite.getRules().size() + " rule(s))";
        }
        Map<ValidationSeverity, Long> counts = tallyBySeverity(issues);
        StringBuilder sb = new StringBuilder("Suite '").append(suite.getName()).append("': ");
        sb.append(issues.size()).append(" issue(s)");
        for (ValidationSeverity s : ValidationSeverity.values()) {
            Long n = counts.get(s);
            if (n != null && n > 0) {
                sb.append(" — ").append(n).append(' ').append(s.name().toLowerCase());
            }
        }
        return sb.toString();
    }

    private static ValidationIssueEntity issueFromExecutorError(UUID runId, SuiteRule rule, String message) {
        return ValidationIssueEntity.builder()
            .runId(runId)
            .ruleId(rule == null ? null : rule.getId())
            .severity(ValidationSeverity.ERROR)
            .message(message)
            .details(Map.of("executorError", true))
            .build();
    }

    private static String serialiseToTurtle(Model model) {
        StringWriter w = new StringWriter();
        model.write(w, "TURTLE");
        return w.toString();
    }

    private static String nodeToString(RDFNode node) {
        if (node == null) return null;
        if (node.isURIResource()) return node.asResource().getURI();
        if (node.isLiteral()) return node.asLiteral().getLexicalForm();
        if (node.isAnon()) return "_:" + node.asResource().getId().getLabelString();
        return node.toString();
    }
}
