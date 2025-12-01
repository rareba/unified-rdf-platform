package io.rdfforge.pipeline.repository;

import io.rdfforge.pipeline.entity.PipelineEntity;
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
public interface PipelineRepository extends JpaRepository<PipelineEntity, UUID> {
    Page<PipelineEntity> findByProjectId(UUID projectId, Pageable pageable);
    
    List<PipelineEntity> findByIsTemplateTrue();
    
    Optional<PipelineEntity> findByProjectIdAndName(UUID projectId, String name);
    
    @Query("SELECT p FROM PipelineEntity p WHERE p.projectId = :projectId AND " +
           "(LOWER(p.name) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(p.description) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<PipelineEntity> searchByProjectId(@Param("projectId") UUID projectId, 
                                            @Param("search") String search, 
                                            Pageable pageable);
    
    @Query("SELECT p FROM PipelineEntity p WHERE :tag = ANY(p.tags)")
    List<PipelineEntity> findByTag(@Param("tag") String tag);
}
