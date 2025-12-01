package io.rdfforge.common.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CubeTest {

    @Test
    void testCubeCreation() {
        Cube cube = Cube.builder()
                .name("Sales Cube")
                .uri("http://example.org/cube/sales")
                .build();

        assertEquals("Sales Cube", cube.getName());
        assertEquals("http://example.org/cube/sales", cube.getUri());
    }
}
