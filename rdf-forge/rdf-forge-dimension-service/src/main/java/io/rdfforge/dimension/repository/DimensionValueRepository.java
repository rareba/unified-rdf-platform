package io.rdfforge.dimension.repository;

import io.rdfforge.dimension.entity.DimensionValueEntity;
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
public interface DimensionValueRepository extends JpaRepository<DimensionValueEntity, UUID> {
    
    List<DimensionValueEntity> findByDimensionId(UUID dimensionId);
    
    Page<DimensionValueEntity> findByDimensionId(UUID dimensionId, Pageable pageable);
    
    Optional<DimensionValueEntity> findByDimensionIdAndCode(UUID dimensionId, String code);
    
    Optional<DimensionValueEntity> findByDimensionIdAndUri(UUID dimensionId, String uri);
    
    List<DimensionValueEntity> findByDimensionIdAndParentIdIsNull(UUID dimensionId);
    
    List<DimensionValueEntity> findByParentId(UUID parentId);
    
    @Query("SELECT v FROM DimensionValueEntity v WHERE v.dimensionId = :dimensionId " +
           "AND v.hierarchyLevel = :level ORDER BY v.sortOrder, v.label")
    List<DimensionValueEntity> findByDimensionIdAndLevel(
        @Param("dimensionId") UUID dimensionId,
        @Param("level") Integer level
    );
    
    @Query("SELECT v FROM DimensionValueEntity v WHERE v.dimensionId = :dimensionId " +
           "AND (:search IS NULL OR LOWER(v.label) LIKE LOWER(CONCAT('%', :search, '%')) " +
           "OR LOWER(v.code) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<DimensionValueEntity> searchValues(
        @Param("dimensionId") UUID dimensionId,
        @Param("search") String search,
        Pageable pageable
    );
    
    @Query("SELECT COUNT(v) FROM DimensionValueEntity v WHERE v.dimensionId = :dimensionId")
    long countByDimensionId(@Param("dimensionId") UUID dimensionId);
    
    @Query("SELECT MAX(v.hierarchyLevel) FROM DimensionValueEntity v WHERE v.dimensionId = :dimensionId")
    Integer findMaxLevelByDimensionId(@Param("dimensionId") UUID dimensionId);
    
    @Query("SELECT v FROM DimensionValueEntity v WHERE v.dimensionId = :dimensionId " +
           "AND v.isDeprecated = false ORDER BY v.sortOrder, v.label")
    List<DimensionValueEntity> findActiveValuesByDimensionId(@Param("dimensionId") UUID dimensionId);
    
    void deleteByDimensionId(UUID dimensionId);
    
    boolean existsByDimensionIdAndCode(UUID dimensionId, String code);
    
    boolean existsByDimensionIdAndUri(UUID dimensionId, String uri);
}
