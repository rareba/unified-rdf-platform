package io.rdfforge.engine.operation.source;

import io.rdfforge.engine.operation.Operation.OperationContext;
import io.rdfforge.engine.operation.OperationException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;

class LoadJsonOperationTest {

    @Test
    void execute_withoutFile_throws() {
        LoadJsonOperation op = new LoadJsonOperation();
        OperationContext ctx = new OperationContext(
                Map.of(),   // parameters
                null,       // inputStream
                null,       // inputModel
                Map.of(),   // variables
                null        // callback
        );
        assertThrows(OperationException.class, () -> op.execute(ctx));
    }
}
