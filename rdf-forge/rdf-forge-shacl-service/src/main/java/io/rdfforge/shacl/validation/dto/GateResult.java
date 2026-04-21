package io.rdfforge.shacl.validation.dto;

import java.util.List;

/**
 * Outcome of applying a suite's {@code ReleaseGate} to its issues. Consumed
 * by the future release-factory (phase 6) to decide whether a publish can
 * proceed. {@link #blockedBy()} contains the issues that breached the gate;
 * when {@link #passed()} is {@code true} it is an empty list.
 */
public record GateResult(
    boolean passed,
    List<ValidationIssueDto> blockedBy
) {
}
