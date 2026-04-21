package io.rdfforge.triplestore.connector;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class FusekiConnectorTest {

    @Test
    void testFusekiConnectorInstantiation() {
        // Real constructor: (endpoint, username, password). Credentials nullable.
        FusekiConnector connector = new FusekiConnector(
                "http://localhost:3030/ds",
                null,
                null
        );
        assertNotNull(connector);
    }
}
