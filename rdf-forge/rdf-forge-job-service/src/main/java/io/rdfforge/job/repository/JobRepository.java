package io.rdfforge.job.repository;

import io.rdfforge.job.entity.JobEntity;
import io.rdfforge.job.entity.JobEntity.JobStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface JobRepository extends JpaRepository<JobEntity, UUID> {
    
    List<JobEntity> findByPipelineIdOrderByCreatedAtDesc(UUID pipelineId);
    
    List<JobEntity> findByStatusOrderByPriorityDescCreatedAtAsc(JobStatus status);
    
    Page<JobEntity> findByStatusIn(List<JobStatus> statuses, Pageable pageable);
    
    @Query("SELECT j FROM JobEntity j WHERE j.status = :status ORDER BY j.priority DESC, j.createdAt ASC")
    List<JobEntity> findPendingJobs(@Param("status") JobStatus status);
    
    @Query("SELECT j FROM JobEntity j WHERE j.status = 'RUNNING' AND j.startedAt < :timeout")
    List<JobEntity> findStaleRunningJobs(@Param("timeout") Instant timeout);
    
    @Query("SELECT COUNT(j) FROM JobEntity j WHERE j.status = :status")
    long countByStatus(@Param("status") JobStatus status);
    
    @Query("SELECT COUNT(j) FROM JobEntity j WHERE j.status = :status AND j.completedAt >= :since")
    long countByStatusSince(@Param("status") JobStatus status, @Param("since") Instant since);
    
    @Query("SELECT j FROM JobEntity j WHERE j.createdBy = :userId ORDER BY j.createdAt DESC")
    Page<JobEntity> findByUser(@Param("userId") UUID userId, Pageable pageable);
    
    @Query("SELECT j FROM JobEntity j WHERE " +
           "(:status IS NULL OR j.status = :status) AND " +
           "(:pipelineId IS NULL OR j.pipelineId = :pipelineId) " +
           "ORDER BY j.createdAt DESC")
    Page<JobEntity> findWithFilters(
        @Param("status") JobStatus status,
        @Param("pipelineId") UUID pipelineId,
        Pageable pageable
    );
}
