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
public class FilterOperation implements Operation {

    @Override
    public String getId() {
        return "filter";
    }

    @Override
    public String getName() {
        return "Filter";
    }

    @Override
    public String getDescription() {
        return "Filters stream items based on a condition";
    }

    @Override
    public OperationType getType() {
        return OperationType.TRANSFORM;
    }

    @Override
    public Map<String, ParameterSpec> getParameters() {
        return Map.of(
            "condition", new ParameterSpec("condition", "JavaScript condition (e.g. 'item.value > 10')", String.class, true, null)
        );
    }

    @Override
    public OperationResult execute(OperationContext context) throws OperationException {
        Stream<?> input = context.inputStream();
        if (input == null) {
            return new OperationResult(true, Stream.empty(), null, null, null);
        }
        
        return new OperationResult(true, input, null, null, null);
    }
}