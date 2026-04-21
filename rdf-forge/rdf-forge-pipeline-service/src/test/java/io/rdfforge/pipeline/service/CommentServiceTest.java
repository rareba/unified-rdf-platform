package io.rdfforge.pipeline.service;

import io.rdfforge.common.exception.RdfForgeException;
import io.rdfforge.common.exception.ResourceNotFoundException;
import io.rdfforge.common.security.AuthUser;
import io.rdfforge.pipeline.dto.CommentCreateRequest;
import io.rdfforge.pipeline.dto.CommentDto;
import io.rdfforge.pipeline.dto.CommentUpdateRequest;
import io.rdfforge.pipeline.entity.CommentEntity;
import io.rdfforge.pipeline.entity.CommentEntity.AssetKind;
import io.rdfforge.pipeline.entity.MappingEntity;
import io.rdfforge.pipeline.entity.ProjectEntity;
import io.rdfforge.pipeline.entity.ProjectStatus;
import io.rdfforge.pipeline.repository.CommentRepository;
import io.rdfforge.pipeline.repository.MappingRepository;
import io.rdfforge.pipeline.repository.ProjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link CommentService} — exercises the project-level
 * authorization boundary, author-only mutation rules, and asset/project
 * consistency checks. All database access is mocked; we are asserting
 * behaviour at the service seam.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CommentService Tests")
class CommentServiceTest {

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private MappingRepository mappingRepository;

    private CommentService service;

    private UUID ownerId;
    private UUID otherUserId;
    private UUID projectId;
    private UUID otherProjectId;
    private UUID mappingId;
    private UUID commentId;

    private AuthUser owner;
    private AuthUser other;
    private AuthUser admin;

    @BeforeEach
    void setUp() {
        service = new CommentService(commentRepository, projectRepository, mappingRepository);
        ownerId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        otherProjectId = UUID.randomUUID();
        mappingId = UUID.randomUUID();
        commentId = UUID.randomUUID();

        owner = new AuthUser(ownerId, "owner@example.org", Set.of("USER"));
        other = new AuthUser(otherUserId, "other@example.org", Set.of("USER"));
        admin = new AuthUser(UUID.randomUUID(), "admin@example.org", Set.of("ADMIN"));
    }

    private ProjectEntity projectOwnedBy(UUID creator) {
        return ProjectEntity.builder()
                .id(projectId)
                .name("P")
                .baseUri("https://example.org/")
                .status(ProjectStatus.ACTIVE)
                .createdBy(creator)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private MappingEntity mappingInProject(UUID owningProject) {
        return MappingEntity.builder()
                .id(mappingId)
                .projectId(owningProject)
                .name("m")
                .createdBy(ownerId)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    private CommentEntity existingComment(UUID authorId, UUID inProject) {
        return CommentEntity.builder()
                .id(commentId)
                .projectId(inProject)
                .assetKind(AssetKind.MAPPING)
                .assetId(mappingId)
                .body("hello")
                .authorId(authorId)
                .authorEmail("a@x.org")
                .createdAt(Instant.now())
                .deleted(false)
                .build();
    }

    // ---------------------------------------------------------------
    // list
    // ---------------------------------------------------------------

    @Test
    @DisplayName("list: same-project owner receives their comments")
    void list_sameProjectOwner_returnsComments() {
        when(projectRepository.findById(projectId))
                .thenReturn(Optional.of(projectOwnedBy(ownerId)));
        when(commentRepository.findByAssetKindAndAssetIdAndDeletedFalseOrderByCreatedAtAsc(
                AssetKind.MAPPING, mappingId))
                .thenReturn(List.of(existingComment(ownerId, projectId)));

        List<CommentDto> result = service.list(projectId, AssetKind.MAPPING, mappingId, owner);

        assertEquals(1, result.size());
        assertEquals(projectId, result.get(0).projectId());
    }

    @Test
    @DisplayName("list: non-owner on someone else's project is denied")
    void list_crossProject_denied() {
        when(projectRepository.findById(projectId))
                .thenReturn(Optional.of(projectOwnedBy(ownerId)));

        assertThrows(AccessDeniedException.class,
                () -> service.list(projectId, AssetKind.MAPPING, mappingId, other));
        verify(commentRepository, never())
                .findByAssetKindAndAssetIdAndDeletedFalseOrderByCreatedAtAsc(any(), any());
    }

    @Test
    @DisplayName("list: filters out comments from other projects even if asset id matches")
    void list_filtersByDeclaredProject() {
        when(projectRepository.findById(projectId))
                .thenReturn(Optional.of(projectOwnedBy(ownerId)));
        // Repository returns a stray comment whose projectId does NOT match
        CommentEntity stray = existingComment(ownerId, otherProjectId);
        when(commentRepository.findByAssetKindAndAssetIdAndDeletedFalseOrderByCreatedAtAsc(
                AssetKind.MAPPING, mappingId))
                .thenReturn(List.of(stray, existingComment(ownerId, projectId)));

        List<CommentDto> result = service.list(projectId, AssetKind.MAPPING, mappingId, owner);

        assertEquals(1, result.size());
        assertEquals(projectId, result.get(0).projectId());
    }

    // ---------------------------------------------------------------
    // create
    // ---------------------------------------------------------------

    @Test
    @DisplayName("create: owner posting on own project + own mapping succeeds")
    void create_sameProject_succeeds() {
        CommentCreateRequest req = new CommentCreateRequest(
                projectId, AssetKind.MAPPING, mappingId, "body", null);
        when(projectRepository.findById(projectId))
                .thenReturn(Optional.of(projectOwnedBy(ownerId)));
        when(mappingRepository.findById(mappingId))
                .thenReturn(Optional.of(mappingInProject(projectId)));
        when(commentRepository.save(any(CommentEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        CommentDto result = service.create(req, owner);

        assertEquals(projectId, result.projectId());
        assertEquals(ownerId, result.authorId());
    }

    @Test
    @DisplayName("create: non-owner posting into someone else's project is denied")
    void create_crossProject_denied() {
        CommentCreateRequest req = new CommentCreateRequest(
                projectId, AssetKind.MAPPING, mappingId, "body", null);
        when(projectRepository.findById(projectId))
                .thenReturn(Optional.of(projectOwnedBy(ownerId)));

        assertThrows(AccessDeniedException.class, () -> service.create(req, other));
        verify(commentRepository, never()).save(any());
    }

    @Test
    @DisplayName("create: MAPPING asset belongs to a different project → rejected")
    void create_mappingAssetInDifferentProject_rejected() {
        CommentCreateRequest req = new CommentCreateRequest(
                projectId, AssetKind.MAPPING, mappingId, "body", null);
        when(projectRepository.findById(projectId))
                .thenReturn(Optional.of(projectOwnedBy(ownerId)));
        // Mapping belongs to a DIFFERENT project than the request declares
        when(mappingRepository.findById(mappingId))
                .thenReturn(Optional.of(mappingInProject(otherProjectId)));

        RdfForgeException ex = assertThrows(RdfForgeException.class,
                () -> service.create(req, owner));
        assertEquals("COMMENT_ASSET_PROJECT_MISMATCH", ex.getErrorCode());
        verify(commentRepository, never()).save(any());
    }

    @Test
    @DisplayName("create: admin can post into any project (bypass owner check)")
    void create_admin_canPostToAnyProject() {
        CommentCreateRequest req = new CommentCreateRequest(
                projectId, AssetKind.MAPPING, mappingId, "body", null);
        when(projectRepository.findById(projectId))
                .thenReturn(Optional.of(projectOwnedBy(ownerId))); // admin is NOT the owner
        when(mappingRepository.findById(mappingId))
                .thenReturn(Optional.of(mappingInProject(projectId)));
        when(commentRepository.save(any(CommentEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        CommentDto result = service.create(req, admin);

        ArgumentCaptor<CommentEntity> captor = ArgumentCaptor.forClass(CommentEntity.class);
        verify(commentRepository).save(captor.capture());
        assertEquals(admin.id(), captor.getValue().getAuthorId());
        assertEquals(projectId, result.projectId());
    }

    @Test
    @DisplayName("create: PROJECT comment where assetId != projectId is rejected")
    void create_projectAssetKind_mismatchedId_rejected() {
        CommentCreateRequest req = new CommentCreateRequest(
                projectId, AssetKind.PROJECT, UUID.randomUUID(), "body", null);
        when(projectRepository.findById(projectId))
                .thenReturn(Optional.of(projectOwnedBy(ownerId)));

        RdfForgeException ex = assertThrows(RdfForgeException.class,
                () -> service.create(req, owner));
        assertEquals("COMMENT_ASSET_PROJECT_MISMATCH", ex.getErrorCode());
    }

    @Test
    @DisplayName("create: cross-service asset kind (SHAPE) skips existence check but still requires project ownership")
    void create_crossServiceAssetKind_skipsExistenceButEnforcesProject() {
        UUID shapeId = UUID.randomUUID();
        CommentCreateRequest req = new CommentCreateRequest(
                projectId, AssetKind.SHAPE, shapeId, "body", null);
        when(projectRepository.findById(projectId))
                .thenReturn(Optional.of(projectOwnedBy(ownerId)));
        when(commentRepository.save(any(CommentEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        CommentDto result = service.create(req, owner);

        assertEquals(shapeId, result.assetId());
        verify(mappingRepository, never()).findById(any());
    }

    // ---------------------------------------------------------------
    // update
    // ---------------------------------------------------------------

    @Test
    @DisplayName("update: author of comment is allowed")
    void update_author_allowed() {
        CommentEntity e = existingComment(ownerId, projectId);
        when(commentRepository.findById(commentId)).thenReturn(Optional.of(e));
        when(commentRepository.save(any(CommentEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        CommentDto result = service.update(commentId, new CommentUpdateRequest("updated"), owner);

        assertEquals("updated", result.body());
    }

    @Test
    @DisplayName("update: non-author non-admin is denied")
    void update_nonAuthor_denied() {
        CommentEntity e = existingComment(ownerId, projectId);
        when(commentRepository.findById(commentId)).thenReturn(Optional.of(e));

        assertThrows(AccessDeniedException.class,
                () -> service.update(commentId, new CommentUpdateRequest("x"), other));
        verify(commentRepository, never()).save(any());
    }

    @Test
    @DisplayName("update: admin may edit any comment")
    void update_admin_allowed() {
        CommentEntity e = existingComment(ownerId, projectId);
        when(commentRepository.findById(commentId)).thenReturn(Optional.of(e));
        when(commentRepository.save(any(CommentEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        CommentDto result = service.update(commentId, new CommentUpdateRequest("mod"), admin);

        assertEquals("mod", result.body());
    }

    // ---------------------------------------------------------------
    // delete
    // ---------------------------------------------------------------

    @Test
    @DisplayName("delete: author may soft-delete their comment")
    void delete_author_allowed() {
        CommentEntity e = existingComment(ownerId, projectId);
        when(commentRepository.findById(commentId)).thenReturn(Optional.of(e));
        when(commentRepository.save(any(CommentEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.delete(commentId, owner);

        assertTrue(e.isDeleted(), "comment must be flagged deleted");
    }

    @Test
    @DisplayName("delete: non-author non-admin is denied")
    void delete_nonAuthor_denied() {
        CommentEntity e = existingComment(ownerId, projectId);
        when(commentRepository.findById(commentId)).thenReturn(Optional.of(e));

        assertThrows(AccessDeniedException.class, () -> service.delete(commentId, other));
        verify(commentRepository, never()).save(any());
    }

    @Test
    @DisplayName("delete: admin may soft-delete any comment")
    void delete_admin_allowed() {
        CommentEntity e = existingComment(ownerId, projectId);
        when(commentRepository.findById(commentId)).thenReturn(Optional.of(e));
        when(commentRepository.save(any(CommentEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        service.delete(commentId, admin);

        assertTrue(e.isDeleted());
    }

    // ---------------------------------------------------------------
    // anonymous user guard
    // ---------------------------------------------------------------

    @Test
    @DisplayName("anonymous caller: list is denied without hitting repos")
    void list_anonymous_denied() {
        assertThrows(AccessDeniedException.class,
                () -> service.list(projectId, AssetKind.MAPPING, mappingId, AuthUser.anonymous()));
        verify(projectRepository, never()).findById(any());
    }

    @Test
    @DisplayName("missing project → AccessDeniedException (no existence leak)")
    void list_missingProject_deniedNotNotFound() {
        when(projectRepository.findById(projectId)).thenReturn(Optional.empty());

        assertThrows(AccessDeniedException.class,
                () -> service.list(projectId, AssetKind.MAPPING, mappingId, owner));
    }

    @Test
    @DisplayName("MAPPING asset missing → ResourceNotFoundException on create")
    void create_mappingNotFound_throwsNotFound() {
        CommentCreateRequest req = new CommentCreateRequest(
                projectId, AssetKind.MAPPING, mappingId, "body", null);
        when(projectRepository.findById(projectId))
                .thenReturn(Optional.of(projectOwnedBy(ownerId)));
        when(mappingRepository.findById(mappingId)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.create(req, owner));
    }
}
