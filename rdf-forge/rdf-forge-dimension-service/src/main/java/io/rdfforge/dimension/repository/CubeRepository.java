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

    @Query(value = "SELECT * FROM cubes c WHERE " +
           "(CAST(:projectId AS UUID) IS NULL OR c.project_id = CAST(:projectId AS UUID)) AND " +
           "(CAST(:search AS TEXT) IS NULL OR LOWER(c.name) LIKE LOWER('%' || CAST(:search AS TEXT) || '%') OR " +
           "LOWER(c.uri) LIKE LOWER('%' || CAST(:search AS TEXT) || '%'))",
           countQuery = "SELECT COUNT(*) FROM cubes c WHERE " +
           "(CAST(:projectId AS UUID) IS NULL OR c.project_id = CAST(:projectId AS UUID)) AND " +
           "(CAST(:search AS TEXT) IS NULL OR LOWER(c.name) LIKE LOWER('%' || CAST(:search AS TEXT) || '%') OR " +
           "LOWER(c.uri) LIKE LOWER('%' || CAST(:search AS TEXT) || '%'))",
           nativeQuery = true)
    Page<CubeEntity> search(
        @Param("projectId") UUID projectId,
        @Param("search") String search,
        Pageable pageable
    );

    long countByProjectId(UUID projectId);
}
