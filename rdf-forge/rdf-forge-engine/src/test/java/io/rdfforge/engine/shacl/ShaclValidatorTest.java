package io.rdfforge.engine.shacl;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * ShaclValidator is the public interface. Instantiate its production
 * implementation to verify the binding is wired correctly.
 */
class ShaclValidatorTest {

    @Test
    void defaultImplementation_isInstantiable() {
        ShaclValidator validator = new ShaclValidatorService(new SimpleMeterRegistry());
        assertNotNull(validator);
    }
}
