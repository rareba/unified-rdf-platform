package io.rdfforge.pipeline.gitsync.repository;

import io.rdfforge.pipeline.gitsync.entity.GitSyncConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for Git sync configurations.
 */
@Repository
public interface GitSyncConfigRepository extends JpaRepository<GitSyncConfigEntity, UUID> {

    List<GitSyncConfigEntity> findByProjectId(UUID projectId);

    @Query("SELECT c FROM GitSyncConfigEntity c WHERE c.autoSync = true")
    List<GitSyncConfigEntity> findAutoSyncEnabled();

    boolean existsByProjectIdAndRepositoryUrl(UUID projectId, String repositoryUrl);
}
