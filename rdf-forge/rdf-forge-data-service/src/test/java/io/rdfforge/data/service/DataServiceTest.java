package io.rdfforge.data.service;

import io.rdfforge.data.entity.DataSourceEntity;
import io.rdfforge.data.repository.DataSourceRepository;
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
class DataServiceTest {

    @Mock
    private DataSourceRepository dataSourceRepository;
    
    @Mock
    private FileStorageService fileStorageService;

    @InjectMocks
    private DataService dataService;

    @Test
    void testGetDataSource() {
        UUID id = UUID.randomUUID();
        DataSourceEntity ds = new DataSourceEntity();
        ds.setId(id);
        
        when(dataSourceRepository.findById(id)).thenReturn(Optional.of(ds));
        
        Optional<DataSourceEntity> result = dataService.getDataSource(id);
        assertTrue(result.isPresent());
        assertEquals(id, result.get().getId());
    }
}
