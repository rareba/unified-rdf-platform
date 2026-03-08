package io.rdfforge.common.metrics;

/**
 * Centralized metric name constants for the RDF Forge platform.
 * <p>
 * All metric names follow Micrometer naming conventions using dot-separated
 * lowercase words. Prometheus will automatically convert these to
 * underscore-separated names (e.g., rdfforge.jobs.created becomes
 * rdfforge_jobs_created_total for counters).
 * </p>
 */
public final class RdfForgeMetrics {

    private RdfForgeMetrics() {
        // Prevent instantiation
    }

    // -------------------------------------------------------------------------
    // Pipeline Execution Metrics
    // -------------------------------------------------------------------------

    /** Timer: duration of full pipeline executions. */
    public static final String PIPELINE_EXECUTION_DURATION = "rdfforge.pipeline.execution.duration";

    /** Counter: total number of pipeline executions started. */
    public static final String PIPELINE_EXECUTIONS_TOTAL = "rdfforge.pipeline.executions";

    // -------------------------------------------------------------------------
    // Job Processing Metrics
    // -------------------------------------------------------------------------

    /** Counter: total jobs created. */
    public static final String JOBS_CREATED = "rdfforge.jobs.created";

    /** Counter: total jobs completed successfully. */
    public static final String JOBS_COMPLETED = "rdfforge.jobs.completed";

    /** Counter: total jobs that failed. */
    public static final String JOBS_FAILED = "rdfforge.jobs.failed";

    /** Timer: duration of individual job executions. */
    public static final String JOB_DURATION = "rdfforge.job.duration";

    // -------------------------------------------------------------------------
    // SPARQL / Triplestore Metrics
    // -------------------------------------------------------------------------

    /** Timer: duration of SPARQL query executions. */
    public static final String SPARQL_QUERY_DURATION = "rdfforge.sparql.query.duration";

    /** Timer: duration of SPARQL update (write) operations. */
    public static final String SPARQL_UPDATE_DURATION = "rdfforge.sparql.update.duration";

    /** Timer: duration of RDF upload operations. */
    public static final String RDF_UPLOAD_DURATION = "rdfforge.rdf.upload.duration";

    // -------------------------------------------------------------------------
    // File Upload Metrics
    // -------------------------------------------------------------------------

    /** Timer: duration of file upload operations. */
    public static final String FILE_UPLOAD_DURATION = "rdfforge.file.upload.duration";

    /** Counter: total number of files uploaded. */
    public static final String FILES_UPLOADED = "rdfforge.files.uploaded";

    // -------------------------------------------------------------------------
    // SHACL Validation Metrics
    // -------------------------------------------------------------------------

    /** Timer: duration of SHACL validation operations. */
    public static final String SHACL_VALIDATION_DURATION = "rdfforge.shacl.validation.duration";

    /** Counter: total SHACL validations performed. */
    public static final String SHACL_VALIDATIONS_TOTAL = "rdfforge.shacl.validations";

    /** Counter: SHACL validations that resulted in conformance. */
    public static final String SHACL_VALIDATIONS_CONFORMING = "rdfforge.shacl.validations.conforming";

    /** Counter: SHACL validations that found violations. */
    public static final String SHACL_VALIDATIONS_NONCONFORMING = "rdfforge.shacl.validations.nonconforming";

    // -------------------------------------------------------------------------
    // Common Tag Keys
    // -------------------------------------------------------------------------

    public static final String TAG_STATUS = "status";
    public static final String TAG_OUTCOME = "outcome";
    public static final String TAG_SERVICE = "service";
    public static final String TAG_PIPELINE_ID = "pipeline.id";
    public static final String TAG_CONNECTION_ID = "connection.id";
}
