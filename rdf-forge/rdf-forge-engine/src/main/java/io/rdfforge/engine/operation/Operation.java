package io.rdfforge.engine.operation;

import org.apache.jena.rdf.model.Model;
import java.util.Map;
import java.util.stream.Stream;

public interface Operation {
    String getId();
    String getName();
    String getDescription();
    OperationType getType();
    Map<String, ParameterSpec> getParameters();

    OperationResult execute(OperationContext context) throws OperationException;

    enum OperationType {
        SOURCE,
        TRANSFORM,
        CUBE,
        VALIDATION,
        OUTPUT
    }

    record ParameterSpec(
        String name,
        String description,
        Class<?> type,
        boolean required,
        Object defaultValue
    ) {}

    record OperationContext(
        Map<String, Object> parameters,
        Stream<?> inputStream,
        Model inputModel,
        Map<String, Object> variables,
        OperationCallback callback
    ) {}

    record OperationResult(
        boolean success,
        Stream<?> outputStream,
        Model outputModel,
        Map<String, Object> metadata,
        String errorMessage
    ) {}

    interface OperationCallback {
        void onProgress(long processed, long total);
        void onLog(String level, String message);
        void onMetric(String name, Object value);
    }
}
