package io.rdfforge.shacl.validation;

/**
 * Severity tag applied to a validation issue. Ordered from least to most
 * severe so that {@code compareTo()} gives a meaningful ranking.
 */
public enum ValidationSeverity {
    INFO,
    WARNING,
    ERROR,
    FATAL;

    /** Returns true if this severity is at least as severe as the threshold. */
    public boolean atLeast(ValidationSeverity threshold) {
        return this.ordinal() >= threshold.ordinal();
    }
}
