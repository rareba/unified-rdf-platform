package io.rdfforge.shacl.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShapeBuilderServiceTest {

    @Test
    void testBuild() {
        ShapeBuilderService builder = new ShapeBuilderService();
        assertNotNull(builder);
    }
}
