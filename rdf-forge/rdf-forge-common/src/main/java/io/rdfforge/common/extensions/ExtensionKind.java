package io.rdfforge.common.extensions;

/**
 * Canonical set of plugin kinds surfaced by the Extension Catalog.
 *
 * <p>Each kind maps one-to-one to a registry hosted by a backend service:
 * <ul>
 *   <li>{@link #OPERATION} — {@code io.rdfforge.engine.operation.OperationRegistry}
 *       (hosted in pipeline-service)</li>
 *   <li>{@link #FORMAT} — {@code io.rdfforge.data.format.DataFormatRegistry}</li>
 *   <li>{@link #STORAGE_PROVIDER} — {@code io.rdfforge.data.storage.StorageProviderRegistry}</li>
 *   <li>{@link #DESTINATION} — {@code io.rdfforge.pipeline.destination.DestinationRegistry}</li>
 *   <li>{@link #TRIPLESTORE_PROVIDER} — {@code io.rdfforge.triplestore.connector.TriplestoreProviderRegistry}</li>
 *   <li>{@link #MATCHER} — Phase 8 matcher registry (optional)</li>
 *   <li>{@link #VALIDATOR} — SHACL cube profiles / shape templates</li>
 *   <li>{@link #CUBE_PROFILE} — cube validator registry aliases</li>
 * </ul>
 */
public enum ExtensionKind {
    OPERATION,
    FORMAT,
    STORAGE_PROVIDER,
    DESTINATION,
    TRIPLESTORE_PROVIDER,
    MATCHER,
    VALIDATOR,
    CUBE_PROFILE
}
