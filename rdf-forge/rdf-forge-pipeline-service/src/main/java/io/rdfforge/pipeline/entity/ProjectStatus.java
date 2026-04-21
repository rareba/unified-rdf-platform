package io.rdfforge.pipeline.entity;

/**
 * Lifecycle status of a {@link ProjectEntity}.
 *
 * <ul>
 *   <li>{@link #ACTIVE} — project is live; normal CRUD operations allowed.</li>
 *   <li>{@link #ARCHIVED} — project is hidden by default but preserved for audit.
 *       Contents remain intact, and the project can be unarchived. Archive is a
 *       soft operation; it does not cascade to dependent entities.</li>
 * </ul>
 */
public enum ProjectStatus {
    ACTIVE,
    ARCHIVED
}
