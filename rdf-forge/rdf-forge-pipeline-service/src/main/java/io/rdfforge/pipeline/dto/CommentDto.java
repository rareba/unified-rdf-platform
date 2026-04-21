package io.rdfforge.pipeline.dto;

import io.rdfforge.pipeline.entity.CommentEntity;
import io.rdfforge.pipeline.entity.CommentEntity.AssetKind;
import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record CommentDto(
        UUID id,
        UUID projectId,
        AssetKind assetKind,
        UUID assetId,
        String body,
        UUID authorId,
        String authorEmail,
        UUID parentCommentId,
        Instant createdAt,
        Instant updatedAt
) {
    public static CommentDto fromEntity(CommentEntity e) {
        return CommentDto.builder()
                .id(e.getId())
                .projectId(e.getProjectId())
                .assetKind(e.getAssetKind())
                .assetId(e.getAssetId())
                .body(e.getBody())
                .authorId(e.getAuthorId())
                .authorEmail(e.getAuthorEmail())
                .parentCommentId(e.getParentCommentId())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }
}
