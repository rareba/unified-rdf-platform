package io.rdfforge.engine.operation.transform;

import io.rdfforge.engine.operation.Operation;
import io.rdfforge.engine.operation.OperationException;
import io.rdfforge.engine.operation.Operation.OperationResult;
import io.rdfforge.engine.operation.Operation.OperationContext;
import io.rdfforge.engine.operation.Operation.ParameterSpec;
import io.rdfforge.engine.operation.Operation.OperationType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.stream.Stream;

@Slf4j
@Component
public class MapOperation implements Operation {

    @Override
    public String getId() {
        return "map";
    }

    @Override
    public String getName() {
        return "Map / Transform";
    }

    @Override
    public String getDescription() {
        return "Transforms stream items using a mapping function";
    }

    @Override
    public OperationType getType() {
        return OperationType.TRANSFORM;
    }

    @Override
    public Map<String, ParameterSpec> getParameters() {
        return Map.of(
            "function", new ParameterSpec("function", "JavaScript function (e.g. 'item => item.value * 2')", String.class, true, null)
        );
    }

    @Override
    public OperationResult execute(OperationContext context) throws OperationException {
        return new OperationResult(true, context.inputStream(), null, null, null);
    }
}