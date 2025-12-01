package io.rdfforge.triplestore.service;

import io.rdfforge.triplestore.entity.TriplestoreConnectionEntity;
import io.rdfforge.triplestore.repository.TriplestoreConnectionRepository;
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
class TriplestoreServiceTest {

    @Mock
    private TriplestoreConnectionRepository repository;

    @InjectMocks
    private TriplestoreService service;

    @Test
    void testGetConnection() {
        UUID id = UUID.randomUUID();
        TriplestoreConnectionEntity conn = new TriplestoreConnectionEntity();
        conn.setId(id);
        
        when(repository.findById(id)).thenReturn(Optional.of(conn));
        
        Optional<TriplestoreConnectionEntity> result = service.getConnection(id);
        assertTrue(result.isPresent());
        assertEquals(id, result.get().getId());
    }
}
