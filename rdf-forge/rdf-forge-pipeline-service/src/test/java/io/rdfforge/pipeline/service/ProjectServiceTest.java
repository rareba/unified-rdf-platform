package io.rdfforge.pipeline.service;

import io.rdfforge.common.exception.PipelineValidationException;
import io.rdfforge.common.exception.ResourceNotFoundException;
import io.rdfforge.common.security.AuthUser;
import io.rdfforge.pipeline.dto.ProjectCreateRequest;
import io.rdfforge.pipeline.dto.ProjectDto;
import io.rdfforge.pipeline.dto.ProjectSummaryDto;
import io.rdfforge.pipeline.dto.ProjectUpdateRequest;
import io.rdfforge.pipeline.entity.ProjectEntity;
import io.rdfforge.pipeline.entity.ProjectStatus;
import io.rdfforge.pipeline.repository.PipelineRepository;
import io.rdfforge.pipeline.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link ProjectService} — exercises validation, ownership,
 * and lifecycle without touching the database.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProjectService Tests")
class ProjectServiceTest {

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private PipelineRepository pipelineRepository;

    private ProjectService service;

    private UUID ownerId;
    private UUID otherUserId;
    private UUID adminId;
    private UUID projectId;
    private AuthUser owner;
    private AuthUser other;
    private AuthUser admin;

    @BeforeEach
    void setUp() {
        service = new ProjectService(projectRepository, pipelineRepository);
        ownerId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();
        adminId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        owner = new AuthUser(ownerId, "owner@example.org", Set.of("USER"));
        other = new AuthUser(otherUserId, "other@example.org", Set.of("USER"));
        admin = new AuthUser(adminId, "admin@example.org", Set.of("ADMIN"));
    }

    private ProjectEntity sampleEntity() {
        return ProjectEntity.builder()
            .id(projectId)
            .name("My Project")
            .description("desc")
            .baseUri("https://example.org/proj-1/")
            .status(ProjectStatus.ACTIVE)
            .createdBy(ownerId)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();
    }

    @Nested
    @DisplayName("create")
    class CreateTests {

        @Test
        @DisplayName("valid project stamps createdBy from AuthUser, not request body")
        void create_validProject_stampsCreatedByFromUser() {
            ProjectCreateRequest req = new ProjectCreateRequest(
                "My Project", "desc", "https://example.org/proj-1/", Map.of());
            when(projectRepository.existsByCreatedByAndName(ownerId, "My Project")).thenReturn(false);
            when(projectRepository.save(any(ProjectEntity.class))).thenAnswer(inv -> {
                ProjectEntity e = inv.getArgument(0);
                e.setId(projectId);
                return e;
            });

            ProjectDto result = service.create(req, owner);

            ArgumentCaptor<ProjectEntity> captor = ArgumentCaptor.forClass(ProjectEntity.class);
            verify(projectRepository).save(captor.capture());
            assertEquals(ownerId, captor.getValue().getCreatedBy(),
                "createdBy must be the authenticated user, not a client-supplied value");
            assertEquals(ProjectStatus.ACTIVE, captor.getValue().getStatus());
            assertEquals("My Project", result.name());
        }

        @Test
        @DisplayName("duplicate name for same user → validation error")
        void create_duplicateName_throwsConflict() {
            ProjectCreateRequest req = new ProjectCreateRequest(
                "Dup", null, "https://example.org/", null);
            when(projectRepository.existsByCreatedByAndName(ownerId, "Dup")).thenReturn(true);

            assertThrows(PipelineValidationException.class, () -> service.create(req, owner));
            verify(projectRepository, never()).save(any());
        }

        @Test
        @DisplayName("invalid base URI (no scheme) → validation error")
        void create_invalidBaseUri_throwsValidation() {
            ProjectCreateRequest req = new ProjectCreateRequest(
                "X", null, "not-a-uri", null);

            assertThrows(PipelineValidationException.class, () -> service.create(req, owner));
            verify(projectRepository, never()).save(any());
        }

        @Test
        @DisplayName("base URI without trailing slash gets normalized")
        void create_baseUriWithoutTrailingSlash_appendsSlash() {
            ProjectCreateRequest req = new ProjectCreateRequest(
                "X", null, "https://example.org/proj", null);
            when(projectRepository.existsByCreatedByAndName(ownerId, "X")).thenReturn(false);
            when(projectRepository.save(any(ProjectEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            service.create(req, owner);

            ArgumentCaptor<ProjectEntity> captor = ArgumentCaptor.forClass(ProjectEntity.class);
            verify(projectRepository).save(captor.capture());
            assertEquals("https://example.org/proj/", captor.getValue().getBaseUri());
        }

        @Test
        @DisplayName("anonymous user → AccessDeniedException (defense in depth)")
        void create_anonymous_denied() {
            ProjectCreateRequest req = new ProjectCreateRequest(
                "X", null, "https://example.org/", null);
            assertThrows(AccessDeniedException.class,
                () -> service.create(req, AuthUser.anonymous()));
        }
    }

    @Nested
    @DisplayName("findById")
    class FindByIdTests {

        @Test
        @DisplayName("owner can read their own project")
        void findById_ownerAllowed() {
            when(projectRepository.findById(projectId)).thenReturn(Optional.of(sampleEntity()));
            ProjectDto result = service.findById(projectId, owner);
            assertEquals(projectId, result.id());
        }

        @Test
        @DisplayName("non-owner → AccessDeniedException")
        void findById_nonOwner_throwsAccessDenied() {
            when(projectRepository.findById(projectId)).thenReturn(Optional.of(sampleEntity()));
            assertThrows(AccessDeniedException.class, () -> service.findById(projectId, other));
        }

        @Test
        @DisplayName("admin may read any project")
        void findById_admin_allowed() {
            when(projectRepository.findById(projectId)).thenReturn(Optional.of(sampleEntity()));
            ProjectDto result = service.findById(projectId, admin);
            assertEquals(projectId, result.id());
        }

        @Test
        @DisplayName("missing project → ResourceNotFoundException")
        void findById_missing_throws404() {
            when(projectRepository.findById(projectId)).thenReturn(Optional.empty());
            assertThrows(ResourceNotFoundException.class, () -> service.findById(projectId, owner));
        }
    }

    @Nested
    @DisplayName("archive / unarchive")
    class LifecycleTests {

        @Test
        @DisplayName("archive flips status to ARCHIVED")
        void archive_setStatusToArchived() {
            ProjectEntity entity = sampleEntity();
            when(projectRepository.findById(projectId)).thenReturn(Optional.of(entity));
            when(projectRepository.save(any(ProjectEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            ProjectDto result = service.archive(projectId, owner);

            assertEquals(ProjectStatus.ARCHIVED, result.status());
            assertEquals(ProjectStatus.ARCHIVED, entity.getStatus());
        }

        @Test
        @DisplayName("unarchive flips ARCHIVED back to ACTIVE")
        void unarchive_setStatusToActive() {
            ProjectEntity entity = sampleEntity();
            entity.setStatus(ProjectStatus.ARCHIVED);
            when(projectRepository.findById(projectId)).thenReturn(Optional.of(entity));
            when(projectRepository.save(any(ProjectEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            ProjectDto result = service.unarchive(projectId, owner);

            assertEquals(ProjectStatus.ACTIVE, result.status());
        }
    }

    @Nested
    @DisplayName("delete")
    class DeleteTests {

        @Test
        @DisplayName("non-owner → AccessDeniedException and repo never called")
        void delete_nonOwner_throwsAccessDenied() {
            when(projectRepository.findById(projectId)).thenReturn(Optional.of(sampleEntity()));
            assertThrows(AccessDeniedException.class, () -> service.delete(projectId, other));
            verify(projectRepository, never()).delete(any(ProjectEntity.class));
        }

        @Test
        @DisplayName("owner deletes → repo.delete invoked")
        void delete_owner_deletes() {
            ProjectEntity entity = sampleEntity();
            when(projectRepository.findById(projectId)).thenReturn(Optional.of(entity));
            service.delete(projectId, owner);
            verify(projectRepository).delete(entity);
        }
    }

    @Nested
    @DisplayName("update")
    class UpdateTests {

        @Test
        @DisplayName("null fields leave existing values untouched")
        void update_nullFields_preserve() {
            ProjectEntity entity = sampleEntity();
            when(projectRepository.findById(projectId)).thenReturn(Optional.of(entity));
            when(projectRepository.save(any(ProjectEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            service.update(projectId, new ProjectUpdateRequest(null, null, null, null), owner);

            assertEquals("My Project", entity.getName());
            assertEquals("https://example.org/proj-1/", entity.getBaseUri());
        }

        @Test
        @DisplayName("renaming to an already-used name → validation error")
        void update_duplicateName_throws() {
            ProjectEntity entity = sampleEntity();
            when(projectRepository.findById(projectId)).thenReturn(Optional.of(entity));
            when(projectRepository.existsByCreatedByAndName(ownerId, "Taken")).thenReturn(true);

            assertThrows(PipelineValidationException.class, () -> service.update(projectId,
                new ProjectUpdateRequest("Taken", null, null, null), owner));
        }
    }

    @Nested
    @DisplayName("list")
    class ListTests {

        @Test
        @DisplayName("no status filter → repo.findByCreatedByOrderByUpdatedAtDesc")
        void list_noFilter_queriesByOwnerOnly() {
            when(projectRepository.findByCreatedByOrderByUpdatedAtDesc(ownerId))
                .thenReturn(List.of(sampleEntity()));

            List<ProjectDto> result = service.list(owner, null);

            assertEquals(1, result.size());
            verify(projectRepository, never())
                .findByCreatedByAndStatusOrderByUpdatedAtDesc(any(), any());
        }

        @Test
        @DisplayName("ARCHIVED filter routes to status-specific query")
        void list_statusFilter_queriesByOwnerAndStatus() {
            when(projectRepository.findByCreatedByAndStatusOrderByUpdatedAtDesc(ownerId, ProjectStatus.ARCHIVED))
                .thenReturn(List.of());

            service.list(owner, ProjectStatus.ARCHIVED);

            verify(projectRepository).findByCreatedByAndStatusOrderByUpdatedAtDesc(ownerId, ProjectStatus.ARCHIVED);
        }
    }

    @Nested
    @DisplayName("summary")
    class SummaryTests {

        @Test
        @DisplayName("returns counts map with pipelines entry only (Phase 1 scope)")
        void summary_returnsCountsStructure() {
            when(projectRepository.findById(projectId)).thenReturn(Optional.of(sampleEntity()));
            when(pipelineRepository.countByProjectId(projectId)).thenReturn(7L);

            ProjectSummaryDto summary = service.summary(projectId, owner);

            assertNotNull(summary.counts());
            assertEquals(7L, summary.counts().get("pipelines"));
            assertEquals(projectId, summary.id());
        }

        @Test
        @DisplayName("non-owner denied")
        void summary_nonOwner_throwsAccessDenied() {
            when(projectRepository.findById(projectId)).thenReturn(Optional.of(sampleEntity()));
            assertThrows(AccessDeniedException.class, () -> service.summary(projectId, other));
        }
    }
}
