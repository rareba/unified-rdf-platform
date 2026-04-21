package io.rdfforge.pipeline.dto;

import io.rdfforge.pipeline.entity.CommentEntity.AssetKind;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/** New comment or reply. Set {@code parentCommentId} to reply to another comment. */
public record CommentCreateRequest(
        @NotNull UUID projectId,
        @NotNull AssetKind assetKind,
        @NotNull UUID assetId,
        @NotBlank @Size(max = 10_000) String body,
        UUID parentCommentId
) {}
