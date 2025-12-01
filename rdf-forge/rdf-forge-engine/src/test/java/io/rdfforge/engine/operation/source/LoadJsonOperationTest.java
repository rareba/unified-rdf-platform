package io.rdfforge.engine.operation.source;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class LoadJsonOperationTest {

    @Test
    void testValidateParams() {
        LoadJsonOperation op = new LoadJsonOperation();
        
        // Should fail without file
        assertThrows(IllegalArgumentException.class, () -> {
            op.execute(Map.of());
        });
    }
}
