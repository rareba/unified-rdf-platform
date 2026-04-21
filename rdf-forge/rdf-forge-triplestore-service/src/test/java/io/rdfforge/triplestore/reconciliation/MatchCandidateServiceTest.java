package io.rdfforge.triplestore.reconciliation;

import io.rdfforge.common.security.AuthUser;
import io.rdfforge.triplestore.reconciliation.MatchCandidateDtos.ListFilter;
import io.rdfforge.triplestore.reconciliation.MatchCandidateDtos.ManualCandidateRequest;
import io.rdfforge.triplestore.reconciliation.MatchCandidateDtos.MatchCandidateDto;
import io.rdfforge.triplestore.reconciliation.MatchCandidateDtos.SuggestRequest;
import io.rdfforge.triplestore.reconciliation.MatchCandidateDtos.SuggestResponse;
import io.rdfforge.triplestore.reconciliation.MatchCandidateEntity.MatchPredicate;
import io.rdfforge.triplestore.reconciliation.MatchCandidateEntity.MatchStatus;
import io.rdfforge.triplestore.reconciliation.MatchCandidateEntity.MatcherSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("MatchCandidateService — Phase 8")
class MatchCandidateServiceTest {

    @Mock private MatchCandidateRepository repository;
    @Mock private MatcherRegistry registry;

    private MatchCandidateService service;

    private UUID projectId;
    private UUID userId;
    private AuthUser user;
    private AuthUser admin;

    @BeforeEach
    void setUp() {
        service = new MatchCandidateService(repository, registry);
        projectId = UUID.randomUUID();
        userId = UUID.randomUUID();
        user = new AuthUser(userId, "u@example.com", Set.of("USER"));
        admin = new AuthUser(UUID.randomUUID(), "a@example.com", Set.of("ADMIN"));
    }

    // ==================== suggest ====================

    @Test
    @DisplayName("suggest invokes enabled matchers and persists new candidates")
    void suggest_invokesMatchers_persistsCandidates() {
        Matcher matcher = mock(Matcher.class);
        org.mockito.Mockito.lenient().when(matcher.id()).thenReturn("stub");
        when(matcher.enabled()).thenReturn(true);
        when(matcher.supports(any())).thenReturn(true);
        when(matcher.match(any(), any())).thenReturn(List.of(
            new Matcher.MatchCandidate("src", "tgt", MatchPredicate.SAME_AS, 0.9,
                MatcherSource.LOCAL_DUPLICATE, "stub", Map.of())
        ));
        when(registry.getEnabled()).thenReturn(List.of(matcher));
        when(repository.findByProjectIdAndSourceUriAndTargetUriAndPredicate(
                eq(projectId), eq("src"), eq("tgt"), eq(MatchPredicate.SAME_AS)))
            .thenReturn(Optional.empty());
        when(repository.save(any(MatchCandidateEntity.class))).thenAnswer(inv -> {
            MatchCandidateEntity e = inv.getArgument(0);
            e.setId(UUID.randomUUID());
            return e;
        });

        SuggestRequest req = new SuggestRequest(projectId, "src", "Label",
            Set.of(), 10, null, null, Set.of());
        SuggestResponse resp = service.suggest(req, user);

        assertEquals(1, resp.persisted());
        assertEquals(0, resp.duplicatesSkipped());
        verify(repository).save(any(MatchCandidateEntity.class));
    }

    @Test
    @DisplayName("suggest skips duplicates on re-run")
    void suggest_dedupes() {
        Matcher matcher = mock(Matcher.class);
        when(matcher.enabled()).thenReturn(true);
        when(matcher.supports(any())).thenReturn(true);
        when(matcher.match(any(), any())).thenReturn(List.of(
            new Matcher.MatchCandidate("src", "tgt", MatchPredicate.SAME_AS, 0.9,
                MatcherSource.LOCAL_DUPLICATE, "stub", Map.of())
        ));
        when(registry.getEnabled()).thenReturn(List.of(matcher));

        MatchCandidateEntity existing = new MatchCandidateEntity();
        existing.setId(UUID.randomUUID());
        when(repository.findByProjectIdAndSourceUriAndTargetUriAndPredicate(
                eq(projectId), eq("src"), eq("tgt"), eq(MatchPredicate.SAME_AS)))
            .thenReturn(Optional.of(existing));

        SuggestRequest req = new SuggestRequest(projectId, "src", "Label",
            Set.of(), 10, null, null, Set.of());
        SuggestResponse resp = service.suggest(req, user);

        assertEquals(0, resp.persisted());
        assertEquals(1, resp.duplicatesSkipped());
        verify(repository, never()).save(any(MatchCandidateEntity.class));
    }

    @Test
    void suggest_anonymous_denied() {
        SuggestRequest req = new SuggestRequest(projectId, "src", "x",
            Set.of(), 1, null, null, Set.of());
        assertThrows(AccessDeniedException.class, () -> service.suggest(req, AuthUser.anonymous()));
    }

    @Test
    void suggest_requiresProjectIdAndSourceUri() {
        assertThrows(IllegalArgumentException.class, () -> service.suggest(
            new SuggestRequest(null, "src", "x", Set.of(), 1, null, null, Set.of()), user));
        assertThrows(IllegalArgumentException.class, () -> service.suggest(
            new SuggestRequest(projectId, "", "x", Set.of(), 1, null, null, Set.of()), user));
    }

    // ==================== approve / reject ====================

    @Test
    void approve_byOwner_setsApprovedAndDecidedAt() {
        MatchCandidateEntity entity = newPending(userId);
        when(repository.findById(entity.getId())).thenReturn(Optional.of(entity));
        when(repository.save(any(MatchCandidateEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        MatchCandidateDto dto = service.approve(entity.getId(), user);

        assertEquals(MatchStatus.APPROVED, dto.status());
        assertNotNull(dto.decidedAt());
        assertEquals(userId, dto.approvedBy());
    }

    @Test
    void reject_byOwner_setsRejected() {
        MatchCandidateEntity entity = newPending(userId);
        when(repository.findById(entity.getId())).thenReturn(Optional.of(entity));
        when(repository.save(any(MatchCandidateEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        MatchCandidateDto dto = service.reject(entity.getId(), user);

        assertEquals(MatchStatus.REJECTED, dto.status());
        assertEquals(userId, dto.approvedBy());
    }

    @Test
    void approve_byNonOwner_denied() {
        MatchCandidateEntity entity = newPending(UUID.randomUUID());
        when(repository.findById(entity.getId())).thenReturn(Optional.of(entity));
        assertThrows(AccessDeniedException.class, () -> service.approve(entity.getId(), user));
    }

    @Test
    void approve_byAdmin_allowed() {
        MatchCandidateEntity entity = newPending(UUID.randomUUID());
        when(repository.findById(entity.getId())).thenReturn(Optional.of(entity));
        when(repository.save(any(MatchCandidateEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        MatchCandidateDto dto = service.approve(entity.getId(), admin);
        assertEquals(MatchStatus.APPROVED, dto.status());
    }

    // ==================== manual ====================

    @Test
    void manual_createsDirectlyApproved() {
        when(repository.findByProjectIdAndSourceUriAndTargetUriAndPredicate(
                eq(projectId), anyString(), anyString(), any())).thenReturn(Optional.empty());
        when(repository.save(any(MatchCandidateEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ManualCandidateRequest req = new ManualCandidateRequest(
            projectId, "src", "tgt", MatchPredicate.SAME_AS, 1.0, Map.of());
        MatchCandidateDto dto = service.manual(req, user);

        assertEquals(MatchStatus.APPROVED, dto.status());
        assertEquals(MatcherSource.MANUAL, dto.source());
        assertEquals(userId, dto.createdBy());
        assertEquals(userId, dto.approvedBy());
    }

    @Test
    void manual_onExistingPending_upgradesToApproved() {
        MatchCandidateEntity existing = newPending(userId);
        when(repository.findByProjectIdAndSourceUriAndTargetUriAndPredicate(
                eq(projectId), eq("src"), eq("tgt"), eq(MatchPredicate.SAME_AS)))
            .thenReturn(Optional.of(existing));
        when(repository.save(any(MatchCandidateEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        MatchCandidateDto dto = service.manual(
            new ManualCandidateRequest(projectId, "src", "tgt", MatchPredicate.SAME_AS, null, null), user);

        assertEquals(MatchStatus.APPROVED, dto.status());
    }

    // ==================== list / stats ====================

    @Test
    void list_filtersByStatus() {
        MatchCandidateEntity pending = newPending(userId);
        MatchCandidateEntity approved = newPending(userId);
        approved.setStatus(MatchStatus.APPROVED);
        when(repository.findByProjectIdOrderByCreatedAtDesc(projectId))
            .thenReturn(List.of(pending, approved));

        List<MatchCandidateDto> onlyPending = service.list(projectId,
            ListFilter.of(MatchStatus.PENDING, null, null, null), user);
        assertEquals(1, onlyPending.size());
        assertEquals(MatchStatus.PENDING, onlyPending.get(0).status());
    }

    @Test
    void stats_countsStatuses() {
        when(repository.countByProjectIdAndStatus(projectId, MatchStatus.PENDING)).thenReturn(3L);
        when(repository.countByProjectIdAndStatus(projectId, MatchStatus.APPROVED)).thenReturn(1L);
        when(repository.countByProjectIdAndStatus(projectId, MatchStatus.REJECTED)).thenReturn(0L);
        when(repository.countByProjectIdAndStatus(projectId, MatchStatus.ARCHIVED)).thenReturn(0L);
        when(repository.findByProjectIdOrderByCreatedAtDesc(projectId)).thenReturn(List.of());

        var stats = service.stats(projectId, user);
        assertEquals(3L, stats.pending());
        assertEquals(1L, stats.approved());
    }

    @Test
    void listMatchers_delegatesToRegistry() {
        when(registry.describe()).thenReturn(List.of(
            new MatcherRegistry.MatcherInfo("stub", "Stub", true)
        ));
        var infos = service.listMatchers(user);
        assertEquals(1, infos.size());
        assertEquals("stub", infos.get(0).id());
    }

    private MatchCandidateEntity newPending(UUID owner) {
        MatchCandidateEntity e = new MatchCandidateEntity();
        e.setId(UUID.randomUUID());
        e.setProjectId(projectId);
        e.setSourceUri("src");
        e.setTargetUri("tgt");
        e.setPredicate(MatchPredicate.SAME_AS);
        e.setConfidence(0.9);
        e.setSource(MatcherSource.LOCAL_DUPLICATE);
        e.setMatcherName("stub");
        e.setStatus(MatchStatus.PENDING);
        e.setCreatedBy(owner);
        return e;
    }
}
