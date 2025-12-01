package io.rdfforge.dimension.service;

import io.rdfforge.dimension.entity.DimensionEntity;
import io.rdfforge.dimension.repository.DimensionRepository;
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
class DimensionServiceTest {

    @Mock
    private DimensionRepository dimensionRepository;

    @InjectMocks
    private DimensionService dimensionService;

    @Test
    void testFindById() {
        UUID id = UUID.randomUUID();
        DimensionEntity dimension = new DimensionEntity();
        dimension.setId(id);
        
        when(dimensionRepository.findById(id)).thenReturn(Optional.of(dimension));
        
        Optional<DimensionEntity> result = dimensionService.findById(id);
        assertTrue(result.isPresent());
        assertEquals(id, result.get().getId());
    }
}
