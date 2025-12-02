package io.rdfforge.engine.operation.output;

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
public class FileWriterOperation implements Operation {

    @Override
    public String getId() {
        return "file-write";
    }

    @Override
    public String getName() {
        return "Write File";
    }

    @Override
    public String getDescription() {
        return "Writes stream content to a local file";
    }

    @Override
    public OperationType getType() {
        return OperationType.OUTPUT;
    }

    @Override
    public Map<String, ParameterSpec> getParameters() {
        return Map.of(
            "path", new ParameterSpec("path", "Destination file path", String.class, true, null)
        );
    }

    @Override
    public OperationResult execute(OperationContext context) throws OperationException {
        return new OperationResult(true, null, null, null, null);
    }
}