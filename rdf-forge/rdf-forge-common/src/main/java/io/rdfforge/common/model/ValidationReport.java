package io.rdfforge.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationReport {
    private UUID id;
    private boolean conforms;
    private int violationCount;
    private int warningCount;
    private int infoCount;
    private List<ValidationResult> results;
    private Instant validatedAt;
    private long durationMs;
    private Map<String, Object> metadata;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationResult {
        private Severity severity;
        private String focusNode;
        private String resultPath;
        private String value;
        private String message;
        private String sourceConstraintComponent;
        private String sourceShape;

        public enum Severity {
            VIOLATION,
            WARNING,
            INFO
        }
    }
}
