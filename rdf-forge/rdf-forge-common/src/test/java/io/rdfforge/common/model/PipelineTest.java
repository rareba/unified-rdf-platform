package io.rdfforge.common.model;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PipelineTest {

    @Test
    void testPipelineBuilder() {
        UUID id = UUID.randomUUID();
        Pipeline pipeline = Pipeline.builder()
                .id(id)
                .name("Test Pipeline")
                .description("Description")
                .version(1)
                .build();

        assertEquals(id, pipeline.getId());
        assertEquals("Test Pipeline", pipeline.getName());
        assertEquals("Description", pipeline.getDescription());
        assertEquals(1, pipeline.getVersion());
    }

    @Test
    void testPipelineValidation() {
        Pipeline pipeline = new Pipeline();
        assertNull(pipeline.getName());
        
        pipeline.setName("Valid Name");
        assertNotNull(pipeline.getName());
    }
}
