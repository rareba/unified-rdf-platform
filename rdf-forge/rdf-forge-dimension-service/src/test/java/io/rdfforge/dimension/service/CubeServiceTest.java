package io.rdfforge.dimension.service;

import io.rdfforge.common.exception.ResourceNotFoundException;
import io.rdfforge.common.model.Pipeline;
import io.rdfforge.common.model.Shape;
import io.rdfforge.dimension.entity.CubeEntity;
import io.rdfforge.dimension.repository.CubeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive tests for CubeService.
 * Tests cube operations, shape/pipeline generation, and linking.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CubeService Tests")
class CubeServiceTest {

    @Mock
    private CubeRepository cubeRepository;

    @Mock
    private RestTemplateBuilder restTemplateBuilder;

    @Mock
    private RestTemplate restTemplate;

    private CubeService cubeService;

    private UUID cubeId;
    private UUID projectId;
    private UUID shapeId;
    private UUID pipelineId;
    private UUID triplestoreId;
    private UUID dataSourceId;
    private CubeEntity sampleCube;

    @BeforeEach
    void setUp() {
        when(restTemplateBuilder.connectTimeout(any())).thenReturn(restTemplateBuilder);
        when(restTemplateBuilder.readTimeout(any())).thenReturn(restTemplateBuilder);
        when(restTemplateBuilder.build()).thenReturn(restTemplate);
        cubeService = new CubeService(cubeRepository, restTemplateBuilder);

        cubeId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        shapeId = UUID.randomUUID();
        pipelineId = UUID.randomUUID();
        triplestoreId = UUID.randomUUID();
        dataSourceId = UUID.randomUUID();

        sampleCube = new CubeEntity();
        sampleCube.setId(cubeId);
        sampleCube.setProjectId(projectId);
        sampleCube.setUri("http://example.org/cubes/sales");
        sampleCube.setName("Sales Cube");
        sampleCube.setDescription("Sales data cube");
        sampleCube.setGraphUri("http://example.org/graphs/sales");
        sampleCube.setShapeId(shapeId);
        sampleCube.setPipelineId(pipelineId);
        sampleCube.setTriplestoreId(triplestoreId);
        sampleCube.setSourceDataId(dataSourceId);
        sampleCube.setObservationCount(1000L);
        sampleCube.setLastPublished(Instant.now());
        sampleCube.setCreatedAt(Instant.now());
        sampleCube.setMetadata(Map.of(
            "columnMappings", List.of(
                Map.of("name", "date", "role", "dimension", "datatype", "xsd:date"),
                Map.of("name", "amount", "role", "measure", "datatype", "xsd:decimal")
            )
        ));
    }

    @Nested
    @DisplayName("search Tests")
    class SearchTests {

        @Test
        @DisplayName("Should search cubes with filters")
        void search_WithFilters_ReturnsPage() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<CubeEntity> page = new PageImpl<>(List.of(sampleCube), pageable, 1);
            when(cubeRepository.search(projectId, "sales", pageable)).thenReturn(page);

            Page<CubeEntity> result = cubeService.search(projectId, "sales", pageable);

            assertEquals(1, result.getTotalElements());
            assertEquals("Sales Cube", result.getContent().get(0).getName());
        }
    }

    @Nested
    @DisplayName("findById Tests")
    class FindByIdTests {

        @Test
        @DisplayName("Should return cube when found")
        void findById_WhenFound_ReturnsCube() {
            when(cubeRepository.findById(cubeId)).thenReturn(Optional.of(sampleCube));

            Optional<CubeEntity> result = cubeService.findById(cubeId);

            assertTrue(result.isPresent());
            assertEquals(cubeId, result.get().getId());
        }

        @Test
        @DisplayName("Should return empty when not found")
        void findById_WhenNotFound_ReturnsEmpty() {
            when(cubeRepository.findById(cubeId)).thenReturn(Optional.empty());

            Optional<CubeEntity> result = cubeService.findById(cubeId);

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("create Tests")
    class CreateTests {

        @Test
        @DisplayName("Should create cube successfully")
        void create_WithValidData_CreatesCube() {
            CubeEntity newCube = new CubeEntity();
            newCube.setName("New Cube");
            newCube.setUri("http://example.org/cubes/new");

            when(cubeRepository.save(any(CubeEntity.class))).thenAnswer(inv -> {
                CubeEntity entity = inv.getArgument(0);
                entity.setId(cubeId);
                return entity;
            });

            CubeEntity result = cubeService.create(newCube);

            assertNotNull(result);
            assertEquals(cubeId, result.getId());
            assertNotNull(result.getCreatedAt());
        }
    }

    @Nested
    @DisplayName("update Tests")
    class UpdateTests {

        @Test
        @DisplayName("Should update cube successfully")
        void update_WithValidData_UpdatesCube() {
            when(cubeRepository.findById(cubeId)).thenReturn(Optional.of(sampleCube));
            when(cubeRepository.save(any(CubeEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            CubeEntity updates = new CubeEntity();
            updates.setName("Updated Cube");
            updates.setDescription("Updated description");
            updates.setGraphUri("http://example.org/graphs/updated");

            CubeEntity result = cubeService.update(cubeId, updates);

            assertEquals("Updated Cube", result.getName());
            assertEquals("Updated description", result.getDescription());
            assertEquals("http://example.org/graphs/updated", result.getGraphUri());
            assertNotNull(result.getUpdatedAt());
        }

        @Test
        @DisplayName("Should update only provided fields")
        void update_PartialUpdate_UpdatesOnlyProvidedFields() {
            when(cubeRepository.findById(cubeId)).thenReturn(Optional.of(sampleCube));
            when(cubeRepository.save(any(CubeEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            CubeEntity updates = new CubeEntity();
            updates.setName("Updated Name Only");

            CubeEntity result = cubeService.update(cubeId, updates);

            assertEquals("Updated Name Only", result.getName());
            assertEquals("Sales data cube", result.getDescription()); // Unchanged
            assertEquals("http://example.org/graphs/sales", result.getGraphUri()); // Unchanged
        }

        @Test
        @DisplayName("Should throw exception when cube not found")
        void update_WhenNotFound_ThrowsException() {
            when(cubeRepository.findById(cubeId)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () ->
                cubeService.update(cubeId, new CubeEntity())
            );
        }
    }

    @Nested
    @DisplayName("delete Tests")
    class DeleteTests {

        @Test
        @DisplayName("Should delete cube")
        void delete_DeletesCube() {
            cubeService.delete(cubeId);

            verify(cubeRepository).deleteById(cubeId);
        }
    }

    @Nested
    @DisplayName("markPublished Tests")
    class MarkPublishedTests {

        @Test
        @DisplayName("Should mark cube as published with observation count")
        void markPublished_SetsPublishedDate() {
            when(cubeRepository.findById(cubeId)).thenReturn(Optional.of(sampleCube));
            when(cubeRepository.save(any(CubeEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            CubeEntity result = cubeService.markPublished(cubeId, 5000L);

            assertNotNull(result.getLastPublished());
            assertEquals(5000L, result.getObservationCount());
            assertNotNull(result.getUpdatedAt());
        }

        @Test
        @DisplayName("Should mark cube as published without observation count")
        void markPublished_WithoutCount_SetsPublishedDate() {
            when(cubeRepository.findById(cubeId)).thenReturn(Optional.of(sampleCube));
            when(cubeRepository.save(any(CubeEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            CubeEntity result = cubeService.markPublished(cubeId, null);

            assertNotNull(result.getLastPublished());
            // Observation count should remain unchanged
            assertEquals(1000L, result.getObservationCount());
        }
    }

    @Nested
    @DisplayName("linkShape Tests")
    class LinkShapeTests {

        @Test
        @DisplayName("Should link shape to cube")
        void linkShape_LinksShape() {
            UUID newShapeId = UUID.randomUUID();
            when(cubeRepository.findById(cubeId)).thenReturn(Optional.of(sampleCube));
            when(cubeRepository.save(any(CubeEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            CubeEntity result = cubeService.linkShape(cubeId, newShapeId);

            assertEquals(newShapeId, result.getShapeId());
            assertNotNull(result.getUpdatedAt());
        }
    }

    @Nested
    @DisplayName("linkPipeline Tests")
    class LinkPipelineTests {

        @Test
        @DisplayName("Should link pipeline to cube")
        void linkPipeline_LinksPipeline() {
            UUID newPipelineId = UUID.randomUUID();
            when(cubeRepository.findById(cubeId)).thenReturn(Optional.of(sampleCube));
            when(cubeRepository.save(any(CubeEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            CubeEntity result = cubeService.linkPipeline(cubeId, newPipelineId);

            assertEquals(newPipelineId, result.getPipelineId());
            assertNotNull(result.getUpdatedAt());
        }
    }

    @Nested
    @DisplayName("unlinkShape Tests")
    class UnlinkShapeTests {

        @Test
        @DisplayName("Should unlink shape from cube")
        void unlinkShape_RemovesShapeLink() {
            when(cubeRepository.findById(cubeId)).thenReturn(Optional.of(sampleCube));
            when(cubeRepository.save(any(CubeEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            CubeEntity result = cubeService.unlinkShape(cubeId);

            assertNull(result.getShapeId());
            assertNotNull(result.getUpdatedAt());
        }
    }

    @Nested
    @DisplayName("unlinkPipeline Tests")
    class UnlinkPipelineTests {

        @Test
        @DisplayName("Should unlink pipeline from cube")
        void unlinkPipeline_RemovesPipelineLink() {
            when(cubeRepository.findById(cubeId)).thenReturn(Optional.of(sampleCube));
            when(cubeRepository.save(any(CubeEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            CubeEntity result = cubeService.unlinkPipeline(cubeId);

            assertNull(result.getPipelineId());
            assertNotNull(result.getUpdatedAt());
        }
    }

    @Nested
    @DisplayName("generateShape Tests")
    class GenerateShapeTests {

        @Test
        @DisplayName("Should generate shape from cube definition")
        void generateShape_CreatesShape() {
            Shape createdShape = Shape.builder()
                .id(UUID.randomUUID())
                .name("Sales Cube Validation Shape")
                .projectId(projectId)
                .build();

            when(cubeRepository.findById(cubeId)).thenReturn(Optional.of(sampleCube));
            when(restTemplate.postForObject(anyString(), any(Shape.class), eq(Shape.class)))
                .thenReturn(createdShape);
            when(cubeRepository.save(any(CubeEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            var result = cubeService.generateShape(cubeId, "Custom Shape Name", "http://example.org/CustomClass");

            assertNotNull(result);
            assertEquals("SHACL_SHAPE", result.type());

            ArgumentCaptor<Shape> shapeCaptor = ArgumentCaptor.forClass(Shape.class);
            verify(restTemplate).postForObject(anyString(), shapeCaptor.capture(), eq(Shape.class));

            Shape sentShape = shapeCaptor.getValue();
            assertEquals("Custom Shape Name", sentShape.getName());
            assertEquals("http://example.org/CustomClass", sentShape.getTargetClass());
            assertTrue(sentShape.getContent().contains("sh:NodeShape"));
        }

        @Test
        @DisplayName("Should use default shape name when not provided")
        void generateShape_DefaultName_UsesCubeName() {
            when(cubeRepository.findById(cubeId)).thenReturn(Optional.of(sampleCube));
            when(restTemplate.postForObject(anyString(), any(), eq(Shape.class)))
                .thenReturn(Shape.builder().id(UUID.randomUUID()).build());
            when(cubeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            cubeService.generateShape(cubeId, null, null);

            ArgumentCaptor<Shape> shapeCaptor = ArgumentCaptor.forClass(Shape.class);
            verify(restTemplate).postForObject(anyString(), shapeCaptor.capture(), eq(Shape.class));

            assertTrue(shapeCaptor.getValue().getName().contains("Sales Cube"));
            assertEquals("http://purl.org/linked-data/cube#Observation",
                shapeCaptor.getValue().getTargetClass());
        }

        @Test
        @DisplayName("Should throw exception when cube not found")
        void generateShape_CubeNotFound_ThrowsException() {
            when(cubeRepository.findById(cubeId)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () ->
                cubeService.generateShape(cubeId, "Shape", "http://example.org/Class")
            );
        }

        @Test
        @DisplayName("Should throw exception when SHACL service fails")
        void generateShape_ServiceFailure_ThrowsException() {
            when(cubeRepository.findById(cubeId)).thenReturn(Optional.of(sampleCube));
            when(restTemplate.postForObject(anyString(), any(), eq(Shape.class)))
                .thenThrow(new RuntimeException("Service unavailable"));

            RuntimeException exception = assertThrows(RuntimeException.class, () ->
                cubeService.generateShape(cubeId, "Shape", "http://example.org/Class")
            );

            assertTrue(exception.getMessage().contains("Failed to create shape"));
        }

        @Test
        @DisplayName("Should throw exception when service returns null")
        void generateShape_NullResponse_ThrowsException() {
            when(cubeRepository.findById(cubeId)).thenReturn(Optional.of(sampleCube));
            when(restTemplate.postForObject(anyString(), any(), eq(Shape.class)))
                .thenReturn(null);

            RuntimeException exception = assertThrows(RuntimeException.class, () ->
                cubeService.generateShape(cubeId, "Shape", "http://example.org/Class")
            );

            assertTrue(exception.getMessage().contains("returned null"));
        }
    }

    @Nested
    @DisplayName("generatePipeline Tests")
    class GeneratePipelineTests {

        @Test
        @DisplayName("Should generate pipeline from cube definition")
        void generatePipeline_CreatesPipeline() {
            Pipeline createdPipeline = Pipeline.builder()
                .id(UUID.randomUUID())
                .name("Pipeline: Sales Cube")
                .projectId(projectId)
                .build();

            when(cubeRepository.findById(cubeId)).thenReturn(Optional.of(sampleCube));
            when(restTemplate.postForObject(anyString(), any(Pipeline.class), eq(Pipeline.class)))
                .thenReturn(createdPipeline);
            when(cubeRepository.save(any(CubeEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            var result = cubeService.generatePipeline(cubeId, "Custom Pipeline", triplestoreId,
                "http://example.org/graphs/custom");

            assertNotNull(result);
            assertEquals("PIPELINE", result.type());

            ArgumentCaptor<Pipeline> pipelineCaptor = ArgumentCaptor.forClass(Pipeline.class);
            verify(restTemplate).postForObject(anyString(), pipelineCaptor.capture(), eq(Pipeline.class));

            Pipeline sentPipeline = pipelineCaptor.getValue();
            assertEquals("Custom Pipeline", sentPipeline.getName());
            assertTrue(sentPipeline.getDefinition().contains("load-csv"));
            assertTrue(sentPipeline.getDefinition().contains("create-observation"));
        }

        @Test
        @DisplayName("Should include validate-shacl step when shape exists")
        void generatePipeline_WithShape_IncludesValidationStep() {
            when(cubeRepository.findById(cubeId)).thenReturn(Optional.of(sampleCube));
            when(restTemplate.postForObject(anyString(), any(), eq(Pipeline.class)))
                .thenReturn(Pipeline.builder().id(UUID.randomUUID()).build());
            when(cubeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            cubeService.generatePipeline(cubeId, "Pipeline", triplestoreId, "http://example.org/graph");

            ArgumentCaptor<Pipeline> pipelineCaptor = ArgumentCaptor.forClass(Pipeline.class);
            verify(restTemplate).postForObject(anyString(), pipelineCaptor.capture(), eq(Pipeline.class));

            assertTrue(pipelineCaptor.getValue().getDefinition().contains("validate-shacl"));
        }

        @Test
        @DisplayName("Should use cube graph URI when not provided")
        void generatePipeline_NullGraphUri_UsesCubeGraphUri() {
            when(cubeRepository.findById(cubeId)).thenReturn(Optional.of(sampleCube));
            when(restTemplate.postForObject(anyString(), any(), eq(Pipeline.class)))
                .thenReturn(Pipeline.builder().id(UUID.randomUUID()).build());
            when(cubeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            cubeService.generatePipeline(cubeId, "Pipeline", triplestoreId, null);

            ArgumentCaptor<Pipeline> pipelineCaptor = ArgumentCaptor.forClass(Pipeline.class);
            verify(restTemplate).postForObject(anyString(), pipelineCaptor.capture(), eq(Pipeline.class));

            assertTrue(pipelineCaptor.getValue().getDefinition()
                .contains("http://example.org/graphs/sales"));
        }

        @Test
        @DisplayName("Should throw exception when cube not found")
        void generatePipeline_CubeNotFound_ThrowsException() {
            when(cubeRepository.findById(cubeId)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () ->
                cubeService.generatePipeline(cubeId, "Pipeline", triplestoreId, "http://example.org/graph")
            );
        }
    }
}
