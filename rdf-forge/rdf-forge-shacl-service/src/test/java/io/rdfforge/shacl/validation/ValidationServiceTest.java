package io.rdfforge.shacl.validation;

import io.rdfforge.common.security.AuthUser;
import io.rdfforge.engine.shacl.ShaclValidatorService;
import io.rdfforge.shacl.entity.ShapeEntity;
import io.rdfforge.shacl.repository.ShapeRepository;
import io.rdfforge.shacl.service.ProfileValidationService;
import io.rdfforge.shacl.validation.ValidationSuiteEntity.ReleaseGate;
import io.rdfforge.shacl.validation.ValidationSuiteEntity.RuleType;
import io.rdfforge.shacl.validation.ValidationSuiteEntity.SuiteRule;
import io.rdfforge.shacl.validation.dto.ValidationIssueDto;
import io.rdfforge.shacl.validation.dto.ValidationRunDto;
import io.rdfforge.shacl.validation.dto.ValidationRunRequest;
import io.rdfforge.shacl.validation.dto.ValidationSuiteCreateRequest;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

import java.io.StringReader;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises the executor paths end-to-end against an in-memory Jena data
 * graph — no DB involvement. The repositories are hand-rolled stubs so the
 * test stays free of Spring/JPA boot cost.
 */
@DisplayName("ValidationService — executor + authz")
class ValidationServiceTest {

    private static final String VALID_SHAPE = """
        @prefix sh:  <http://www.w3.org/ns/shacl#> .
        @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .
        @prefix ex:  <http://example.org/> .
        ex:PersonShape a sh:NodeShape ;
            sh:targetClass ex:Person ;
            sh:property [
                sh:path ex:name ;
                sh:datatype xsd:string ;
                sh:minCount 1 ;
            ] .
        """;

    private static final String VALID_DATA = """
        @prefix ex:  <http://example.org/> .
        ex:alice a ex:Person ; ex:name "Alice" .
        """;

    private static final String VIOLATING_DATA = """
        @prefix ex:  <http://example.org/> .
        ex:bob a ex:Person .
        """;

    private InMemorySuiteRepo suiteRepo;
    private InMemoryRunRepo runRepo;
    private InMemoryIssueRepo issueRepo;
    private StubShapeRepo shapeRepo;
    private ValidationService service;

    private UUID owner;
    private UUID projectId;
    private UUID shapeId;

    @BeforeEach
    void setUp() {
        suiteRepo = new InMemorySuiteRepo();
        runRepo = new InMemoryRunRepo();
        issueRepo = new InMemoryIssueRepo();
        shapeRepo = new StubShapeRepo();

        owner = UUID.randomUUID();
        projectId = UUID.randomUUID();
        shapeId = UUID.randomUUID();
        shapeRepo.put(ShapeEntity.builder()
            .id(shapeId)
            .projectId(projectId)
            .name("Person")
            .contentFormat("TURTLE")
            .content(VALID_SHAPE)
            .createdBy(owner)
            .build());

        ShaclValidatorService shaclValidator = new ShaclValidatorService(new SimpleMeterRegistry());
        ProfileValidationService profileSvc = new ProfileValidationService(shaclValidator);
        // Do NOT invoke @PostConstruct loadProfiles — tests don't need cube profiles.

        service = new ValidationService(
            suiteRepo,
            runRepo,
            issueRepo,
            shapeRepo,
            shaclValidator,
            profileSvc,
            new StubTargetDataResolver()
        );
    }

    private AuthUser ownerUser() {
        return new AuthUser(owner, "owner@example.com", Set.of("USER"));
    }

    private AuthUser adminUser() {
        return new AuthUser(UUID.randomUUID(), "admin@example.com", Set.of("ADMIN"));
    }

    private AuthUser otherUser() {
        return new AuthUser(UUID.randomUUID(), "other@example.com", Set.of("USER"));
    }

    // ValidationRunRequest is a record, so we cannot subclass. We push the
    // payload for the next run into this thread-local which the stub resolver
    // reads. Each test calls dataToReturn(...) before calling run().
    private static final ThreadLocal<String> NEXT_DATA = ThreadLocal.withInitial(() -> VALID_DATA);

    private class StubTargetDataResolver implements TargetDataResolver {
        @Override public Model resolve(ValidationRunRequest request) {
            Model m = ModelFactory.createDefaultModel();
            m.read(new StringReader(NEXT_DATA.get()), null, "TURTLE");
            return m;
        }
    }

    private void dataToReturn(String turtle) {
        NEXT_DATA.set(turtle);
    }

    private ValidationSuiteEntity persistSuite(List<SuiteRule> rules) {
        ValidationSuiteCreateRequest req = new ValidationSuiteCreateRequest(
            projectId, "Suite-" + UUID.randomUUID().toString().substring(0, 6),
            "test suite", rules, ReleaseGate.FAIL_ON_ERROR);
        service.createSuite(req, ownerUser());
        return suiteRepo.findByProjectIdOrderByNameAsc(projectId).get(
            suiteRepo.findByProjectIdOrderByNameAsc(projectId).size() - 1);
    }

    // ===== Tests ===============================================================

    @Test
    @DisplayName("SHACL rule against valid data — run passes with 0 issues")
    void shaclValid() {
        SuiteRule rule = SuiteRule.builder()
            .id("r1").name("person-has-name").type(RuleType.SHACL_SHAPE)
            .resourceRef(shapeId.toString())
            .severity(ValidationSeverity.ERROR).build();
        ValidationSuiteEntity suite = persistSuite(List.of(rule));
        dataToReturn(VALID_DATA);

        ValidationRunDto dto = service.run(suite.getId(),
            new ValidationRunRequest("urn:test:graph", UUID.randomUUID(), "manual"),
            ownerUser());

        assertEquals(ValidationStatus.PASSED, dto.status());
        assertEquals(0, dto.issueCount());
    }

    @Test
    @DisplayName("SHACL rule against violating data — run fails with expected issue")
    void shaclViolation() {
        SuiteRule rule = SuiteRule.builder()
            .id("r1").name("person-has-name").type(RuleType.SHACL_SHAPE)
            .resourceRef(shapeId.toString())
            .severity(ValidationSeverity.ERROR).build();
        ValidationSuiteEntity suite = persistSuite(List.of(rule));
        dataToReturn(VIOLATING_DATA);

        ValidationRunDto dto = service.run(suite.getId(),
            new ValidationRunRequest("urn:test:graph", UUID.randomUUID(), "manual"),
            ownerUser());

        assertEquals(ValidationStatus.FAILED, dto.status());
        assertTrue(dto.issueCount() >= 1, "expected at least one issue");
        assertTrue(dto.errorCount() >= 1, "expected ERROR severity");

        List<ValidationIssueDto> issues = service.issues(dto.id(), null, 100);
        assertFalse(issues.isEmpty());
        assertEquals(ValidationSeverity.ERROR, issues.get(0).severity());
    }

    @Test
    @DisplayName("SPARQL_ASK returning false produces a single issue")
    void sparqlAskFalse() {
        String ask = "PREFIX ex: <http://example.org/> ASK { ex:nobody a ex:Person }";
        SuiteRule rule = SuiteRule.builder()
            .id("r-ask").name("expect-nobody").type(RuleType.SPARQL_ASK)
            .resourceRef(ask)
            .severity(ValidationSeverity.WARNING).build();
        ValidationSuiteEntity suite = persistSuite(List.of(rule));
        dataToReturn(VALID_DATA);

        ValidationRunDto dto = service.run(suite.getId(),
            new ValidationRunRequest("urn:test:graph", UUID.randomUUID(), "manual"),
            ownerUser());

        assertEquals(1, dto.issueCount());
        assertEquals(1, dto.warningCount());
        assertEquals(ValidationStatus.PASSED, dto.status(),
            "warnings do not breach FAIL_ON_ERROR gate");
    }

    @Test
    @DisplayName("SPARQL_SELECT returning 3 rows produces 3 issues")
    void sparqlSelectMultipleRows() {
        // 3 Person instances in data → 3 rows → 3 issues
        String data = """
            @prefix ex: <http://example.org/> .
            ex:a a ex:Person ; ex:name "A" .
            ex:b a ex:Person ; ex:name "B" .
            ex:c a ex:Person ; ex:name "C" .
            """;
        String select = "PREFIX ex: <http://example.org/> "
            + "SELECT ?resource WHERE { ?resource a ex:Person }";
        SuiteRule rule = SuiteRule.builder()
            .id("r-select").name("persons").type(RuleType.SPARQL_SELECT)
            .resourceRef(select)
            .severity(ValidationSeverity.INFO).build();
        ValidationSuiteEntity suite = persistSuite(List.of(rule));
        dataToReturn(data);

        ValidationRunDto dto = service.run(suite.getId(),
            new ValidationRunRequest("urn:test:graph", UUID.randomUUID(), "manual"),
            ownerUser());

        assertEquals(3, dto.issueCount());
        assertEquals(3, dto.infoCount());
    }

    @Test
    @DisplayName("Non-owner cannot run someone else's suite")
    void authzDeny() {
        SuiteRule rule = SuiteRule.builder()
            .id("r1").name("person-has-name").type(RuleType.SHACL_SHAPE)
            .resourceRef(shapeId.toString())
            .severity(ValidationSeverity.ERROR).build();
        ValidationSuiteEntity suite = persistSuite(List.of(rule));
        dataToReturn(VALID_DATA);

        assertThrows(AccessDeniedException.class, () ->
            service.run(suite.getId(),
                new ValidationRunRequest("urn:test:graph", UUID.randomUUID(), "manual"),
                otherUser()));
    }

    @Test
    @DisplayName("Admin can run any suite")
    void authzAdmin() {
        SuiteRule rule = SuiteRule.builder()
            .id("r1").name("person-has-name").type(RuleType.SHACL_SHAPE)
            .resourceRef(shapeId.toString())
            .severity(ValidationSeverity.ERROR).build();
        ValidationSuiteEntity suite = persistSuite(List.of(rule));
        dataToReturn(VALID_DATA);

        ValidationRunDto dto = service.run(suite.getId(),
            new ValidationRunRequest("urn:test:graph", UUID.randomUUID(), "manual"),
            adminUser());
        assertEquals(ValidationStatus.PASSED, dto.status());
    }

    @Test
    @DisplayName("evaluateGate returns blocked issues when threshold breached")
    void gateBlocks() {
        SuiteRule rule = SuiteRule.builder()
            .id("r1").name("person-has-name").type(RuleType.SHACL_SHAPE)
            .resourceRef(shapeId.toString())
            .severity(ValidationSeverity.ERROR).build();
        ValidationSuiteEntity suite = persistSuite(List.of(rule));
        dataToReturn(VIOLATING_DATA);

        ValidationRunDto run = service.run(suite.getId(),
            new ValidationRunRequest("urn:test:graph", UUID.randomUUID(), "manual"),
            ownerUser());
        var gate = service.evaluateGate(run.id());
        assertFalse(gate.passed());
        assertFalse(gate.blockedBy().isEmpty());
    }

    // ===== In-memory repository stubs ==========================================

    private static class InMemorySuiteRepo implements ValidationSuiteRepository {
        private final Map<UUID, ValidationSuiteEntity> store = new LinkedHashMap<>();
        @Override public List<ValidationSuiteEntity> findByProjectIdOrderByNameAsc(UUID projectId) {
            return store.values().stream()
                .filter(s -> s.getProjectId().equals(projectId))
                .sorted(Comparator.comparing(ValidationSuiteEntity::getName))
                .toList();
        }
        @Override public Optional<ValidationSuiteEntity> findByProjectIdAndName(UUID projectId, String name) {
            return store.values().stream()
                .filter(s -> s.getProjectId().equals(projectId) && s.getName().equals(name))
                .findFirst();
        }
        @Override public boolean existsByProjectIdAndName(UUID projectId, String name) {
            return findByProjectIdAndName(projectId, name).isPresent();
        }
        @Override public <S extends ValidationSuiteEntity> S save(S e) {
            if (e.getId() == null) e.setId(UUID.randomUUID());
            if (e.getCreatedAt() == null) e.setCreatedAt(java.time.Instant.now());
            e.setUpdatedAt(java.time.Instant.now());
            store.put(e.getId(), e);
            return e;
        }
        @Override public Optional<ValidationSuiteEntity> findById(UUID id) {
            return Optional.ofNullable(store.get(id));
        }
        @Override public boolean existsById(UUID id) { return store.containsKey(id); }
        @Override public List<ValidationSuiteEntity> findAll() { return new ArrayList<>(store.values()); }
        @Override public long count() { return store.size(); }
        @Override public void deleteById(UUID id) { store.remove(id); }
        @Override public void delete(ValidationSuiteEntity e) { if (e.getId() != null) store.remove(e.getId()); }
        @Override public void deleteAllById(Iterable<? extends UUID> ids) { ids.forEach(store::remove); }
        @Override public void deleteAll(Iterable<? extends ValidationSuiteEntity> es) {
            es.forEach(e -> store.remove(e.getId()));
        }
        @Override public void deleteAll() { store.clear(); }
        @Override public <S extends ValidationSuiteEntity> List<S> saveAll(Iterable<S> es) {
            List<S> out = new ArrayList<>();
            es.forEach(e -> out.add(save(e)));
            return out;
        }
        @Override public List<ValidationSuiteEntity> findAllById(Iterable<UUID> ids) {
            List<ValidationSuiteEntity> out = new ArrayList<>();
            ids.forEach(id -> findById(id).ifPresent(out::add));
            return out;
        }
        @Override public void flush() {}
        @Override public <S extends ValidationSuiteEntity> S saveAndFlush(S entity) { return save(entity); }
        @Override public <S extends ValidationSuiteEntity> List<S> saveAllAndFlush(Iterable<S> entities) { return saveAll(entities); }
        @Override public void deleteAllInBatch(Iterable<ValidationSuiteEntity> entities) { deleteAll(entities); }
        @Override public void deleteAllByIdInBatch(Iterable<UUID> ids) { deleteAllById(ids); }
        @Override public void deleteAllInBatch() { deleteAll(); }
        @Override public ValidationSuiteEntity getOne(UUID id) { return findById(id).orElse(null); }
        @Override public ValidationSuiteEntity getById(UUID id) { return findById(id).orElse(null); }
        @Override public ValidationSuiteEntity getReferenceById(UUID id) { return findById(id).orElse(null); }
        @Override public <S extends ValidationSuiteEntity> List<S> findAll(org.springframework.data.domain.Example<S> example) { return List.of(); }
        @Override public <S extends ValidationSuiteEntity> List<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Sort sort) { return List.of(); }
        @Override public List<ValidationSuiteEntity> findAll(org.springframework.data.domain.Sort sort) { return findAll(); }
        @Override public org.springframework.data.domain.Page<ValidationSuiteEntity> findAll(Pageable pageable) { return org.springframework.data.domain.Page.empty(); }
        @Override public <S extends ValidationSuiteEntity> Optional<S> findOne(org.springframework.data.domain.Example<S> example) { return Optional.empty(); }
        @Override public <S extends ValidationSuiteEntity> org.springframework.data.domain.Page<S> findAll(org.springframework.data.domain.Example<S> example, Pageable pageable) { return org.springframework.data.domain.Page.empty(); }
        @Override public <S extends ValidationSuiteEntity> long count(org.springframework.data.domain.Example<S> example) { return 0; }
        @Override public <S extends ValidationSuiteEntity> boolean exists(org.springframework.data.domain.Example<S> example) { return false; }
        @Override public <S extends ValidationSuiteEntity, R> R findBy(org.springframework.data.domain.Example<S> example, java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { return null; }
    }

    private static class InMemoryRunRepo implements ValidationRunRepository {
        private final Map<UUID, ValidationRunEntity> store = new LinkedHashMap<>();
        @Override public List<ValidationRunEntity> findBySuiteIdOrderByRanAtDesc(UUID suiteId, Pageable p) {
            return store.values().stream()
                .filter(r -> r.getSuiteId().equals(suiteId))
                .sorted(Comparator.comparing(ValidationRunEntity::getRanAt).reversed())
                .limit(p.getPageSize())
                .toList();
        }
        @Override public List<ValidationRunEntity> findByProjectIdOrderByRanAtDesc(UUID projectId, Pageable p) {
            return store.values().stream()
                .filter(r -> r.getProjectId().equals(projectId))
                .sorted(Comparator.comparing(ValidationRunEntity::getRanAt).reversed())
                .limit(p.getPageSize())
                .toList();
        }
        @Override public Optional<ValidationRunEntity> findTop1BySuiteIdOrderByRanAtDesc(UUID suiteId) {
            return findBySuiteIdOrderByRanAtDesc(suiteId, org.springframework.data.domain.PageRequest.of(0, 1))
                .stream().findFirst();
        }
        @Override public <S extends ValidationRunEntity> S save(S e) {
            if (e.getId() == null) e.setId(UUID.randomUUID());
            if (e.getRanAt() == null) e.setRanAt(java.time.Instant.now());
            store.put(e.getId(), e);
            return e;
        }
        @Override public Optional<ValidationRunEntity> findById(UUID id) { return Optional.ofNullable(store.get(id)); }
        @Override public boolean existsById(UUID id) { return store.containsKey(id); }
        @Override public List<ValidationRunEntity> findAll() { return new ArrayList<>(store.values()); }
        @Override public long count() { return store.size(); }
        @Override public void deleteById(UUID id) { store.remove(id); }
        @Override public void delete(ValidationRunEntity e) { if (e.getId() != null) store.remove(e.getId()); }
        @Override public void deleteAllById(Iterable<? extends UUID> ids) { ids.forEach(store::remove); }
        @Override public void deleteAll(Iterable<? extends ValidationRunEntity> es) { es.forEach(e -> store.remove(e.getId())); }
        @Override public void deleteAll() { store.clear(); }
        @Override public <S extends ValidationRunEntity> List<S> saveAll(Iterable<S> es) {
            List<S> out = new ArrayList<>();
            es.forEach(e -> out.add(save(e)));
            return out;
        }
        @Override public List<ValidationRunEntity> findAllById(Iterable<UUID> ids) {
            List<ValidationRunEntity> out = new ArrayList<>();
            ids.forEach(id -> findById(id).ifPresent(out::add));
            return out;
        }
        @Override public void flush() {}
        @Override public <S extends ValidationRunEntity> S saveAndFlush(S entity) { return save(entity); }
        @Override public <S extends ValidationRunEntity> List<S> saveAllAndFlush(Iterable<S> entities) { return saveAll(entities); }
        @Override public void deleteAllInBatch(Iterable<ValidationRunEntity> entities) { deleteAll(entities); }
        @Override public void deleteAllByIdInBatch(Iterable<UUID> ids) { deleteAllById(ids); }
        @Override public void deleteAllInBatch() { deleteAll(); }
        @Override public ValidationRunEntity getOne(UUID id) { return findById(id).orElse(null); }
        @Override public ValidationRunEntity getById(UUID id) { return findById(id).orElse(null); }
        @Override public ValidationRunEntity getReferenceById(UUID id) { return findById(id).orElse(null); }
        @Override public <S extends ValidationRunEntity> List<S> findAll(org.springframework.data.domain.Example<S> example) { return List.of(); }
        @Override public <S extends ValidationRunEntity> List<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Sort sort) { return List.of(); }
        @Override public List<ValidationRunEntity> findAll(org.springframework.data.domain.Sort sort) { return findAll(); }
        @Override public org.springframework.data.domain.Page<ValidationRunEntity> findAll(Pageable pageable) { return org.springframework.data.domain.Page.empty(); }
        @Override public <S extends ValidationRunEntity> Optional<S> findOne(org.springframework.data.domain.Example<S> example) { return Optional.empty(); }
        @Override public <S extends ValidationRunEntity> org.springframework.data.domain.Page<S> findAll(org.springframework.data.domain.Example<S> example, Pageable pageable) { return org.springframework.data.domain.Page.empty(); }
        @Override public <S extends ValidationRunEntity> long count(org.springframework.data.domain.Example<S> example) { return 0; }
        @Override public <S extends ValidationRunEntity> boolean exists(org.springframework.data.domain.Example<S> example) { return false; }
        @Override public <S extends ValidationRunEntity, R> R findBy(org.springframework.data.domain.Example<S> example, java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { return null; }
    }

    private static class InMemoryIssueRepo implements ValidationIssueRepository {
        private final Map<UUID, ValidationIssueEntity> store = new LinkedHashMap<>();
        @Override public List<ValidationIssueEntity> findByRunId(UUID runId, Pageable p) {
            return store.values().stream()
                .filter(i -> i.getRunId().equals(runId))
                .limit(p.getPageSize())
                .toList();
        }
        @Override public List<ValidationIssueEntity> findByRunIdAndSeverity(UUID runId, ValidationSeverity severity, Pageable p) {
            return store.values().stream()
                .filter(i -> i.getRunId().equals(runId) && i.getSeverity() == severity)
                .limit(p.getPageSize())
                .toList();
        }
        @Override public void deleteByRunId(UUID runId) {
            store.values().removeIf(i -> i.getRunId().equals(runId));
        }
        @Override public <S extends ValidationIssueEntity> S save(S e) {
            if (e.getId() == null) e.setId(UUID.randomUUID());
            store.put(e.getId(), e);
            return e;
        }
        @Override public Optional<ValidationIssueEntity> findById(UUID id) { return Optional.ofNullable(store.get(id)); }
        @Override public boolean existsById(UUID id) { return store.containsKey(id); }
        @Override public List<ValidationIssueEntity> findAll() { return new ArrayList<>(store.values()); }
        @Override public long count() { return store.size(); }
        @Override public void deleteById(UUID id) { store.remove(id); }
        @Override public void delete(ValidationIssueEntity e) { if (e.getId() != null) store.remove(e.getId()); }
        @Override public void deleteAllById(Iterable<? extends UUID> ids) { ids.forEach(store::remove); }
        @Override public void deleteAll(Iterable<? extends ValidationIssueEntity> es) { es.forEach(e -> store.remove(e.getId())); }
        @Override public void deleteAll() { store.clear(); }
        @Override public <S extends ValidationIssueEntity> List<S> saveAll(Iterable<S> es) {
            List<S> out = new ArrayList<>();
            es.forEach(e -> out.add(save(e)));
            return out;
        }
        @Override public List<ValidationIssueEntity> findAllById(Iterable<UUID> ids) {
            List<ValidationIssueEntity> out = new ArrayList<>();
            ids.forEach(id -> findById(id).ifPresent(out::add));
            return out;
        }
        @Override public void flush() {}
        @Override public <S extends ValidationIssueEntity> S saveAndFlush(S entity) { return save(entity); }
        @Override public <S extends ValidationIssueEntity> List<S> saveAllAndFlush(Iterable<S> entities) { return saveAll(entities); }
        @Override public void deleteAllInBatch(Iterable<ValidationIssueEntity> entities) { deleteAll(entities); }
        @Override public void deleteAllByIdInBatch(Iterable<UUID> ids) { deleteAllById(ids); }
        @Override public void deleteAllInBatch() { deleteAll(); }
        @Override public ValidationIssueEntity getOne(UUID id) { return findById(id).orElse(null); }
        @Override public ValidationIssueEntity getById(UUID id) { return findById(id).orElse(null); }
        @Override public ValidationIssueEntity getReferenceById(UUID id) { return findById(id).orElse(null); }
        @Override public <S extends ValidationIssueEntity> List<S> findAll(org.springframework.data.domain.Example<S> example) { return List.of(); }
        @Override public <S extends ValidationIssueEntity> List<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Sort sort) { return List.of(); }
        @Override public List<ValidationIssueEntity> findAll(org.springframework.data.domain.Sort sort) { return findAll(); }
        @Override public org.springframework.data.domain.Page<ValidationIssueEntity> findAll(Pageable pageable) { return org.springframework.data.domain.Page.empty(); }
        @Override public <S extends ValidationIssueEntity> Optional<S> findOne(org.springframework.data.domain.Example<S> example) { return Optional.empty(); }
        @Override public <S extends ValidationIssueEntity> org.springframework.data.domain.Page<S> findAll(org.springframework.data.domain.Example<S> example, Pageable pageable) { return org.springframework.data.domain.Page.empty(); }
        @Override public <S extends ValidationIssueEntity> long count(org.springframework.data.domain.Example<S> example) { return 0; }
        @Override public <S extends ValidationIssueEntity> boolean exists(org.springframework.data.domain.Example<S> example) { return false; }
        @Override public <S extends ValidationIssueEntity, R> R findBy(org.springframework.data.domain.Example<S> example, java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { return null; }
    }

    private static class StubShapeRepo implements ShapeRepository {
        private final Map<UUID, ShapeEntity> store = new LinkedHashMap<>();
        void put(ShapeEntity e) { store.put(e.getId(), e); }
        @Override public org.springframework.data.domain.Page<ShapeEntity> findByProjectId(UUID projectId, Pageable p) { return org.springframework.data.domain.Page.empty(); }
        @Override public org.springframework.data.domain.Page<ShapeEntity> findAllByOptionalProjectId(UUID projectId, Pageable p) { return org.springframework.data.domain.Page.empty(); }
        @Override public List<ShapeEntity> findByIsTemplateTrue() { return List.of(); }
        @Override public Optional<ShapeEntity> findByProjectIdAndUri(UUID projectId, String uri) { return Optional.empty(); }
        @Override public List<ShapeEntity> findByCategory(String category) { return List.of(); }
        @Override public org.springframework.data.domain.Page<ShapeEntity> searchByOptionalProjectId(UUID projectId, String search, Pageable p) { return org.springframework.data.domain.Page.empty(); }
        @Override public List<String> findCategoriesByOptionalProjectId(UUID projectId) { return List.of(); }
        @Override public <S extends ShapeEntity> S save(S e) { store.put(e.getId(), e); return e; }
        @Override public Optional<ShapeEntity> findById(UUID id) { return Optional.ofNullable(store.get(id)); }
        @Override public boolean existsById(UUID id) { return store.containsKey(id); }
        @Override public List<ShapeEntity> findAll() { return new ArrayList<>(store.values()); }
        @Override public long count() { return store.size(); }
        @Override public void deleteById(UUID id) { store.remove(id); }
        @Override public void delete(ShapeEntity e) { if (e.getId() != null) store.remove(e.getId()); }
        @Override public void deleteAllById(Iterable<? extends UUID> ids) { ids.forEach(store::remove); }
        @Override public void deleteAll(Iterable<? extends ShapeEntity> es) { es.forEach(e -> store.remove(e.getId())); }
        @Override public void deleteAll() { store.clear(); }
        @Override public <S extends ShapeEntity> List<S> saveAll(Iterable<S> es) { List<S> out = new ArrayList<>(); es.forEach(e -> out.add(save(e))); return out; }
        @Override public List<ShapeEntity> findAllById(Iterable<UUID> ids) { List<ShapeEntity> out = new ArrayList<>(); ids.forEach(id -> findById(id).ifPresent(out::add)); return out; }
        @Override public void flush() {}
        @Override public <S extends ShapeEntity> S saveAndFlush(S entity) { return save(entity); }
        @Override public <S extends ShapeEntity> List<S> saveAllAndFlush(Iterable<S> entities) { return saveAll(entities); }
        @Override public void deleteAllInBatch(Iterable<ShapeEntity> entities) { deleteAll(entities); }
        @Override public void deleteAllByIdInBatch(Iterable<UUID> ids) { deleteAllById(ids); }
        @Override public void deleteAllInBatch() { deleteAll(); }
        @Override public ShapeEntity getOne(UUID id) { return findById(id).orElse(null); }
        @Override public ShapeEntity getById(UUID id) { return findById(id).orElse(null); }
        @Override public ShapeEntity getReferenceById(UUID id) { return findById(id).orElse(null); }
        @Override public <S extends ShapeEntity> List<S> findAll(org.springframework.data.domain.Example<S> example) { return List.of(); }
        @Override public <S extends ShapeEntity> List<S> findAll(org.springframework.data.domain.Example<S> example, org.springframework.data.domain.Sort sort) { return List.of(); }
        @Override public List<ShapeEntity> findAll(org.springframework.data.domain.Sort sort) { return findAll(); }
        @Override public org.springframework.data.domain.Page<ShapeEntity> findAll(Pageable pageable) { return org.springframework.data.domain.Page.empty(); }
        @Override public <S extends ShapeEntity> Optional<S> findOne(org.springframework.data.domain.Example<S> example) { return Optional.empty(); }
        @Override public <S extends ShapeEntity> org.springframework.data.domain.Page<S> findAll(org.springframework.data.domain.Example<S> example, Pageable pageable) { return org.springframework.data.domain.Page.empty(); }
        @Override public <S extends ShapeEntity> long count(org.springframework.data.domain.Example<S> example) { return 0; }
        @Override public <S extends ShapeEntity> boolean exists(org.springframework.data.domain.Example<S> example) { return false; }
        @Override public <S extends ShapeEntity, R> R findBy(org.springframework.data.domain.Example<S> example, java.util.function.Function<org.springframework.data.repository.query.FluentQuery.FetchableFluentQuery<S>, R> queryFunction) { return null; }
    }
}
