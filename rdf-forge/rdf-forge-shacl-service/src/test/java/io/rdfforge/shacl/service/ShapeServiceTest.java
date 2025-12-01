package io.rdfforge.shacl.service;

import io.rdfforge.shacl.entity.ShapeEntity;
import io.rdfforge.shacl.repository.ShapeRepository;
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
class ShapeServiceTest {

    @Mock
    private ShapeRepository shapeRepository;

    @InjectMocks
    private ShapeService shapeService;

    @Test
    void testFindById() {
        UUID id = UUID.randomUUID();
        ShapeEntity shape = new ShapeEntity();
        shape.setId(id);
        
        when(shapeRepository.findById(id)).thenReturn(Optional.of(shape));
        
        Optional<ShapeEntity> result = shapeService.findById(id);
        assertTrue(result.isPresent());
        assertEquals(id, result.get().getId());
    }
}
