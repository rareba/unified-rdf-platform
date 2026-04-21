package io.rdfforge.pipeline.dto;

import io.rdfforge.pipeline.entity.ProjectStatus;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Aggregated dashboard snapshot for a single Project. Counts is a map of
 * entity-type → count (e.g. {@code "pipelines" -> 12}). Only the local
 * entity types (pipelines, for now) are populated in this Phase 1 scope;
 * counts for cross-service entities (data sources, shapes, dimensions,
 * cubes, jobs, triplestores) are reserved for Phase 1.1.
 *
 * <p>{@code lastActivity} and {@code lastRelease} are best-effort hints
 * that may be {@code null} if no activity has been recorded for the project.
 */
public record ProjectSummaryDto(
    UUID id,
    String name,
    String description,
    ProjectStatus status,
    String baseUri,
    Instant createdAt,
    Instant updatedAt,
    Map<String, Long> counts,
    Instant lastActivity,
    String lastRelease
) {}
