package io.rdfforge.pipeline.service;

import io.rdfforge.common.exception.PipelineValidationException;
import io.rdfforge.common.exception.ResourceNotFoundException;
import io.rdfforge.common.security.AuthUser;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ReleaseService}. Covers:
 *  - draft creation (authz + SemVer + duplicate version rejection)
 *  - build assembly produces a valid non-empty zip on disk
 *  - archive lifecycle
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ReleaseService Tests")
class ReleaseServiceTest {

    @Mock private ReleaseRepository releaseRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private MappingRepository mappingRepository;

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
        service = new ReleaseService(releaseRepository, projectRepository, mappingRepository);
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

    // ─────────────────── create ───────────────────

    @Test
    @DisplayName("create valid draft stamps createdBy from user, persists DRAFT")
    void create_valid_persistsDraft() {
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(sampleProject()));
        when(releaseRepository.existsByProjectIdAndVersion(projectId, "1.0.0")).thenReturn(false);
        when(releaseRepository.save(any(ReleaseEntity.class))).thenAnswer(inv -> {
            ReleaseEntity e = inv.getArgument(0);
            e.setId(releaseId);
            // @PrePersist runs inside JPA; in the mock we stamp the timestamps
            // explicitly so DTO conversion doesn't NPE.
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
        // validateVersion short-circuits before repo lookup — no stubs needed.
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

    // ─────────────────── build ───────────────────

    @Test
    @DisplayName("build produces a valid non-empty zip with manifest.json and README.md")
    void build_producesZip() throws IOException {
        ProjectEntity project = sampleProject();
        UUID mappingId = UUID.randomUUID();
        MappingEntity mapping = sampleMapping(mappingId, "my-mapping");

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
        // Populate refs manifest so gatherMappings finds something
        draft.setManifest(new java.util.HashMap<>(Map.of("refs", Map.of(
            "mappings", List.of(mappingId.toString()),
            "dataSources", List.of(),
            "shapes", List.of(),
            "ontologies", List.of(),
            "validationSuiteIds", List.of()
        ))));

        when(releaseRepository.findById(releaseId)).thenReturn(Optional.of(draft));
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));
        when(mappingRepository.findById(mappingId)).thenReturn(Optional.of(mapping));
        when(releaseRepository.save(any(ReleaseEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ReleaseBuildResponse resp = service.build(releaseId, owner);

        assertNotNull(resp.artifactUri(), "build must set artifactUri on success");
        assertTrue(resp.artifactSizeBytes() > 0, "artifact size must be > 0");
        Path zipPath = Paths.get(resp.artifactUri());
        assertTrue(Files.exists(zipPath), "zip file must exist on disk");

        // Verify zip structure: manifest.json + README.md + mappings/*.json
        try (ZipFile zip = new ZipFile(zipPath.toFile())) {
            assertNotNull(zip.getEntry("manifest.json"), "manifest.json must be in zip");
            assertNotNull(zip.getEntry("README.md"), "README.md must be in zip");
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

    // ─────────────────── list / archive ───────────────────

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
