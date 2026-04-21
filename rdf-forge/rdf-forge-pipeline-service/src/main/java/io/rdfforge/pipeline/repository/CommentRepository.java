package io.rdfforge.pipeline.repository;

import io.rdfforge.pipeline.entity.CommentEntity;
import io.rdfforge.pipeline.entity.CommentEntity.AssetKind;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface CommentRepository extends JpaRepository<CommentEntity, UUID> {

    /**
     * All non-deleted comments for a given asset, newest-first by created_at.
     */
    List<CommentEntity> findByAssetKindAndAssetIdAndDeletedFalseOrderByCreatedAtAsc(
            AssetKind assetKind, UUID assetId);

    /**
     * All comments in a project (for moderation / audit views).
     */
    List<CommentEntity> findByProjectIdAndDeletedFalseOrderByCreatedAtDesc(UUID projectId);
}
