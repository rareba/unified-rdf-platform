package io.rdfforge.shacl.validation;

/** Lifecycle status of a single suite run. */
public enum ValidationStatus {
    /** The run is currently executing (reserved for future async runs). */
    RUNNING,
    /** The run finished and no issue breached the suite's release gate. */
    PASSED,
    /** The run finished but at least one issue breached the release gate. */
    FAILED,
    /** The run aborted with an infrastructure/exec error (not a rule violation). */
    ERRORED
}
