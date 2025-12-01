package io.rdfforge.pipeline.repository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class PipelineRepositoryTest {

    @Autowired
    private PipelineRepository pipelineRepository;

    @Test
    void testRepository() {
        assertNotNull(pipelineRepository);
    }
}
