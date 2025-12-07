package io.rdfforge.shacl.repository;

import io.rdfforge.shacl.entity.ShapeEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ShapeRepository extends JpaRepository<ShapeEntity, UUID> {
    Page<ShapeEntity> findByProjectId(UUID projectId, Pageable pageable);

    @Query("SELECT s FROM ShapeEntity s WHERE (:projectId IS NULL OR s.projectId = :projectId)")
    Page<ShapeEntity> findAllByOptionalProjectId(@Param("projectId") UUID projectId, Pageable pageable);

    List<ShapeEntity> findByIsTemplateTrue();

    Optional<ShapeEntity> findByProjectIdAndUri(UUID projectId, String uri);

    List<ShapeEntity> findByCategory(String category);

    @Query("SELECT s FROM ShapeEntity s WHERE (:projectId IS NULL OR s.projectId = :projectId) AND " +
           "(LOWER(s.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(s.description) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(s.targetClass) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<ShapeEntity> searchByOptionalProjectId(@Param("projectId") UUID projectId,
                                                 @Param("search") String search,
                                                 Pageable pageable);

    @Query("SELECT DISTINCT s.category FROM ShapeEntity s WHERE (:projectId IS NULL OR s.projectId = :projectId) AND s.category IS NOT NULL")
    List<String> findCategoriesByOptionalProjectId(@Param("projectId") UUID projectId);
}
