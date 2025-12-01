package io.rdfforge.triplestore.connector;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FusekiConnectorTest {

    @Test
    void testFusekiConnector() {
        FusekiConnector connector = new FusekiConnector();
        assertNotNull(connector);
    }
}
