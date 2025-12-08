package io.rdfforge.job.repository;

import io.rdfforge.job.entity.JobLogEntity;
import io.rdfforge.job.entity.JobLogEntity.LogLevel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface JobLogRepository extends JpaRepository<JobLogEntity, Long> {

    List<JobLogEntity> findByJob_IdOrderByTimestampAsc(UUID jobId);

    Page<JobLogEntity> findByJob_IdOrderByTimestampAsc(UUID jobId, Pageable pageable);

    @Query("SELECT l FROM JobLogEntity l WHERE l.job.id = :jobId AND l.level IN :levels ORDER BY l.timestamp ASC")
    List<JobLogEntity> findByJobIdAndLevels(@Param("jobId") UUID jobId, @Param("levels") List<LogLevel> levels);

    @Query("SELECT l FROM JobLogEntity l WHERE l.job.id = :jobId AND l.step = :step ORDER BY l.timestamp ASC")
    List<JobLogEntity> findByJobIdAndStep(@Param("jobId") UUID jobId, @Param("step") String step);

    void deleteByJob_Id(UUID jobId);
}
