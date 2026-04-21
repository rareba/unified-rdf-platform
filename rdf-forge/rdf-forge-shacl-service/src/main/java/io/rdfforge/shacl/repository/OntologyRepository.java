package io.rdfforge.shacl.repository;

import io.rdfforge.shacl.entity.OntologyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface OntologyRepository extends JpaRepository<OntologyEntity, UUID> {

    List<OntologyEntity> findByProjectId(UUID projectId);

    Optional<OntologyEntity> findByProjectIdAndName(UUID projectId, String name);

    boolean existsByProjectIdAndName(UUID projectId, String name);
}
