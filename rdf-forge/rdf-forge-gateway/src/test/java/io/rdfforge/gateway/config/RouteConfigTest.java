package io.rdfforge.gateway.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RouteConfigTest {

    @Test
    void testConfig() {
        RouteConfig config = new RouteConfig();
        assertNotNull(config);
    }
}
