package io.rdfforge.engine.operation.transform;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class MapToRdfOperationTest {

    @Test
    void testConfiguration() {
        MapToRdfOperation op = new MapToRdfOperation();
        assertEquals("map-to-rdf", op.getId());
    }
}
