package io.rdfforge.pipeline.gitsync.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Status of a Git sync operation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GitSyncStatus {
    private String operationId;
    private SyncState state;
    private SyncDirection direction;
    private Instant startedAt;
    private Instant completedAt;
    private String commitSha;
    private String commitMessage;
    private List<SyncedFile> syncedFiles;
    private List<String> errors;
    private List<String> warnings;

    public enum SyncState {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        FAILED
    }

    public enum SyncDirection {
        PUSH,
        PULL
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SyncedFile {
        private String path;
        private String type;
        private FileAction action;
        private String resourceId;
        private String resourceName;

        public enum FileAction {
            CREATED,
            UPDATED,
            DELETED,
            UNCHANGED
        }
    }
}
