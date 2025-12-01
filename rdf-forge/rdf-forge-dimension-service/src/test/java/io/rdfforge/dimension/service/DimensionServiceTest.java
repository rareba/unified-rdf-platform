package io.rdfforge.dimension.service;

import io.rdfforge.dimension.entity.DimensionEntity;
import io.rdfforge.dimension.entity.DimensionEntity.DimensionType;
import io.rdfforge.dimension.entity.DimensionEntity.HierarchyType;
import io.rdfforge.dimension.entity.DimensionValueEntity;
import io.rdfforge.dimension.repository.DimensionRepository;
import io.rdfforge.dimension.repository.DimensionValueRepository;
import io.rdfforge.dimension.repository.HierarchyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DimensionService Tests")
class DimensionServiceTest {

    @Mock
    private DimensionRepository dimensionRepository;

    @Mock
    private DimensionValueRepository valueRepository;

    @Mock
    private HierarchyRepository hierarchyRepository;

    private DimensionService dimensionService;

    private UUID dimensionId;
    private UUID projectId;
    private UUID userId;
    private UUID valueId;
    private DimensionEntity sampleDimension;
    private DimensionValueEntity sampleValue;

    @BeforeEach
    void setUp() {
        dimensionService = new DimensionService(dimensionRepository, valueRepository, hierarchyRepository);
        
        dimensionId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        userId = UUID.randomUUID();
        valueId = UUID.randomUUID();

        sampleDimension = new DimensionEntity();
        sampleDimension.setId(dimensionId);
        sampleDimension.setProjectId(projectId);
        sampleDimension.setUri("http://example.org/dimensions/time");
        sampleDimension.setName("Time Dimension");
        sampleDimension.setDescription("Time-based dimension");
        sampleDimension.setType(DimensionType.TEMPORAL);
        sampleDimension.setHierarchyType(HierarchyType.SINGLE);
        sampleDimension.setBaseUri("http://example.org/dimensions/time/");
        sampleDimension.setVersion(1);
        sampleDimension.setValueCount(0L);
        sampleDimension.setCreatedBy(userId);
        sampleDimension.setCreatedAt(Instant.now());

        sampleValue = new DimensionValueEntity();
        sampleValue.setId(valueId);
        sampleValue.setDimensionId(dimensionId);
        sampleValue.setCode("2024");
        sampleValue.setLabel("Year 2024");
        sampleValue.setUri("http://example.org/dimensions/time/2024");
        sampleValue.setSortOrder(0);
        sampleValue.setCreatedAt(Instant.now());
    }

    @Nested
    @DisplayName("create Tests")
    class CreateTests {

        @Test
        @DisplayName("Should create dimension successfully")
        void create_WithValidData_CreatesDimension() {
            when(dimensionRepository.existsByProjectIdAndUri(projectId, sampleDimension.getUri()))
                .thenReturn(false);
            when(dimensionRepository.save(any(DimensionEntity.class)))
                .thenAnswer(inv -> {
                    DimensionEntity entity = inv.getArgument(0);
                    entity.setId(dimensionId);
                    return entity;
                });

            DimensionEntity result = dimensionService.create(sampleDimension);

            assertNotNull(result);
            assertEquals(1, result.getVersion());
            assertEquals(0L, result.getValueCount());
            assertNotNull(result.getCreatedAt());
        }

        @Test
        @DisplayName("Should throw exception when URI already exists")
        void create_WithDuplicateUri_ThrowsException() {
            when(dimensionRepository.existsByProjectIdAndUri(projectId, sampleDimension.getUri()))
                .thenReturn(true);

            assertThrows(IllegalArgumentException.class, () -> 
                dimensionService.create(sampleDimension)
            );
        }
    }

    @Nested
    @DisplayName("findById Tests")
    class FindByIdTests {

        @Test
        @DisplayName("Should return dimension when found")
        void findById_WhenFound_ReturnsDimension() {
            when(dimensionRepository.findById(dimensionId)).thenReturn(Optional.of(sampleDimension));

            Optional<DimensionEntity> result = dimensionService.findById(dimensionId);

            assertTrue(result.isPresent());
            assertEquals(dimensionId, result.get().getId());
        }

        @Test
        @DisplayName("Should return empty when not found")
        void findById_WhenNotFound_ReturnsEmpty() {
            when(dimensionRepository.findById(dimensionId)).thenReturn(Optional.empty());

            Optional<DimensionEntity> result = dimensionService.findById(dimensionId);

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("findByProject Tests")
    class FindByProjectTests {

        @Test
        @DisplayName("Should return page of dimensions")
        void findByProject_ReturnsPage() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<DimensionEntity> page = new PageImpl<>(List.of(sampleDimension), pageable, 1);
            when(dimensionRepository.findByProjectId(projectId, pageable)).thenReturn(page);

            Page<DimensionEntity> result = dimensionService.findByProject(projectId, pageable);

            assertEquals(1, result.getTotalElements());
        }
    }

    @Nested
    @DisplayName("search Tests")
    class SearchTests {

        @Test
        @DisplayName("Should search dimensions with filters")
        void search_WithFilters_ReturnsMatchingDimensions() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<DimensionEntity> page = new PageImpl<>(List.of(sampleDimension), pageable, 1);
            when(dimensionRepository.findByFilters(projectId, DimensionType.TEMPORAL, "Time", pageable))
                .thenReturn(page);

            Page<DimensionEntity> result = dimensionService.search(projectId, DimensionType.TEMPORAL, "Time", pageable);

            assertEquals(1, result.getTotalElements());
        }
    }

    @Nested
    @DisplayName("update Tests")
    class UpdateTests {

        @Test
        @DisplayName("Should update dimension successfully")
        void update_WithValidData_UpdatesDimension() {
            when(dimensionRepository.findById(dimensionId)).thenReturn(Optional.of(sampleDimension));
            when(dimensionRepository.save(any(DimensionEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            DimensionEntity updates = new DimensionEntity();
            updates.setName("Updated Time Dimension");
            updates.setDescription("Updated description");

            DimensionEntity result = dimensionService.update(dimensionId, updates);

            assertEquals("Updated Time Dimension", result.getName());
            assertEquals("Updated description", result.getDescription());
            assertEquals(2, result.getVersion());
            assertNotNull(result.getUpdatedAt());
        }

        @Test
        @DisplayName("Should throw exception when dimension not found")
        void update_WhenNotFound_ThrowsException() {
            when(dimensionRepository.findById(dimensionId)).thenReturn(Optional.empty());

            assertThrows(NoSuchElementException.class, () -> 
                dimensionService.update(dimensionId, new DimensionEntity())
            );
        }
    }

    @Nested
    @DisplayName("delete Tests")
    class DeleteTests {

        @Test
        @DisplayName("Should delete dimension and related data")
        void delete_DeletesDimensionAndRelatedData() {
            dimensionService.delete(dimensionId);

            verify(hierarchyRepository).deleteByDimensionId(dimensionId);
            verify(valueRepository).deleteByDimensionId(dimensionId);
            verify(dimensionRepository).deleteById(dimensionId);
        }
    }

    @Nested
    @DisplayName("getValues Tests")
    class GetValuesTests {

        @Test
        @DisplayName("Should return all values for dimension")
        void getValues_ReturnsAllValues() {
            when(valueRepository.findByDimensionId(dimensionId)).thenReturn(List.of(sampleValue));

            List<DimensionValueEntity> result = dimensionService.getValues(dimensionId);

            assertEquals(1, result.size());
            assertEquals("2024", result.get(0).getCode());
        }

        @Test
        @DisplayName("Should return paged values with search")
        void getValues_WithSearch_ReturnsFilteredValues() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<DimensionValueEntity> page = new PageImpl<>(List.of(sampleValue), pageable, 1);
            when(valueRepository.searchValues(dimensionId, "2024", pageable)).thenReturn(page);

            Page<DimensionValueEntity> result = dimensionService.getValues(dimensionId, "2024", pageable);

            assertEquals(1, result.getTotalElements());
        }
    }

    @Nested
    @DisplayName("addValue Tests")
    class AddValueTests {

        @Test
        @DisplayName("Should add value successfully")
        void addValue_WithValidData_AddsValue() {
            when(valueRepository.existsByDimensionIdAndCode(dimensionId, "2024")).thenReturn(false);
            when(valueRepository.save(any(DimensionValueEntity.class))).thenAnswer(inv -> {
                DimensionValueEntity entity = inv.getArgument(0);
                entity.setId(valueId);
                return entity;
            });
            when(valueRepository.countByDimensionId(dimensionId)).thenReturn(1L);
            when(dimensionRepository.findById(dimensionId)).thenReturn(Optional.of(sampleDimension));
            when(dimensionRepository.save(any())).thenReturn(sampleDimension);

            DimensionValueEntity result = dimensionService.addValue(dimensionId, sampleValue);

            assertNotNull(result);
            assertEquals(dimensionId, result.getDimensionId());
            assertNotNull(result.getCreatedAt());
        }

        @Test
        @DisplayName("Should throw exception when code already exists")
        void addValue_WithDuplicateCode_ThrowsException() {
            when(valueRepository.existsByDimensionIdAndCode(dimensionId, "2024")).thenReturn(true);

            assertThrows(IllegalArgumentException.class, () -> 
                dimensionService.addValue(dimensionId, sampleValue)
            );
        }
    }

    @Nested
    @DisplayName("addValues Tests")
    class AddValuesTests {

        @Test
        @DisplayName("Should add multiple values successfully")
        void addValues_WithValidData_AddsAllValues() {
            List<DimensionValueEntity> values = List.of(sampleValue);
            when(valueRepository.saveAll(anyList())).thenReturn(values);
            when(valueRepository.countByDimensionId(dimensionId)).thenReturn(1L);
            when(dimensionRepository.findById(dimensionId)).thenReturn(Optional.of(sampleDimension));
            when(dimensionRepository.save(any())).thenReturn(sampleDimension);

            List<DimensionValueEntity> result = dimensionService.addValues(dimensionId, values);

            assertEquals(1, result.size());
            verify(valueRepository).saveAll(anyList());
        }
    }

    @Nested
    @DisplayName("updateValue Tests")
    class UpdateValueTests {

        @Test
        @DisplayName("Should update value successfully")
        void updateValue_WithValidData_UpdatesValue() {
            when(valueRepository.findById(valueId)).thenReturn(Optional.of(sampleValue));
            when(valueRepository.save(any(DimensionValueEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            DimensionValueEntity updates = new DimensionValueEntity();
            updates.setLabel("Updated Label");
            updates.setDescription("Updated Description");

            DimensionValueEntity result = dimensionService.updateValue(valueId, updates);

            assertEquals("Updated Label", result.getLabel());
            assertEquals("Updated Description", result.getDescription());
            assertNotNull(result.getUpdatedAt());
        }

        @Test
        @DisplayName("Should throw exception when value not found")
        void updateValue_WhenNotFound_ThrowsException() {
            when(valueRepository.findById(valueId)).thenReturn(Optional.empty());

            assertThrows(NoSuchElementException.class, () -> 
                dimensionService.updateValue(valueId, new DimensionValueEntity())
            );
        }
    }

    @Nested
    @DisplayName("deleteValue Tests")
    class DeleteValueTests {

        @Test
        @DisplayName("Should delete value and update count")
        void deleteValue_DeletesValueAndUpdatesCount() {
            when(valueRepository.findById(valueId)).thenReturn(Optional.of(sampleValue));
            when(valueRepository.countByDimensionId(dimensionId)).thenReturn(0L);
            when(dimensionRepository.findById(dimensionId)).thenReturn(Optional.of(sampleDimension));
            when(dimensionRepository.save(any())).thenReturn(sampleDimension);

            dimensionService.deleteValue(valueId);

            verify(valueRepository).deleteById(valueId);
        }

        @Test
        @DisplayName("Should throw exception when value not found")
        void deleteValue_WhenNotFound_ThrowsException() {
            when(valueRepository.findById(valueId)).thenReturn(Optional.empty());

            assertThrows(NoSuchElementException.class, () -> 
                dimensionService.deleteValue(valueId)
            );
        }
    }

    @Nested
    @DisplayName("importFromCsv Tests")
    class ImportFromCsvTests {

        @Test
        @DisplayName("Should import values from CSV")
        void importFromCsv_WithValidCsv_ImportsValues() {
            String csvContent = "code,label,uri\n2023,Year 2023,http://example.org/2023\n2024,Year 2024,http://example.org/2024";
            
            when(valueRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
            when(valueRepository.countByDimensionId(dimensionId)).thenReturn(2L);
            when(dimensionRepository.findById(dimensionId)).thenReturn(Optional.of(sampleDimension));
            when(dimensionRepository.save(any())).thenReturn(sampleDimension);

            List<DimensionValueEntity> result = dimensionService.importFromCsv(dimensionId, csvContent);

            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("Should handle CSV with default column positions")
        void importFromCsv_WithDefaultColumns_ImportsCorrectly() {
            String csvContent = "header1,header2\nCODE1,Label 1\nCODE2,Label 2";
            
            when(valueRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));
            when(valueRepository.countByDimensionId(dimensionId)).thenReturn(2L);
            when(dimensionRepository.findById(dimensionId)).thenReturn(Optional.of(sampleDimension));
            when(dimensionRepository.save(any())).thenReturn(sampleDimension);

            List<DimensionValueEntity> result = dimensionService.importFromCsv(dimensionId, csvContent);

            assertEquals(2, result.size());
            assertEquals("CODE1", result.get(0).getCode());
        }
    }

    @Nested
    @DisplayName("exportToTurtle Tests")
    class ExportToTurtleTests {

        @Test
        @DisplayName("Should export dimension to Turtle format")
        void exportToTurtle_ReturnsValidTurtle() {
            when(dimensionRepository.findById(dimensionId)).thenReturn(Optional.of(sampleDimension));
            when(valueRepository.findActiveValuesByDimensionId(dimensionId)).thenReturn(List.of(sampleValue));

            String result = dimensionService.exportToTurtle(dimensionId);

            assertNotNull(result);
            assertTrue(result.contains("@prefix skos:"));
            assertTrue(result.contains("skos:ConceptScheme"));
            assertTrue(result.contains("skos:Concept"));
        }

        @Test
        @DisplayName("Should throw exception when dimension not found")
        void exportToTurtle_WhenNotFound_ThrowsException() {
            when(dimensionRepository.findById(dimensionId)).thenReturn(Optional.empty());

            assertThrows(NoSuchElementException.class, () -> 
                dimensionService.exportToTurtle(dimensionId)
            );
        }
    }

    @Nested
    @DisplayName("getHierarchyTree Tests")
    class GetHierarchyTreeTests {

        @Test
        @DisplayName("Should return hierarchy tree")
        void getHierarchyTree_ReturnsTreeStructure() {
            when(valueRepository.findByDimensionIdAndParentIdIsNull(dimensionId))
                .thenReturn(List.of(sampleValue));
            when(valueRepository.findByParentId(sampleValue.getId()))
                .thenReturn(List.of());

            List<DimensionValueEntity> result = dimensionService.getHierarchyTree(dimensionId);

            assertNotNull(result);
            assertEquals(1, result.size());
        }
    }

    @Nested
    @DisplayName("lookupValue Tests")
    class LookupValueTests {

        @Test
        @DisplayName("Should find value by code")
        void lookupValue_ByCode_ReturnsValue() {
            when(valueRepository.findByDimensionIdAndCode(dimensionId, "2024"))
                .thenReturn(Optional.of(sampleValue));

            Optional<DimensionValueEntity> result = dimensionService.lookupValue(dimensionId, "2024");

            assertTrue(result.isPresent());
            assertEquals("2024", result.get().getCode());
        }

        @Test
        @DisplayName("Should find value by URI when code not found")
        void lookupValue_ByUri_ReturnsValue() {
            String uri = "http://example.org/dimensions/time/2024";
            when(valueRepository.findByDimensionIdAndCode(dimensionId, uri))
                .thenReturn(Optional.empty());
            when(valueRepository.findByDimensionIdAndUri(dimensionId, uri))
                .thenReturn(Optional.of(sampleValue));

            Optional<DimensionValueEntity> result = dimensionService.lookupValue(dimensionId, uri);

            assertTrue(result.isPresent());
        }
    }

    @Nested
    @DisplayName("getStats Tests")
    class GetStatsTests {

        @Test
        @DisplayName("Should return dimension statistics")
        void getStats_ReturnsStatistics() {
            when(dimensionRepository.countByProjectId(projectId)).thenReturn(5L);
            when(dimensionRepository.sumValueCountByProjectId(projectId)).thenReturn(100L);
            when(dimensionRepository.findByProjectId(projectId)).thenReturn(List.of(sampleDimension));

            Map<String, Object> result = dimensionService.getStats(projectId);

            assertNotNull(result);
            assertEquals(5L, result.get("dimensionCount"));
            assertEquals(100L, result.get("totalValues"));
            assertNotNull(result.get("byType"));
        }
    }
}
