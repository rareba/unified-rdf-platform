package io.rdfforge.data.repository;

import io.rdfforge.data.entity.DataSourceEntity;
import io.rdfforge.data.entity.DataSourceEntity.DataFormat;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DataSourceRepository extends JpaRepository<DataSourceEntity, UUID> {
    
    List<DataSourceEntity> findByProjectIdOrderByUploadedAtDesc(UUID projectId);
    
    Page<DataSourceEntity> findByProjectId(UUID projectId, Pageable pageable);
    
    List<DataSourceEntity> findByFormat(DataFormat format);
    
    @Query("SELECT d FROM DataSourceEntity d WHERE " +
           "(:projectId IS NULL OR d.projectId = :projectId) AND " +
           "(:format IS NULL OR d.format = :format) AND " +
           "(:search IS NULL OR LOWER(d.name) LIKE LOWER(CONCAT('%', :search, '%'))) " +
           "ORDER BY d.uploadedAt DESC")
    Page<DataSourceEntity> findWithFilters(
        @Param("projectId") UUID projectId,
        @Param("format") DataFormat format,
        @Param("search") String search,
        Pageable pageable
    );
    
    @Query("SELECT SUM(d.sizeBytes) FROM DataSourceEntity d WHERE d.projectId = :projectId")
    Long getTotalSizeByProject(@Param("projectId") UUID projectId);
}
