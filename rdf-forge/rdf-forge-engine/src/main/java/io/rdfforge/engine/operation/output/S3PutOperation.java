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
public class S3PutOperation implements Operation {

    @Override
    public String getId() {
        return "s3-put";
    }

    @Override
    public String getName() {
        return "S3 Put";
    }

    @Override
    public String getDescription() {
        return "Uploads content to an S3 bucket";
    }

    @Override
    public OperationType getType() {
        return OperationType.OUTPUT;
    }

    @Override
    public Map<String, ParameterSpec> getParameters() {
        return Map.of(
            "bucket", new ParameterSpec("bucket", "S3 Bucket Name", String.class, true, null),
            "key", new ParameterSpec("key", "Object Key (Path)", String.class, true, null)
        );
    }

    @Override
    public OperationResult execute(OperationContext context) throws OperationException {
        return new OperationResult(true, null, null, null, null);
    }
}