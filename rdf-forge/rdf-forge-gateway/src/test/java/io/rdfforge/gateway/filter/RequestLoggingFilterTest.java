package io.rdfforge.gateway.filter;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RequestLoggingFilterTest {

    @Test
    void testFilterCreation() {
        RequestLoggingFilter filter = new RequestLoggingFilter();
        assertNotNull(filter);
    }
}
