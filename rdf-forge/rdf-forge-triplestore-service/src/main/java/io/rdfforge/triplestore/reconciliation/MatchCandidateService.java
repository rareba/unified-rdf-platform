package io.rdfforge.triplestore.reconciliation;

import io.rdfforge.common.exception.ResourceNotFoundException;
import io.rdfforge.common.security.AuthUser;
import io.rdfforge.triplestore.reconciliation.MatchCandidateDtos.ListFilter;
import io.rdfforge.triplestore.reconciliation.MatchCandidateDtos.ManualCandidateRequest;
import io.rdfforge.triplestore.reconciliation.MatchCandidateDtos.MatchCandidateDto;
import io.rdfforge.triplestore.reconciliation.MatchCandidateDtos.MatchStatsDto;
import io.rdfforge.triplestore.reconciliation.MatchCandidateDtos.SuggestRequest;
import io.rdfforge.triplestore.reconciliation.MatchCandidateDtos.SuggestResponse;
import io.rdfforge.triplestore.reconciliation.MatchCandidateEntity.MatchPredicate;
import io.rdfforge.triplestore.reconciliation.MatchCandidateEntity.MatchStatus;
import io.rdfforge.triplestore.reconciliation.MatchCandidateEntity.MatcherSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * Service for Phase 8 — reconciliation workflow. Matcher dispatch + persistence
 * + approve/reject audit trail.
 */
@Service
@Transactional
@Slf4j
public class MatchCandidateService {

    private final MatchCandidateRepository repository;
    private final MatcherRegistry registry;

    public MatchCandidateService(MatchCandidateRepository repository, MatcherRegistry registry) {
        this.repository = repository;
        this.registry = registry;
    }

    // ==================== Suggest / Match ====================

    /**
     * Run all enabled matchers (or the subset named in {@code matcherIds}) for
     * the given query. Dedupes against existing (project, source, target, predicate)
     * candidates — re-running for the same input does not create duplicates.
     */
    public SuggestResponse suggest(SuggestRequest request, AuthUser user) {
        requireAuthenticated(user);
        if (request.projectId() == null) throw new IllegalArgumentException("projectId required");
        if (request.sourceUri() == null || request.sourceUri().isBlank()) {
            throw new IllegalArgumentException("sourceUri required");
        }

        Matcher.MatchQuery q = new Matcher.MatchQuery(
            request.projectId(),
            request.sourceUri(),
            request.label(),
            request.types() == null ? Set.of() : request.types(),
            request.limit() == null ? 20 : request.limit(),
            request.triplestoreId(),
            request.graph()
        );

        List<Matcher> matchers = selectMatchers(request.matcherIds());
        List<MatchCandidateDto> persistedDtos = new ArrayList<>();
        int duplicatesSkipped = 0;

        for (Matcher m : matchers) {
            if (!m.enabled() || !m.supports(q)) continue;
            try {
                List<Matcher.MatchCandidate> results = m.match(q, user);
                for (Matcher.MatchCandidate candidate : results) {
                    Optional<MatchCandidateEntity> existing = repository
                        .findByProjectIdAndSourceUriAndTargetUriAndPredicate(
                            q.projectId(), candidate.sourceUri(), candidate.targetUri(), candidate.predicate());

                    if (existing.isPresent()) {
                        duplicatesSkipped++;
                        continue;
                    }

                    MatchCandidateEntity entity = toEntity(q.projectId(), candidate, user);
                    persistedDtos.add(MatchCandidateDto.from(repository.save(entity)));
                }
            } catch (Exception ex) {
                log.error("Matcher {} failed: {}", m.id(), ex.getMessage(), ex);
            }
        }

        return new SuggestResponse(persistedDtos.size(), duplicatesSkipped, persistedDtos);
    }

    private List<Matcher> selectMatchers(Set<String> matcherIds) {
        List<Matcher> all = registry.getEnabled();
        if (matcherIds == null || matcherIds.isEmpty()) return all;
        return all.stream().filter(m -> matcherIds.contains(m.id())).toList();
    }

    private MatchCandidateEntity toEntity(UUID projectId, Matcher.MatchCandidate c, AuthUser user) {
        MatchCandidateEntity entity = new MatchCandidateEntity();
        entity.setProjectId(projectId);
        entity.setSourceUri(c.sourceUri());
        entity.setTargetUri(c.targetUri());
        entity.setPredicate(c.predicate());
        entity.setConfidence(c.confidence());
        entity.setSource(c.source() == null ? MatcherSource.LOCAL_DUPLICATE : c.source());
        entity.setMatcherName(c.matcherName() == null ? "unknown" : c.matcherName());
        entity.setStatus(MatchStatus.PENDING);
        entity.setEvidence(c.evidence());
        entity.setCreatedBy(user.id());
        return entity;
    }

    // ==================== CRUD / decisions ====================

    @Transactional(readOnly = true)
    public List<MatchCandidateDto> list(UUID projectId, ListFilter filter, AuthUser user) {
        requireAuthenticated(user);
        List<MatchCandidateEntity> all = repository.findByProjectIdOrderByCreatedAtDesc(projectId);
        return all.stream()
            .filter(e -> filter.status() == null || filter.status() == e.getStatus())
            .filter(e -> filter.predicate() == null || filter.predicate() == e.getPredicate())
            .filter(e -> filter.matcher() == null || filter.matcher().equals(e.getMatcherName()))
            .filter(e -> matchesSearch(e, filter.search()))
            .map(MatchCandidateDto::from)
            .toList();
    }

    @Transactional(readOnly = true)
    public MatchCandidateDto get(UUID id, AuthUser user) {
        requireAuthenticated(user);
        return MatchCandidateDto.from(load(id));
    }

    public MatchCandidateDto approve(UUID id, AuthUser user) {
        requireAuthenticated(user);
        MatchCandidateEntity entity = load(id);
        requireDecisionAllowed(entity, user);
        entity.setStatus(MatchStatus.APPROVED);
        entity.setApprovedBy(user.id());
        entity.setDecidedAt(Instant.now());
        log.info("AUDIT approve match-candidate id={} user={} predicate={}",
                 entity.getId(), user.id(), entity.getPredicate());
        // TODO Phase 8 follow-up: optionally write approved triple to a configured "links" graph
        // via TriplestoreService.executeUpdate — gated by a per-project setting.
        return MatchCandidateDto.from(repository.save(entity));
    }

    public MatchCandidateDto reject(UUID id, AuthUser user) {
        requireAuthenticated(user);
        MatchCandidateEntity entity = load(id);
        requireDecisionAllowed(entity, user);
        entity.setStatus(MatchStatus.REJECTED);
        entity.setApprovedBy(user.id());
        entity.setDecidedAt(Instant.now());
        log.info("AUDIT reject match-candidate id={} user={}", entity.getId(), user.id());
        return MatchCandidateDto.from(repository.save(entity));
    }

    public MatchCandidateDto manual(ManualCandidateRequest request, AuthUser user) {
        requireAuthenticated(user);
        if (request.projectId() == null) throw new IllegalArgumentException("projectId required");
        if (request.sourceUri() == null || request.sourceUri().isBlank()) {
            throw new IllegalArgumentException("sourceUri required");
        }
        if (request.targetUri() == null || request.targetUri().isBlank()) {
            throw new IllegalArgumentException("targetUri required");
        }
        MatchPredicate pred = request.predicate() == null ? MatchPredicate.SAME_AS : request.predicate();

        // Dedupe — if one already exists for this tuple, just return it.
        Optional<MatchCandidateEntity> existing = repository
            .findByProjectIdAndSourceUriAndTargetUriAndPredicate(
                request.projectId(), request.sourceUri(), request.targetUri(), pred);
        if (existing.isPresent()) {
            MatchCandidateEntity e = existing.get();
            if (e.getStatus() == MatchStatus.PENDING) {
                e.setStatus(MatchStatus.APPROVED);
                e.setApprovedBy(user.id());
                e.setDecidedAt(Instant.now());
                repository.save(e);
            }
            return MatchCandidateDto.from(e);
        }

        MatchCandidateEntity entity = new MatchCandidateEntity();
        entity.setProjectId(request.projectId());
        entity.setSourceUri(request.sourceUri());
        entity.setTargetUri(request.targetUri());
        entity.setPredicate(pred);
        entity.setConfidence(request.confidence() == null ? 1.0 : request.confidence());
        entity.setSource(MatcherSource.MANUAL);
        entity.setMatcherName("manual");
        entity.setStatus(MatchStatus.APPROVED);
        entity.setEvidence(request.evidence());
        entity.setCreatedBy(user.id());
        entity.setApprovedBy(user.id());
        entity.setDecidedAt(Instant.now());
        log.info("AUDIT manual-match-create source={} target={} user={}",
                 request.sourceUri(), request.targetUri(), user.id());
        return MatchCandidateDto.from(repository.save(entity));
    }

    @Transactional(readOnly = true)
    public MatchStatsDto stats(UUID projectId, AuthUser user) {
        requireAuthenticated(user);
        long pending  = repository.countByProjectIdAndStatus(projectId, MatchStatus.PENDING);
        long approved = repository.countByProjectIdAndStatus(projectId, MatchStatus.APPROVED);
        long rejected = repository.countByProjectIdAndStatus(projectId, MatchStatus.REJECTED);
        long archived = repository.countByProjectIdAndStatus(projectId, MatchStatus.ARCHIVED);

        // Compute distributions in code — cheaper than N queries for dashboard view.
        List<MatchCandidateEntity> all = repository.findByProjectIdOrderByCreatedAtDesc(projectId);
        Map<String, Long> byPredicate = new HashMap<>();
        Map<String, Long> byMatcher = new HashMap<>();
        for (MatchCandidateEntity e : all) {
            byPredicate.merge(e.getPredicate().name(), 1L, Long::sum);
            byMatcher.merge(e.getMatcherName(), 1L, Long::sum);
        }
        return new MatchStatsDto(projectId, pending, approved, rejected, archived, byPredicate, byMatcher);
    }

    public List<MatcherRegistry.MatcherInfo> listMatchers(AuthUser user) {
        requireAuthenticated(user);
        return registry.describe();
    }

    // ==================== Internal ====================

    private MatchCandidateEntity load(UUID id) {
        return repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("MatchCandidate", id.toString()));
    }

    private void requireAuthenticated(AuthUser user) {
        if (user == null || user.isAnonymous()) {
            throw new AccessDeniedException("Authentication required");
        }
    }

    /**
     * Decision-making requires ownership: creator or admin. For suggested
     * candidates {@code createdBy} is the user that invoked suggest(); admin
     * always bypasses.
     */
    private void requireDecisionAllowed(MatchCandidateEntity entity, AuthUser user) {
        if (user.isAdmin()) return;
        UUID owner = entity.getCreatedBy();
        if (owner != null && owner.equals(user.id())) return;
        throw new AccessDeniedException("Not authorized to decide this match candidate");
    }

    private boolean matchesSearch(MatchCandidateEntity e, String search) {
        if (search == null || search.isBlank()) return true;
        String q = search.toLowerCase(Locale.ROOT);
        return (e.getSourceUri() != null && e.getSourceUri().toLowerCase(Locale.ROOT).contains(q))
            || (e.getTargetUri() != null && e.getTargetUri().toLowerCase(Locale.ROOT).contains(q));
    }
}
