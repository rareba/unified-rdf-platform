package io.rdfforge.shacl.validation;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ValidationIssueRepository extends JpaRepository<ValidationIssueEntity, UUID> {

    List<ValidationIssueEntity> findByRunId(UUID runId, Pageable pageable);

    List<ValidationIssueEntity> findByRunIdAndSeverity(UUID runId, ValidationSeverity severity, Pageable pageable);

    void deleteByRunId(UUID runId);
}
