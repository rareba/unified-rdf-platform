package io.rdfforge.gateway.filter;

import io.rdfforge.gateway.config.RateLimitConfig;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RateLimitFilterTest {

    @Test
    void testFilterCreation() {
        RateLimitConfig config = mock(RateLimitConfig.class);
        RateLimitFilter filter = new RateLimitFilter(config);
        assertNotNull(filter);
    }
}
