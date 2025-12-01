package io.rdfforge.engine.operation.source;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class LoadCsvOperationTest {

    @Test
    void testValidateParams() {
        LoadCsvOperation op = new LoadCsvOperation();
        
        // Should fail without file
        assertThrows(IllegalArgumentException.class, () -> {
            op.execute(Map.of());
        });
    }
}
