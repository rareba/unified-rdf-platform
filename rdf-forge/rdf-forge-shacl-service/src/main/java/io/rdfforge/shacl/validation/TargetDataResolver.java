package io.rdfforge.shacl.validation;

import io.rdfforge.shacl.validation.dto.ValidationRunRequest;
import org.apache.jena.rdf.model.Model;

/**
 * Strategy for fetching the data-graph that a validation suite executes
 * against. Production deployments resolve this by querying the triplestore
 * named by {@link ValidationRunRequest#targetTriplestoreId()}; tests supply
 * a stub that returns an in-memory Jena model, which keeps the executor
 * path DB-free.
 */
public interface TargetDataResolver {

    /**
     * Build (or fetch) a Jena {@link Model} containing the data that should
     * be validated. Implementations may return an empty model if the target
     * graph does not exist — the ValidationService will treat that as an
     * {@code ERRORED} run for visibility.
     */
    Model resolve(ValidationRunRequest request);
}
