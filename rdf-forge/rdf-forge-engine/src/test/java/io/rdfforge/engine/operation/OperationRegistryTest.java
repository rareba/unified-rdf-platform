package io.rdfforge.engine.operation;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = {OperationRegistry.class, OperationRegistryTest.TestConfig.class})
class OperationRegistryTest {

    @Autowired
    private OperationRegistry registry;

    @Test
    void testRegistryInitialization() {
        assertNotNull(registry);
    }

    @Test
    void testGetOperation() {
        // Assuming we have at least one operation registered or can mock one
        // For now just checking the registry exists and methods are callable
        Optional<Operation> op = registry.getOperation("unknown-op");
        assertTrue(op.isEmpty());
    }
    
    @Configuration
    static class TestConfig {
        @Bean
        public List<Operation> operations() {
            return Collections.emptyList();
        }
    }
}
