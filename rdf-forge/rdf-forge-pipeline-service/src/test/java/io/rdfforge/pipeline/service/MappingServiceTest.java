package io.rdfforge.pipeline.service;

import io.rdfforge.common.exception.PipelineValidationException;
import io.rdfforge.common.exception.ResourceNotFoundException;
import io.rdfforge.common.security.AuthUser;
import io.rdfforge.engine.mapping.MappingExecutor;
import io.rdfforge.pipeline.dto.ExplainRequest;
import io.rdfforge.pipeline.dto.ExplainResponse;
import io.rdfforge.pipeline.dto.MappingCreateRequest;
import io.rdfforge.pipeline.dto.MappingDto;
import io.rdfforge.pipeline.dto.MappingPreviewRequest;
import io.rdfforge.pipeline.dto.MappingPreviewResponse;
import io.rdfforge.pipeline.dto.MappingUpdateRequest;
import io.rdfforge.pipeline.entity.MappingEntity;
import io.rdfforge.pipeline.entity.MappingRule;
import io.rdfforge.pipeline.entity.MappingType;
import io.rdfforge.pipeline.entity.ProjectEntity;
import io.rdfforge.pipeline.entity.ProjectStatus;
import io.rdfforge.pipeline.entity.SourceType;
import io.rdfforge.pipeline.repository.MappingRepository;
import io.rdfforge.pipeline.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link MappingService}. Covers authz (owner/admin),
 * uniqueness, rule-set validation, and preview/explain delegation to the
 * real {@link MappingExecutor} — we deliberately instantiate a real executor
 * so the integration between service and engine is exercised.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MappingService Tests")
class MappingServiceTest {

    @Mock
    private MappingRepository mappingRepository;

    @Mock
    private ProjectRepository projectRepository;

    private MappingExecutor executor;
    private MappingService service;

    private UUID ownerId;
    private UUID otherUserId;
    private UUID projectId;
    private UUID mappingId;
    private AuthUser owner;
    private AuthUser other;
    private AuthUser admin;

    @BeforeEach
    void setUp() {
        executor = new MappingExecutor();
        service = new MappingService(mappingRepository, projectRepository, executor);
        ReflectionTestUtils.setField(service, "previewMaxRows", 50);
        ReflectionTestUtils.setField(service, "explainMaxRows", 5);

        ownerId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        mappingId = UUID.randomUUID();

        owner = new AuthUser(ownerId, "o@x.org", Set.of("USER"));
        other = new AuthUser(otherUserId, "b@x.org", Set.of("USER"));
        admin = new AuthUser(UUID.randomUUID(), "a@x.org", Set.of("ADMIN"));
    }

    private ProjectEntity sampleProject() {
        return ProjectEntity.builder()
            .id(projectId)
            .name("P")
            .baseUri("https://ex.org/")
            .status(ProjectStatus.ACTIVE)
            .createdBy(ownerId)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
    }

    private MappingEntity sampleMapping() {
        List<MappingRule> rules = new ArrayList<>();
        rules.add(new MappingRule(
            "s", MappingRule.RuleType.FIXED_URI,
            null, null, "${baseUri}r/${id}", null, null, null));
        rules.add(new MappingRule(
            "l", MappingRule.RuleType.COLUMN_TO_LITERAL,
            "name", "http://ex.org/name", null, null, null, null));
        return MappingEntity.builder()
            .id(mappingId)
            .projectId(projectId)
            .name("Test Mapping")
            .sourceType(SourceType.CSV)
            .targetNamespace("https://ex.org/")
            .rules(rules)
            .mappingType(MappingType.GENERIC)
            .version(1)
            .createdBy(ownerId)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
    }

    @Nested
    @DisplayName("create")
    class CreateTests {

        @Test
        @DisplayName("owner creates mapping: rules + metadata persisted")
        void create_ownerCreates() {
            MappingCreateRequest req = new MappingCreateRequest(
                projectId, "My Map", "desc", SourceType.CSV,
                Map.of("delimiter", ","), "https://ex.org/", null,
                List.of(new MappingRule("r1", MappingRule.RuleType.FIXED_URI,
                    null, null, "${baseUri}r/${id}", null, null, null)),
                MappingType.GENERIC);
            when(projectRepository.findById(projectId)).thenReturn(Optional.of(sampleProject()));
            when(mappingRepository.existsByProjectIdAndName(projectId, "My Map")).thenReturn(false);
            when(mappingRepository.save(any(MappingEntity.class))).thenAnswer(inv -> {
                MappingEntity e = inv.getArgument(0);
                e.setId(mappingId);
                return e;
            });

            MappingDto result = service.create(req, owner);
            assertEquals("My Map", result.name());
            assertEquals(MappingType.GENERIC, result.mappingType());
            assertEquals(1, result.rules().size());
        }

        @Test
        @DisplayName("non-owner → AccessDeniedException")
        void create_nonOwner() {
            when(projectRepository.findById(projectId)).thenReturn(Optional.of(sampleProject()));
            MappingCreateRequest req = new MappingCreateRequest(
                projectId, "X", null, SourceType.CSV, null, null, null, List.of(), null);
            assertThrows(AccessDeniedException.class, () -> service.create(req, other));
        }

        @Test
        @DisplayName("duplicate name in same project → validation error")
        void create_duplicateName() {
            when(projectRepository.findById(projectId)).thenReturn(Optional.of(sampleProject()));
            when(mappingRepository.existsByProjectIdAndName(projectId, "Dup")).thenReturn(true);
            MappingCreateRequest req = new MappingCreateRequest(
                projectId, "Dup", null, SourceType.CSV, null, null, null, List.of(), null);
            assertThrows(PipelineValidationException.class, () -> service.create(req, owner));
            verify(mappingRepository, never()).save(any());
        }

        @Test
        @DisplayName("duplicate rule ids → validation error")
        void create_duplicateRuleIds() {
            when(projectRepository.findById(projectId)).thenReturn(Optional.of(sampleProject()));
            when(mappingRepository.existsByProjectIdAndName(projectId, "X")).thenReturn(false);
            MappingCreateRequest req = new MappingCreateRequest(
                projectId, "X", null, SourceType.CSV, null, null, null,
                List.of(
                    new MappingRule("dup", MappingRule.RuleType.FIXED_URI,
                        null, null, "${baseUri}/a", null, null, null),
                    new MappingRule("dup", MappingRule.RuleType.FIXED_URI,
                        null, null, "${baseUri}/b", null, null, null)
                ), null);
            assertThrows(PipelineValidationException.class, () -> service.create(req, owner));
        }

        @Test
        @DisplayName("anonymous user → AccessDeniedException")
        void create_anonymous() {
            MappingCreateRequest req = new MappingCreateRequest(
                projectId, "X", null, SourceType.CSV, null, null, null, null, null);
            assertThrows(AccessDeniedException.class,
                () -> service.create(req, AuthUser.anonymous()));
        }
    }

    @Nested
    @DisplayName("update")
    class UpdateTests {

        @Test
        @DisplayName("owner updates rules and version bumps")
        void update_rulesVersion() {
            when(mappingRepository.findById(mappingId)).thenReturn(Optional.of(sampleMapping()));
            when(projectRepository.findById(projectId)).thenReturn(Optional.of(sampleProject()));
            when(mappingRepository.save(any(MappingEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            MappingUpdateRequest req = new MappingUpdateRequest(
                null, null, null, null, null, null,
                List.of(new MappingRule("new", MappingRule.RuleType.FIXED_URI,
                    null, null, "${baseUri}/a", null, null, null)));
            MappingDto out = service.update(mappingId, req, owner);
            assertEquals(2, out.version());
            assertEquals(1, out.rules().size());
            assertEquals("new", out.rules().get(0).id());
        }

        @Test
        @DisplayName("non-owner blocked from update")
        void update_nonOwnerDenied() {
            when(mappingRepository.findById(mappingId)).thenReturn(Optional.of(sampleMapping()));
            when(projectRepository.findById(projectId)).thenReturn(Optional.of(sampleProject()));
            MappingUpdateRequest req = new MappingUpdateRequest(
                "other", null, null, null, null, null, null);
            assertThrows(AccessDeniedException.class, () -> service.update(mappingId, req, other));
        }
    }

    @Nested
    @DisplayName("preview / explain")
    class PreviewExplainTests {

        @Test
        @DisplayName("preview materializes triples from inline rows")
        void preview_happyPath() {
            when(mappingRepository.findById(mappingId)).thenReturn(Optional.of(sampleMapping()));
            when(projectRepository.findById(projectId)).thenReturn(Optional.of(sampleProject()));

            MappingPreviewRequest req = new MappingPreviewRequest(
                List.of(Map.of("id", "1", "name", "Alice")),
                null, null, 10);
            MappingPreviewResponse resp = service.preview(mappingId, req, owner);
            assertEquals(1, resp.sampleSize());
            assertEquals(1, resp.triples().size());
            assertEquals("http://ex.org/name", resp.triples().get(0).predicate());
            assertEquals("Alice", resp.triples().get(0).object());
        }

        @Test
        @DisplayName("explain returns trace per triple")
        void explain_happyPath() {
            when(mappingRepository.findById(mappingId)).thenReturn(Optional.of(sampleMapping()));
            when(projectRepository.findById(projectId)).thenReturn(Optional.of(sampleProject()));

            ExplainRequest req = new ExplainRequest(
                null,
                List.of(Map.of("id", "1", "name", "Alice")),
                5);
            ExplainResponse resp = service.explain(mappingId, req, owner);
            assertEquals(1, resp.rows().size());
            assertEquals(1, resp.rows().get(0).triples().size());
            var te = resp.rows().get(0).triples().get(0);
            assertEquals("l", te.trace().ruleId());
            assertEquals("name", te.trace().source());
        }

        @Test
        @DisplayName("explain with out-of-bounds sourceRowIndex → validation error")
        void explain_outOfBounds() {
            when(mappingRepository.findById(mappingId)).thenReturn(Optional.of(sampleMapping()));
            when(projectRepository.findById(projectId)).thenReturn(Optional.of(sampleProject()));
            ExplainRequest req = new ExplainRequest(5,
                List.of(Map.of("id", "1", "name", "Alice")), null);
            assertThrows(PipelineValidationException.class,
                () -> service.explain(mappingId, req, owner));
        }
    }

    @Nested
    @DisplayName("findById / delete")
    class ReadDeleteTests {

        @Test
        @DisplayName("admin can read any mapping")
        void findById_admin() {
            when(mappingRepository.findById(mappingId)).thenReturn(Optional.of(sampleMapping()));
            when(projectRepository.findById(projectId)).thenReturn(Optional.of(sampleProject()));
            MappingDto out = service.findById(mappingId, admin);
            assertEquals(mappingId, out.id());
        }

        @Test
        @DisplayName("missing mapping → 404")
        void findById_missing() {
            when(mappingRepository.findById(mappingId)).thenReturn(Optional.empty());
            assertThrows(ResourceNotFoundException.class,
                () -> service.findById(mappingId, owner));
        }

        @Test
        @DisplayName("delete requires owner or admin")
        void delete_nonOwner() {
            when(mappingRepository.findById(mappingId)).thenReturn(Optional.of(sampleMapping()));
            when(projectRepository.findById(projectId)).thenReturn(Optional.of(sampleProject()));
            assertThrows(AccessDeniedException.class, () -> service.delete(mappingId, other));
        }
    }
}
