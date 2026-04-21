package io.rdfforge.shacl.validation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ValidationSuiteRepository extends JpaRepository<ValidationSuiteEntity, UUID> {

    List<ValidationSuiteEntity> findByProjectIdOrderByNameAsc(UUID projectId);

    Optional<ValidationSuiteEntity> findByProjectIdAndName(UUID projectId, String name);

    boolean existsByProjectIdAndName(UUID projectId, String name);
}
