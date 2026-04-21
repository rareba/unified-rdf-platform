package io.rdfforge.pipeline.service;

import io.rdfforge.common.exception.PipelineValidationException;
import io.rdfforge.common.exception.ResourceNotFoundException;
import io.rdfforge.common.security.AuthUser;
import io.rdfforge.pipeline.client.AuthenticatedShaclClient;
import io.rdfforge.pipeline.client.OntologySummary;
import io.rdfforge.pipeline.client.ShaclClientException;
import io.rdfforge.pipeline.client.ShapeSummary;
import io.rdfforge.pipeline.client.ValidationIssueSummary;
import io.rdfforge.pipeline.client.ValidationRunSummary;
import io.rdfforge.pipeline.dto.ReleaseBuildResponse;
import io.rdfforge.pipeline.dto.ReleaseCreateRequest;
import io.rdfforge.pipeline.dto.ReleaseDto;
import io.rdfforge.pipeline.entity.MappingEntity;
import io.rdfforge.pipeline.entity.MappingType;
import io.rdfforge.pipeline.entity.ProjectEntity;
import io.rdfforge.pipeline.entity.ProjectStatus;
import io.rdfforge.pipeline.entity.ReleaseEntity;
import io.rdfforge.pipeline.entity.ReleaseStatus;
import io.rdfforge.pipeline.entity.SourceType;
import io.rdfforge.pipeline.repository.MappingRepository;
import io.rdfforge.pipeline.repository.ProjectRepository;
import io.rdfforge.pipeline.repository.ReleaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ReleaseService}. Covers:
 *  - draft creation (authz + SemVer + duplicate version rejection)
 *  - build assembly produces a valid non-empty zip on disk
 *  - downstream asset fetch behavior: success, 404, 403, 5xx → FAILED
 *    with concise failureReason + artifact cleanup
 *  - identity-header propagation to shacl-service
 *  - archive lifecycle
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReleaseService Tests")
class ReleaseServiceTest {

    @Mock private ReleaseRepository releaseRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private MappingRepository mappingRepository;
    @Mock private AuthenticatedShaclClient shaclClient;

    private ReleaseService service;

    @TempDir Path tmpDir;

    private UUID ownerId;
    private UUID otherUserId;
    private UUID projectId;
    private UUID releaseId;
    private AuthUser owner;
    private AuthUser other;
    private AuthUser admin;

    @BeforeEach
    void setUp() {
        service = new ReleaseService(releaseRepository, projectRepository, mappingRepository, shaclClient);
        ReflectionTestUtils.setField(service, "artifactDir", tmpDir.toString());
        service.ensureArtifactDir();

        ownerId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        releaseId = UUID.randomUUID();

        owner = new AuthUser(ownerId, "o@x.org", Set.of("USER"));
        other = new AuthUser(otherUserId, "b@x.org", Set.of("USER"));
        admin = new AuthUser(UUID.randomUUID(), "a@x.org", Set.of("ADMIN"));
    }

    private ProjectEntity sampleProject() {
        return ProjectEntity.builder()
            .id(projectId).name("P").baseUri("https://ex.org/")
            .status(ProjectStatus.ACTIVE)
            .createdBy(ownerId).createdAt(Instant.now()).updatedAt(Instant.now())
            .build();
    }

    private MappingEntity sampleMapping(UUID id, String name) {
        return MappingEntity.builder()
            .id(id).projectId(projectId).name(name).sourceType(SourceType.CSV)
            .mappingType(MappingType.GENERIC)
            .rules(new ArrayList<>())
            .version(1)
            .createdBy(ownerId)
            .createdAt(Instant.now()).updatedAt(Instant.now())
            .build();
    }

    private ReleaseCreateRequest sampleReq(String version, List<UUID> mappingIds) {
        return new ReleaseCreateRequest(
            version, "Rel " + version, "notes body",
            new ReleaseCreateRequest.ManifestRefs(
                List.of(), mappingIds == null ? List.of() : mappingIds,
                List.of(), List.of(), null, List.of())
        );
    }

    /**
     * Assemble a BUILDING-ready draft entity with a refs manifest that the
     * test can tailor by passing in ontology/shape/validation-suite id lists.
     */
    private ReleaseEntity buildDraftWithRefs(List<UUID> mappingIds,
                                             List<UUID> ontologyIds,
                                             List<UUID> shapeIds,
                                             List<UUID> validationSuiteIds) {
        ReleaseEntity draft = ReleaseEntity.builder()
            .id(releaseId)
            .projectId(projectId)
            .version("1.0.0")
            .name("rel")
            .status(ReleaseStatus.DRAFT)
            .createdBy(ownerId)
            .createdAt(Instant.now()).updatedAt(Instant.now())
            .artifactSizeBytes(0L)
            .build();
        Map<String, Object> refs = new HashMap<>();
        refs.put("mappings", mappingIds.stream().map(UUID::toString).toList());
        refs.put("ontologies", ontologyIds.stream().map(UUID::toString).toList());
        refs.put("shapes", shapeIds.stream().map(UUID::toString).toList());
        refs.put("validationSuiteIds", validationSuiteIds.stream().map(UUID::toString).toList());
        refs.put("dataSources", List.of());
        Map<String, Object> manifest = new HashMap<>();
        manifest.put("refs", refs);
        draft.setManifest(manifest);
        return draft;
    }

    private String readZipEntryAsString(Path zipPath, String entryName) throws IOException {
        try (ZipFile zip = new ZipFile(zipPath.toFile())) {
            ZipEntry e = zip.getEntry(entryName);
            if (e == null) return null;
            try (InputStream is = zip.getInputStream(e)) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
    }

    private List<String> listZipEntryNames(Path zipPath) throws IOException {
        List<String> names = new ArrayList<>();
        try (ZipFile zip = new ZipFile(zipPath.toFile())) {
            var iter = zip.entries();
            while (iter.hasMoreElements()) names.add(iter.nextElement().getName());
        }
        return names;
    }

    // ─────────────────── create ───────────────────

    @Test
    @DisplayName("create valid draft stamps createdBy from user, persists DRAFT")
    void create_valid_persistsDraft() {
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(sampleProject()));
        when(releaseRepository.existsByProjectIdAndVersion(projectId, "1.0.0")).thenReturn(false);
        when(releaseRepository.save(any(ReleaseEntity.class))).thenAnswer(inv -> {
            ReleaseEntity e = inv.getArgument(0);
            e.setId(releaseId);
            if (e.getCreatedAt() == null) e.setCreatedAt(java.time.Instant.now());
            if (e.getUpdatedAt() == null) e.setUpdatedAt(java.time.Instant.now());
            return e;
        });

        ReleaseDto dto = service.create(projectId, sampleReq("1.0.0", null), owner);
        assertEquals(ReleaseStatus.DRAFT, dto.status());
        assertEquals("1.0.0", dto.version());
        assertEquals(ownerId, dto.createdBy(),
            "createdBy must come from AuthUser, not request body");

        ArgumentCaptor<ReleaseEntity> captor = ArgumentCaptor.forClass(ReleaseEntity.class);
        verify(releaseRepository).save(captor.capture());
        assertEquals(ownerId, captor.getValue().getCreatedBy());
    }

    @Test
    @DisplayName("create rejects duplicate (projectId, version)")
    void create_duplicateVersion_rejected() {
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(sampleProject()));
        when(releaseRepository.existsByProjectIdAndVersion(projectId, "1.0.0")).thenReturn(true);

        assertThrows(PipelineValidationException.class,
            () -> service.create(projectId, sampleReq("1.0.0", null), owner));
        verify(releaseRepository, never()).save(any());
    }

    @Test
    @DisplayName("create rejects non-SemVer version")
    void create_invalidVersion_rejected() {
        assertThrows(PipelineValidationException.class,
            () -> service.create(projectId, sampleReq("not-a-semver", null), owner));
        verify(projectRepository, never()).findById(any());
    }

    @Test
    @DisplayName("create denies non-owner")
    void create_nonOwner_denied() {
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(sampleProject()));
        assertThrows(AccessDeniedException.class,
            () -> service.create(projectId, sampleReq("1.0.0", null), other));
    }

    @Test
    @DisplayName("create denies anonymous")
    void create_anonymous_denied() {
        assertThrows(AccessDeniedException.class,
            () -> service.create(projectId, sampleReq("1.0.0", null), AuthUser.anonymous()));
    }

    @Test
    @DisplayName("create with unknown projectId → 404")
    void create_unknownProject_notFound() {
        when(projectRepository.findById(projectId)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
            () -> service.create(projectId, sampleReq("1.0.0", null), owner));
    }

    // ─────────────────── build (pre-existing happy path) ───────────────────

    @Test
    @DisplayName("build with only mappings produces a valid non-empty zip")
    void build_producesZip() throws IOException {
        ProjectEntity project = sampleProject();
        UUID mappingId = UUID.randomUUID();
        MappingEntity mapping = sampleMapping(mappingId, "my-mapping");

        ReleaseEntity draft = buildDraftWithRefs(
            List.of(mappingId), List.of(), List.of(), List.of());

        when(releaseRepository.findById(releaseId)).thenReturn(Optional.of(draft));
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(mappingRepository.findById(mappingId)).thenReturn(Optional.of(mapping));
        when(releaseRepository.save(any(ReleaseEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ReleaseBuildResponse resp = service.build(releaseId, owner);

        assertNotNull(resp.artifactUri(), "build must set artifactUri on success");
        assertTrue(resp.artifactSizeBytes() > 0, "artifact size must be > 0");
        Path zipPath = Paths.get(resp.artifactUri());
        assertTrue(Files.exists(zipPath), "zip file must exist on disk");

        try (ZipFile zip = new ZipFile(zipPath.toFile())) {
            assertNotNull(zip.getEntry("manifest.json"));
            assertNotNull(zip.getEntry("README.md"));
            boolean foundMapping = false;
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry e = entries.nextElement();
                if (e.getName().startsWith("mappings/") && e.getName().endsWith(".json")) {
                    foundMapping = true;
                    break;
                }
            }
            assertTrue(foundMapping, "at least one mapping file must be in zip");
        }
        assertEquals(ReleaseStatus.PUBLISHED, draft.getStatus());
        assertNotNull(draft.getPublishedAt());
        assertNull(draft.getFailureReason());
    }

    @Test
    @DisplayName("build is idempotent when already PUBLISHED")
    void build_alreadyPublished_isIdempotent() {
        ProjectEntity project = sampleProject();
        ReleaseEntity published = ReleaseEntity.builder()
            .id(releaseId).projectId(projectId).version("1.0.0").name("n")
            .status(ReleaseStatus.PUBLISHED)
            .createdBy(ownerId)
            .createdAt(Instant.now()).updatedAt(Instant.now())
            .artifactUri("/existing/artifact.zip")
            .artifactSizeBytes(123L)
            .manifest(Map.of("validationGateResult", Map.of("passed", true)))
            .build();
        when(releaseRepository.findById(releaseId)).thenReturn(Optional.of(published));
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        ReleaseBuildResponse resp = service.build(releaseId, owner);

        assertEquals("/existing/artifact.zip", resp.artifactUri());
        assertEquals(123L, resp.artifactSizeBytes());
        verify(releaseRepository, never()).save(any());
    }

    @Test
    @DisplayName("build refuses archived release")
    void build_archived_rejected() {
        ProjectEntity project = sampleProject();
        ReleaseEntity archived = ReleaseEntity.builder()
            .id(releaseId).projectId(projectId).version("1.0.0").name("n")
            .status(ReleaseStatus.ARCHIVED)
            .createdBy(ownerId)
            .createdAt(Instant.now()).updatedAt(Instant.now())
            .build();
        when(releaseRepository.findById(releaseId)).thenReturn(Optional.of(archived));
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        assertThrows(PipelineValidationException.class, () -> service.build(releaseId, owner));
    }

    // ─────────────────── new build tests ───────────────────

    @Test
    @DisplayName("build with all assets resolvable publishes bundle with real content")
    void build_withAllAssetsResolvable_publishesBundleWithRealContent() throws IOException {
        ProjectEntity project = sampleProject();
        UUID mappingId = UUID.randomUUID();
        UUID ontologyId = UUID.randomUUID();
        UUID shapeId = UUID.randomUUID();
        UUID suiteId = UUID.randomUUID();
        UUID runId = UUID.randomUUID();
        UUID issueId = UUID.randomUUID();

        MappingEntity mapping = sampleMapping(mappingId, "my-mapping");
        String ontologyTurtle = "@prefix ex: <https://ex.org/> .\nex:Foo a <http://www.w3.org/2002/07/owl#Class> .\n";
        String shapeTurtle = "@prefix sh: <http://www.w3.org/ns/shacl#> .\n<https://ex.org/FooShape> a sh:NodeShape .\n";

        OntologySummary ontologySummary =
            new OntologySummary(ontologyId, "My Ontology", "ex", ontologyTurtle);
        ShapeSummary shapeSummary =
            new ShapeSummary(shapeId, "Foo Shape", shapeTurtle, "TURTLE");
        ValidationRunSummary runSummary = new ValidationRunSummary(
            runId, suiteId, projectId, Instant.now(), "PASSED",
            2, 0, 2, 0, 0, "All good");
        List<ValidationIssueSummary> issues = List.of(
            new ValidationIssueSummary(issueId, "rule-1", "WARNING",
                "https://ex.org/r", "msg", "path"));

        ReleaseEntity draft = buildDraftWithRefs(
            List.of(mappingId), List.of(ontologyId), List.of(shapeId), List.of(suiteId));

        when(releaseRepository.findById(releaseId)).thenReturn(Optional.of(draft));
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(mappingRepository.findById(mappingId)).thenReturn(Optional.of(mapping));
        when(releaseRepository.save(any(ReleaseEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(shaclClient.fetchOntology(eq(ontologyId), any(), anyString()))
            .thenReturn(ontologySummary);
        when(shaclClient.fetchShape(eq(shapeId), any())).thenReturn(shapeSummary);
        when(shaclClient.fetchLatestRun(eq(suiteId), any())).thenReturn(runSummary);
        when(shaclClient.fetchIssues(eq(runId), any(), anyInt())).thenReturn(issues);

        ReleaseBuildResponse resp = service.build(releaseId, owner);

        assertNotNull(resp.artifactUri());
        Path zipPath = Paths.get(resp.artifactUri());
        assertTrue(Files.exists(zipPath));
        List<String> names = listZipEntryNames(zipPath);

        // ontologies/{slug}.ttl
        String ontPath = names.stream()
            .filter(n -> n.startsWith("ontologies/") && n.endsWith(".ttl"))
            .findFirst().orElse(null);
        assertNotNull(ontPath, "ontologies/<slug>.ttl must be present");
        assertEquals(ontologyTurtle, readZipEntryAsString(zipPath, ontPath),
            "ontology entry must contain real Turtle bytes");

        // shapes/{slug}.ttl
        String shapePath = names.stream()
            .filter(n -> n.startsWith("shapes/") && n.endsWith(".ttl"))
            .findFirst().orElse(null);
        assertNotNull(shapePath, "shapes/<slug>.ttl must be present");
        assertEquals(shapeTurtle, readZipEntryAsString(zipPath, shapePath),
            "shape entry must contain real Turtle bytes");

        // validation-summary.json
        String validationBody = readZipEntryAsString(zipPath, "validation-summary.json");
        assertNotNull(validationBody, "validation-summary.json must be present");
        assertTrue(validationBody.contains(suiteId.toString()));
        assertTrue(validationBody.contains("\"status\" : \"PASSED\"")
            || validationBody.contains("\"status\":\"PASSED\""));

        // No placeholder files anywhere.
        for (String n : names) {
            assertFalse(n.contains("NOT_YET_FETCHED"), "placeholder entry present: " + n);
            assertFalse(n.contains(".placeholder"), "placeholder entry present: " + n);
            assertFalse(n.endsWith("sample-queries.sparql"),
                "sample-queries.sparql should no longer be emitted");
        }

        assertEquals(ReleaseStatus.PUBLISHED, draft.getStatus());
        assertNotNull(draft.getPublishedAt());
        assertNull(draft.getFailureReason());
    }

    @Test
    @DisplayName("build with ontology fetch 404 transitions release to FAILED")
    void build_ontologyFetchReturns404_releaseTransitionsToFailed() {
        ProjectEntity project = sampleProject();
        UUID ontologyId = UUID.randomUUID();
        ReleaseEntity draft = buildDraftWithRefs(
            List.of(), List.of(ontologyId), List.of(), List.of());

        when(releaseRepository.findById(releaseId)).thenReturn(Optional.of(draft));
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(releaseRepository.save(any(ReleaseEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(shaclClient.fetchOntology(eq(ontologyId), any(), anyString()))
            .thenThrow(new ShaclClientException(404,
                "Failed to fetch ontology " + ontologyId + ": HTTP 404"));

        ReleaseBuildResponse resp = service.build(releaseId, owner);

        assertNull(resp.artifactUri(), "FAILED build must not produce an artifact URI");
        assertEquals(ReleaseStatus.FAILED, draft.getStatus());
        assertNotNull(draft.getFailureReason());
        assertTrue(draft.getFailureReason().toLowerCase().contains("ontology"),
            "failureReason must mention ontology: " + draft.getFailureReason());
        assertTrue(draft.getFailureReason().contains(ontologyId.toString()),
            "failureReason must contain the failing id: " + draft.getFailureReason());
        assertNull(draft.getArtifactUri(), "artifactUri must be null on FAILED build");
        assertEquals(0L, draft.getArtifactSizeBytes());
    }

    @Test
    @DisplayName("build with shape 403 fails and deletes any partial artifact")
    void build_shapeFetchReturns403_releaseFailsAndDeletesArtifact() throws IOException {
        ProjectEntity project = sampleProject();
        UUID shapeId = UUID.randomUUID();
        ReleaseEntity draft = buildDraftWithRefs(
            List.of(), List.of(), List.of(shapeId), List.of());

        when(releaseRepository.findById(releaseId)).thenReturn(Optional.of(draft));
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(releaseRepository.save(any(ReleaseEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(shaclClient.fetchShape(eq(shapeId), any()))
            .thenThrow(new ShaclClientException(403,
                "Failed to fetch shape " + shapeId + ": HTTP 403"));

        ReleaseBuildResponse resp = service.build(releaseId, owner);

        assertNull(resp.artifactUri());
        assertEquals(ReleaseStatus.FAILED, draft.getStatus());
        assertTrue(draft.getFailureReason().toLowerCase().contains("shape"),
            "failureReason must mention shape: " + draft.getFailureReason());
        assertTrue(draft.getFailureReason().contains("403"),
            "failureReason must echo the HTTP status: " + draft.getFailureReason());

        // No artifact file from this build attempt should remain anywhere in the artifact dir.
        try (var stream = Files.walk(tmpDir)) {
            boolean anyZip = stream
                .filter(Files::isRegularFile)
                .anyMatch(p -> p.getFileName().toString().endsWith(".zip")
                    || p.getFileName().toString().endsWith(".tmp"));
            assertFalse(anyZip, "tmp/zip files must not survive a FAILED build");
        }
    }

    @Test
    @DisplayName("build with downstream 5xx transitions to FAILED with descriptive reason")
    void build_downstream5xx_releaseFails() {
        ProjectEntity project = sampleProject();
        UUID ontologyId = UUID.randomUUID();
        ReleaseEntity draft = buildDraftWithRefs(
            List.of(), List.of(ontologyId), List.of(), List.of());

        when(releaseRepository.findById(releaseId)).thenReturn(Optional.of(draft));
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(releaseRepository.save(any(ReleaseEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(shaclClient.fetchOntology(eq(ontologyId), any(), anyString()))
            .thenThrow(new ShaclClientException(503,
                "Failed to fetch ontology " + ontologyId + ": HTTP 503"));

        ReleaseBuildResponse resp = service.build(releaseId, owner);

        assertNull(resp.artifactUri());
        assertEquals(ReleaseStatus.FAILED, draft.getStatus());
        assertNotNull(draft.getFailureReason());
        assertTrue(draft.getFailureReason().contains("503"),
            "failureReason must mention the HTTP 5xx status");
        assertTrue(draft.getFailureReason().toLowerCase().contains("ontology")
            || draft.getFailureReason().toLowerCase().contains("fetch"));
    }

    @Test
    @DisplayName("build with empty manifest kinds produces a minimal but complete bundle")
    void build_emptyManifestKinds_producesMinimalBundle() throws IOException {
        ProjectEntity project = sampleProject();
        ReleaseEntity draft = buildDraftWithRefs(List.of(), List.of(), List.of(), List.of());

        when(releaseRepository.findById(releaseId)).thenReturn(Optional.of(draft));
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(releaseRepository.save(any(ReleaseEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ReleaseBuildResponse resp = service.build(releaseId, owner);

        assertNotNull(resp.artifactUri());
        Path zipPath = Paths.get(resp.artifactUri());
        List<String> names = listZipEntryNames(zipPath);
        assertTrue(names.contains("manifest.json"), "manifest.json must be present");
        assertTrue(names.contains("README.md"), "README.md must be present");

        // No stub folders at all for unreferenced kinds.
        for (String n : names) {
            assertFalse(n.startsWith("ontologies/"),
                "ontologies/ should not exist when no ontologyIds were requested");
            assertFalse(n.startsWith("shapes/"),
                "shapes/ should not exist when no shapeIds were requested");
            assertNotEquals("validation-summary.json", n,
                "validation-summary.json should not exist when no suite ids were requested");
            assertNotEquals("sample-queries.sparql", n,
                "sample-queries.sparql is no longer emitted");
            assertFalse(n.contains(".placeholder"));
            assertFalse(n.contains("NOT_YET_FETCHED"));
        }
        assertEquals(ReleaseStatus.PUBLISHED, draft.getStatus());
        verify(shaclClient, never()).fetchOntology(any(), any(), anyString());
        verify(shaclClient, never()).fetchShape(any(), any());
        verify(shaclClient, never()).fetchLatestRun(any(), any());
    }

    @Test
    @DisplayName("build forwards the caller's identity on every downstream call")
    void build_forwardsIdentityHeaders() {
        ProjectEntity project = sampleProject();
        UUID ontologyId = UUID.randomUUID();
        UUID shapeId = UUID.randomUUID();
        UUID suiteId = UUID.randomUUID();

        ReleaseEntity draft = buildDraftWithRefs(
            List.of(), List.of(ontologyId), List.of(shapeId), List.of(suiteId));

        when(releaseRepository.findById(releaseId)).thenReturn(Optional.of(draft));
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(releaseRepository.save(any(ReleaseEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(shaclClient.fetchOntology(eq(ontologyId), any(), anyString()))
            .thenReturn(new OntologySummary(ontologyId, "O", "o", "@prefix x: <> .\n"));
        when(shaclClient.fetchShape(eq(shapeId), any()))
            .thenReturn(new ShapeSummary(shapeId, "S", "@prefix sh: <> .\n", "TURTLE"));
        when(shaclClient.fetchLatestRun(eq(suiteId), any())).thenReturn(null);

        service.build(releaseId, owner);

        // Capture the AuthUser passed into each downstream call and assert it
        // carries the caller's id/email/roles. The client itself is responsible
        // for translating those into X-User-* headers; this proves the release
        // path doesn't drop identity on the floor.
        ArgumentCaptor<AuthUser> userCap = ArgumentCaptor.forClass(AuthUser.class);
        verify(shaclClient).fetchOntology(eq(ontologyId), userCap.capture(), anyString());
        assertEquals(ownerId, userCap.getValue().id());
        assertEquals(owner.email(), userCap.getValue().email());
        assertEquals(owner.roles(), userCap.getValue().roles());

        ArgumentCaptor<AuthUser> shapeUserCap = ArgumentCaptor.forClass(AuthUser.class);
        verify(shaclClient).fetchShape(eq(shapeId), shapeUserCap.capture());
        assertEquals(ownerId, shapeUserCap.getValue().id());

        ArgumentCaptor<AuthUser> runUserCap = ArgumentCaptor.forClass(AuthUser.class);
        verify(shaclClient).fetchLatestRun(eq(suiteId), runUserCap.capture());
        assertEquals(ownerId, runUserCap.getValue().id());
    }

    @Test
    @DisplayName("partial success still fails the whole build if any required asset fails")
    void build_partialSuccess_stillFailsIfAnyKindRequested() {
        ProjectEntity project = sampleProject();
        UUID ontologyId = UUID.randomUUID();
        UUID shapeId = UUID.randomUUID();

        ReleaseEntity draft = buildDraftWithRefs(
            List.of(), List.of(ontologyId), List.of(shapeId), List.of());

        when(releaseRepository.findById(releaseId)).thenReturn(Optional.of(draft));
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(releaseRepository.save(any(ReleaseEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        // Ontology fetches fine…
        when(shaclClient.fetchOntology(eq(ontologyId), any(), anyString()))
            .thenReturn(new OntologySummary(ontologyId, "Okay Ont", "ok",
                "@prefix ex: <https://ex.org/> .\n"));
        // …but the shape 404s.
        when(shaclClient.fetchShape(eq(shapeId), any()))
            .thenThrow(new ShaclClientException(404,
                "Failed to fetch shape " + shapeId + ": HTTP 404"));

        ReleaseBuildResponse resp = service.build(releaseId, owner);

        assertNull(resp.artifactUri(), "no artifact when any required asset failed");
        assertEquals(ReleaseStatus.FAILED, draft.getStatus());
        assertTrue(draft.getFailureReason().toLowerCase().contains("shape"));
        assertTrue(draft.getFailureReason().contains("404"));
    }

    // ─────────────────── list / archive / download ───────────────────

    @Test
    @DisplayName("list delegates to repo and enforces project ownership")
    void list_delegates() {
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(sampleProject()));
        when(releaseRepository.findByProjectIdOrderByCreatedAtDesc(projectId))
            .thenReturn(List.of());
        assertTrue(service.list(projectId, owner).isEmpty());
    }

    @Test
    @DisplayName("archive flips to ARCHIVED")
    void archive_flips() {
        ReleaseEntity draft = ReleaseEntity.builder()
            .id(releaseId).projectId(projectId).version("1.0.0").name("n")
            .status(ReleaseStatus.DRAFT)
            .createdBy(ownerId)
            .createdAt(Instant.now()).updatedAt(Instant.now())
            .build();
        when(releaseRepository.findById(releaseId)).thenReturn(Optional.of(draft));
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(sampleProject()));
        when(releaseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReleaseDto dto = service.archive(releaseId, owner);
        assertEquals(ReleaseStatus.ARCHIVED, dto.status());
    }

    @Test
    @DisplayName("admin can archive someone else's release")
    void archive_admin_allowed() {
        ReleaseEntity draft = ReleaseEntity.builder()
            .id(releaseId).projectId(projectId).version("1.0.0").name("n")
            .status(ReleaseStatus.DRAFT)
            .createdBy(ownerId)
            .createdAt(Instant.now()).updatedAt(Instant.now())
            .build();
        when(releaseRepository.findById(releaseId)).thenReturn(Optional.of(draft));
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(sampleProject()));
        when(releaseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReleaseDto dto = service.archive(releaseId, admin);
        assertEquals(ReleaseStatus.ARCHIVED, dto.status());
    }

    @Test
    @DisplayName("download of unbuilt release → validation error")
    void download_unbuilt_rejected() {
        ReleaseEntity draft = ReleaseEntity.builder()
            .id(releaseId).projectId(projectId).version("1.0.0").name("n")
            .status(ReleaseStatus.DRAFT)
            .createdBy(ownerId)
            .createdAt(Instant.now()).updatedAt(Instant.now())
            .build();
        when(releaseRepository.findById(releaseId)).thenReturn(Optional.of(draft));
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(sampleProject()));
        assertThrows(PipelineValidationException.class, () -> service.download(releaseId, owner));
    }
}
