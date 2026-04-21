package io.rdfforge.triplestore.repository;

import io.rdfforge.triplestore.entity.SavedQueryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SavedQueryRepository extends JpaRepository<SavedQueryEntity, UUID> {

    List<SavedQueryEntity> findByProjectIdOrderByNameAsc(UUID projectId);

    Optional<SavedQueryEntity> findByProjectIdAndName(UUID projectId, String name);

    boolean existsByProjectIdAndName(UUID projectId, String name);
}
