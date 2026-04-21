package io.rdfforge.pipeline.repository;

import io.rdfforge.pipeline.entity.MappingEntity;
import io.rdfforge.pipeline.entity.MappingType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * JPA repository for {@link MappingEntity}.
 *
 * <p>Queries are project-scoped — authorization is checked at the service
 * layer against the project's owner, so the repository stays oblivious to
 * auth concerns.
 */
@Repository
public interface MappingRepository extends JpaRepository<MappingEntity, UUID> {

    /** Mappings belonging to a project, newest activity first. */
    List<MappingEntity> findByProjectIdOrderByUpdatedAtDesc(UUID projectId);

    /** Same, filtered by mapping type for the cube/skos/generic UI buckets. */
    List<MappingEntity> findByProjectIdAndMappingTypeOrderByUpdatedAtDesc(UUID projectId, MappingType mappingType);

    /** Unique-name guard before insert — matches the DB constraint. */
    boolean existsByProjectIdAndName(UUID projectId, String name);

    /** Used when renaming — checks the new name is free in the same project. */
    boolean existsByProjectIdAndNameAndIdNot(UUID projectId, String name, UUID id);
}
