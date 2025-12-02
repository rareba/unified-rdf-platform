package io.rdfforge.engine.operation.format;

import io.rdfforge.engine.operation.Operation;
import io.rdfforge.engine.operation.OperationException;
import io.rdfforge.engine.operation.Operation.OperationResult;
import io.rdfforge.engine.operation.Operation.OperationContext;
import io.rdfforge.engine.operation.Operation.ParameterSpec;
import io.rdfforge.engine.operation.Operation.OperationType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

@Slf4j
@Component
public class JsonLdParseOperation implements Operation {

    @Override
    public String getId() {
        return "jsonld-parse";
    }

    @Override
    public String getName() {
        return "Parse JSON-LD";
    }

    @Override
    public String getDescription() {
        return "Parses JSON-LD content to RDF Quads";
    }

    @Override
    public OperationType getType() {
        return OperationType.TRANSFORM;
    }

    @Override
    public Map<String, ParameterSpec> getParameters() {
        return Map.of();
    }

    @Override
    public OperationResult execute(OperationContext context) throws OperationException {
        return new OperationResult(true, null, null, null, null);
    }
}