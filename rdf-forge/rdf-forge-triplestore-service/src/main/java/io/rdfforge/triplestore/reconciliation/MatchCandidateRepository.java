package io.rdfforge.triplestore.reconciliation;

import io.rdfforge.triplestore.reconciliation.MatchCandidateEntity.MatchPredicate;
import io.rdfforge.triplestore.reconciliation.MatchCandidateEntity.MatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MatchCandidateRepository extends JpaRepository<MatchCandidateEntity, UUID> {

    List<MatchCandidateEntity> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

    Optional<MatchCandidateEntity> findByProjectIdAndSourceUriAndTargetUriAndPredicate(
        UUID projectId, String sourceUri, String targetUri, MatchPredicate predicate);

    long countByProjectIdAndStatus(UUID projectId, MatchStatus status);

    List<MatchCandidateEntity> findByProjectIdAndStatus(UUID projectId, MatchStatus status);
}
