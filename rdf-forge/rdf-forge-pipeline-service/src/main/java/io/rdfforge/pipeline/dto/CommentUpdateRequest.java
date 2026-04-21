package io.rdfforge.pipeline.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Update a comment's body (author-only; enforced in service). */
public record CommentUpdateRequest(
        @NotBlank @Size(max = 10_000) String body
) {}
