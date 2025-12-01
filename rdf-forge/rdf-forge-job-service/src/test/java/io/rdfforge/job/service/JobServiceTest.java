package io.rdfforge.job.service;

import io.rdfforge.job.entity.JobEntity;
import io.rdfforge.job.repository.JobRepository;
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
class JobServiceTest {

    @Mock
    private JobRepository jobRepository;

    @InjectMocks
    private JobService jobService;

    @Test
    void testGetJob() {
        UUID id = UUID.randomUUID();
        JobEntity job = new JobEntity();
        job.setId(id);
        
        when(jobRepository.findById(id)).thenReturn(Optional.of(job));
        
        Optional<JobEntity> result = jobService.getJob(id);
        assertTrue(result.isPresent());
        assertEquals(id, result.get().getId());
    }
}
