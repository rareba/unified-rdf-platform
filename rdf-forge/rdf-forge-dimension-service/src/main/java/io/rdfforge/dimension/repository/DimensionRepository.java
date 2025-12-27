package io.rdfforge.dimension.repository;

import io.rdfforge.dimension.entity.DimensionEntity;
import io.rdfforge.dimension.entity.DimensionEntity.DimensionType;
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
public interface DimensionRepository extends JpaRepository<DimensionEntity, UUID> {

    Optional<DimensionEntity> findByProjectIdAndUri(UUID projectId, String uri);

    List<DimensionEntity> findByProjectId(UUID projectId);

    Page<DimensionEntity> findByProjectId(UUID projectId, Pageable pageable);

    List<DimensionEntity> findByProjectIdAndType(UUID projectId, DimensionType type);

    List<DimensionEntity> findByIsSharedTrue();

    Page<DimensionEntity> findByIsSharedTrue(Pageable pageable);

    @Query(value = "SELECT * FROM dimensions d WHERE d.project_id = :projectId " +
           "AND (:type IS NULL OR d.type = CAST(:type AS VARCHAR)) " +
           "AND (:search IS NULL OR d.name ILIKE CONCAT('%', CAST(:search AS VARCHAR), '%') " +
           "OR d.description ILIKE CONCAT('%', CAST(:search AS VARCHAR), '%'))",
           countQuery = "SELECT COUNT(*) FROM dimensions d WHERE d.project_id = :projectId " +
           "AND (:type IS NULL OR d.type = CAST(:type AS VARCHAR)) " +
           "AND (:search IS NULL OR d.name ILIKE CONCAT('%', CAST(:search AS VARCHAR), '%') " +
           "OR d.description ILIKE CONCAT('%', CAST(:search AS VARCHAR), '%'))",
           nativeQuery = true)
    Page<DimensionEntity> findByFilters(
        @Param("projectId") UUID projectId,
        @Param("type") String type,
        @Param("search") String search,
        Pageable pageable
    );

    @Query(value = "SELECT * FROM dimensions d WHERE d.is_shared = true " +
           "AND (:type IS NULL OR d.type = CAST(:type AS VARCHAR)) " +
           "AND (:search IS NULL OR d.name ILIKE CONCAT('%', CAST(:search AS VARCHAR), '%'))",
           countQuery = "SELECT COUNT(*) FROM dimensions d WHERE d.is_shared = true " +
           "AND (:type IS NULL OR d.type = CAST(:type AS VARCHAR)) " +
           "AND (:search IS NULL OR d.name ILIKE CONCAT('%', CAST(:search AS VARCHAR), '%'))",
           nativeQuery = true)
    Page<DimensionEntity> findSharedByFilters(
        @Param("type") String type,
        @Param("search") String search,
        Pageable pageable
    );

    @Query("SELECT COUNT(d) FROM DimensionEntity d WHERE d.projectId = :projectId")
    long countByProjectId(@Param("projectId") UUID projectId);

    @Query("SELECT COALESCE(SUM(d.valueCount), 0) FROM DimensionEntity d WHERE d.projectId = :projectId")
    long sumValueCountByProjectId(@Param("projectId") UUID projectId);

    boolean existsByProjectIdAndUri(UUID projectId, String uri);

    // Hierarchy support
    List<DimensionEntity> findByParentDimensionId(UUID parentDimensionId);
    
    List<DimensionEntity> findByHierarchyNameAndProjectId(String hierarchyName, UUID projectId);
    
    List<DimensionEntity> findByHierarchyName(String hierarchyName);
    
    @Query("SELECT d FROM DimensionEntity d WHERE d.parentDimensionId IS NULL AND d.hierarchyName = :hierarchyName")
    List<DimensionEntity> findHierarchyRoots(@Param("hierarchyName") String hierarchyName);
}
