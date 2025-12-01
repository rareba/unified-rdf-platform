package io.rdfforge.pipeline.service;

import io.rdfforge.pipeline.entity.PipelineEntity;
import io.rdfforge.pipeline.repository.PipelineRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PipelineServiceTest {

    @Mock
    private PipelineRepository pipelineRepository;

    @InjectMocks
    private PipelineService pipelineService;

    @Test
    void testFindById() {
        UUID id = UUID.randomUUID();
        PipelineEntity pipeline = new PipelineEntity();
        pipeline.setId(id);
        
        when(pipelineRepository.findById(id)).thenReturn(Optional.of(pipeline));
        
        Optional<PipelineEntity> result = pipelineService.findById(id);
        assertTrue(result.isPresent());
        assertEquals(id, result.get().getId());
    }
}
