package io.rdfforge.dimension.repository;

import io.rdfforge.dimension.entity.CubeEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface CubeRepository extends JpaRepository<CubeEntity, UUID> {

    Page<CubeEntity> findByProjectId(UUID projectId, Pageable pageable);

    @Query("SELECT c FROM CubeEntity c WHERE " +
           "(:projectId IS NULL OR c.projectId = :projectId) AND " +
           "(:search IS NULL OR LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(c.uri) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<CubeEntity> search(
        @Param("projectId") UUID projectId,
        @Param("search") String search,
        Pageable pageable
    );

    long countByProjectId(UUID projectId);
}
