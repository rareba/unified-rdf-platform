package io.rdfforge.pipeline.lineage;

import io.rdfforge.common.exception.PipelineValidationException;
import io.rdfforge.common.security.AuthUser;
import io.rdfforge.pipeline.dto.LineageDto;
import io.rdfforge.pipeline.entity.MappingEntity;
import io.rdfforge.pipeline.entity.MappingType;
import io.rdfforge.pipeline.entity.PipelineEntity;
import io.rdfforge.pipeline.entity.ProjectEntity;
import io.rdfforge.pipeline.entity.ProjectStatus;
import io.rdfforge.pipeline.entity.ReleaseEntity;
import io.rdfforge.pipeline.entity.ReleaseStatus;
import io.rdfforge.pipeline.entity.SourceType;
import io.rdfforge.pipeline.repository.MappingRepository;
import io.rdfforge.pipeline.repository.PipelineRepository;
import io.rdfforge.pipeline.repository.ProjectRepository;
import io.rdfforge.pipeline.repository.ReleaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link LineageService}. Fixtures populate mapping +
 * pipeline + release repos, then assert the composed graph contains the
 * expected nodes and edges.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("LineageService Tests")
class LineageServiceTest {

    @Mock private ProjectRepository projectRepository;
    @Mock private MappingRepository mappingRepository;
    @Mock private PipelineRepository pipelineRepository;
    @Mock private ReleaseRepository releaseRepository;

    private LineageService service;

    private UUID ownerId;
    private UUID otherUserId;
    private UUID projectId;
    private AuthUser owner;
    private AuthUser other;
    private AuthUser admin;

    @BeforeEach
    void setUp() {
        service = new LineageService(projectRepository, mappingRepository,
            pipelineRepository, releaseRepository);
        ownerId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        owner = new AuthUser(ownerId, "o@x.org", Set.of("USER"));
        other = new AuthUser(otherUserId, "b@x.org", Set.of("USER"));
        admin = new AuthUser(UUID.randomUUID(), "a@x.org", Set.of("ADMIN"));
    }

    private ProjectEntity project() {
        return ProjectEntity.builder()
            .id(projectId).name("P").baseUri("https://ex.org/")
            .status(ProjectStatus.ACTIVE)
            .createdBy(ownerId)
            .createdAt(Instant.now()).updatedAt(Instant.now())
            .build();
    }

    private MappingEntity mapping(UUID id, String name, UUID dataSourceId) {
        return MappingEntity.builder()
            .id(id).projectId(projectId).name(name)
            .sourceType(SourceType.CSV)
            .sourceConfig(dataSourceId == null ? Map.of() : Map.of(
                "sourceDataRef", dataSourceId.toString()))
            .mappingType(MappingType.GENERIC)
            .targetOntologies(Map.of("prefixes", Map.of("ex", "https://ex.org/onto/")))
            .rules(new ArrayList<>())
            .version(1)
            .createdBy(ownerId)
            .createdAt(Instant.now()).updatedAt(Instant.now())
            .build();
    }

    private PipelineEntity pipeline(UUID id, String name) {
        PipelineEntity p = PipelineEntity.builder()
            .id(id).projectId(projectId).name(name)
            .definition("{}").version(1).isTemplate(false)
            .createdBy(ownerId)
            .createdAt(Instant.now()).updatedAt(Instant.now())
            .build();
        return p;
    }

    private ReleaseEntity release(UUID id, UUID mappingId) {
        // Map.of rejects null values so triplestoreId is simply omitted.
        java.util.Map<String, Object> refs = new java.util.LinkedHashMap<>();
        refs.put("mappings", List.of(mappingId.toString()));
        refs.put("dataSources", List.of());
        refs.put("shapes", List.of());
        refs.put("ontologies", List.of());
        refs.put("validationSuiteIds", List.of());
        java.util.Map<String, Object> manifest = new java.util.LinkedHashMap<>();
        manifest.put("refs", refs);
        return ReleaseEntity.builder()
            .id(id).projectId(projectId).version("1.0.0").name("rel")
            .status(ReleaseStatus.PUBLISHED)
            .createdBy(ownerId)
            .createdAt(Instant.now()).updatedAt(Instant.now())
            .manifest(manifest)
            .artifactSizeBytes(10L)
            .build();
    }

    @Test
    @DisplayName("forProject assembles project + mappings + pipelines + releases")
    void forProject_composesGraph() {
        UUID mappingId = UUID.randomUUID();
        UUID dsId = UUID.randomUUID();
        UUID pipelineId = UUID.randomUUID();
        UUID releaseId = UUID.randomUUID();

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project()));
        when(mappingRepository.findByProjectIdOrderByUpdatedAtDesc(projectId))
            .thenReturn(List.of(mapping(mappingId, "map1", dsId)));
        when(pipelineRepository.findByProjectIdOrderByUpdatedAtDesc(projectId))
            .thenReturn(List.of(pipeline(pipelineId, "pipe1")));
        when(releaseRepository.findByProjectIdOrderByCreatedAtDesc(projectId))
            .thenReturn(List.of(release(releaseId, mappingId)));

        LineageDto dto = service.forProject(projectId, owner);

        assertEquals(projectId, dto.projectId());
        // Expect at minimum: project + mapping + datasource + pipeline + release + ontology
        assertTrue(dto.nodes().size() >= 6, "graph must include root + children");
        // Project BELONGS_TO edges exist for mapping, datasource, pipeline, release
        long belongs = dto.edges().stream()
            .filter(e -> e.kind() == LineageDto.EdgeKind.BELONGS_TO).count();
        assertTrue(belongs >= 4, "each child node belongs_to project");
        // Mapping USED_BY dataSource
        assertTrue(dto.edges().stream().anyMatch(e ->
            e.kind() == LineageDto.EdgeKind.USED_BY
                && e.from().contains("mapping-" + mappingId)
                && e.to().contains("data-" + dsId)));
        // Release DERIVED_FROM mapping
        assertTrue(dto.edges().stream().anyMatch(e ->
            e.kind() == LineageDto.EdgeKind.DERIVED_FROM
                && e.from().contains("release-" + releaseId)
                && e.to().contains("mapping-" + mappingId)));
        // Ontology REFERENCES edge from mapping
        assertTrue(dto.edges().stream().anyMatch(e ->
            e.kind() == LineageDto.EdgeKind.REFERENCES
                && e.from().contains("mapping-" + mappingId)
                && e.to().contains("ontology-")));
    }

    @Test
    @DisplayName("forProject denies non-owner")
    void forProject_nonOwner_denied() {
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project()));
        assertThrows(AccessDeniedException.class, () -> service.forProject(projectId, other));
    }

    @Test
    @DisplayName("forProject allows admin")
    void forProject_admin_allowed() {
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project()));
        when(mappingRepository.findByProjectIdOrderByUpdatedAtDesc(projectId)).thenReturn(List.of());
        when(pipelineRepository.findByProjectIdOrderByUpdatedAtDesc(projectId)).thenReturn(List.of());
        when(releaseRepository.findByProjectIdOrderByCreatedAtDesc(projectId)).thenReturn(List.of());
        LineageDto dto = service.forProject(projectId, admin);
        assertEquals(1, dto.nodes().size(), "only project node for an empty project");
    }

    @Test
    @DisplayName("forResource MAPPING returns focused subgraph")
    void forResource_mapping_subgraph() {
        UUID mappingId = UUID.randomUUID();
        UUID dsId = UUID.randomUUID();

        when(mappingRepository.findById(mappingId))
            .thenReturn(Optional.of(mapping(mappingId, "map1", dsId)));
        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project()));
        when(mappingRepository.findByProjectIdOrderByUpdatedAtDesc(projectId))
            .thenReturn(List.of(mapping(mappingId, "map1", dsId)));
        when(pipelineRepository.findByProjectIdOrderByUpdatedAtDesc(projectId)).thenReturn(List.of());
        when(releaseRepository.findByProjectIdOrderByCreatedAtDesc(projectId)).thenReturn(List.of());

        LineageDto dto = service.forResource("MAPPING", mappingId, owner);

        // Subgraph should include mapping + project + datasource + ontology (direct neighbours)
        assertTrue(dto.nodes().stream().anyMatch(n ->
            n.id().contains("mapping-" + mappingId)));
        assertTrue(dto.nodes().stream().anyMatch(n ->
            n.id().contains("data-" + dsId)));
    }

    @Test
    @DisplayName("forResource rejects unknown kind")
    void forResource_unknownKind_rejected() {
        assertThrows(PipelineValidationException.class,
            () -> service.forResource("BOGUS", UUID.randomUUID(), owner));
    }

    @Test
    @DisplayName("forResource without auth denied")
    void forResource_anonymous_denied() {
        assertThrows(AccessDeniedException.class,
            () -> service.forResource("PROJECT", projectId, AuthUser.anonymous()));
    }
}
