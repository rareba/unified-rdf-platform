package io.rdfforge.shacl.validation;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ValidationRunRepository extends JpaRepository<ValidationRunEntity, UUID> {

    List<ValidationRunEntity> findBySuiteIdOrderByRanAtDesc(UUID suiteId, Pageable pageable);

    List<ValidationRunEntity> findByProjectIdOrderByRanAtDesc(UUID projectId, Pageable pageable);

    /** Latest run for a given suite — used by health widgets. */
    Optional<ValidationRunEntity> findTop1BySuiteIdOrderByRanAtDesc(UUID suiteId);
}
