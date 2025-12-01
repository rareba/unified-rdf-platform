package io.rdfforge.engine.shacl;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ShaclValidatorTest {

    @Test
    void testValidator() {
        ShaclValidator validator = new ShaclValidator();
        assertNotNull(validator);
    }
}
