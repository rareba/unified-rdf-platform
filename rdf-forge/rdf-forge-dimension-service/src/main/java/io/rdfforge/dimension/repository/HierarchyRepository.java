package io.rdfforge.dimension.repository;

import io.rdfforge.dimension.entity.HierarchyEntity;
import io.rdfforge.dimension.entity.HierarchyEntity.HierarchyScheme;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface HierarchyRepository extends JpaRepository<HierarchyEntity, UUID> {
    
    List<HierarchyEntity> findByDimensionId(UUID dimensionId);
    
    Optional<HierarchyEntity> findByDimensionIdAndUri(UUID dimensionId, String uri);
    
    Optional<HierarchyEntity> findByDimensionIdAndIsDefaultTrue(UUID dimensionId);
    
    List<HierarchyEntity> findByDimensionIdAndHierarchyType(UUID dimensionId, HierarchyScheme type);
    
    @Query("SELECT h FROM HierarchyEntity h WHERE h.dimensionId = :dimensionId " +
           "ORDER BY h.isDefault DESC, h.name ASC")
    List<HierarchyEntity> findByDimensionIdOrderedByDefault(@Param("dimensionId") UUID dimensionId);
    
    boolean existsByDimensionIdAndUri(UUID dimensionId, String uri);
    
    void deleteByDimensionId(UUID dimensionId);
}
