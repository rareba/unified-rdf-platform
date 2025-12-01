package io.rdfforge.common.exception;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExceptionTest {

    @Test
    void testRdfForgeException() {
        RdfForgeException ex = new RdfForgeException("Error occurred");
        assertEquals("Error occurred", ex.getMessage());
    }

    @Test
    void testResourceNotFoundException() {
        ResourceNotFoundException ex = new ResourceNotFoundException("Resource", "123");
        assertTrue(ex.getMessage().contains("Resource not found: 123"));
    }
}
