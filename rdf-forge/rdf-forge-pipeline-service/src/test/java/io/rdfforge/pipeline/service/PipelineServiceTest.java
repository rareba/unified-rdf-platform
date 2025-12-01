package io.rdfforge.pipeline.service;

import io.rdfforge.common.exception.PipelineValidationException;
import io.rdfforge.common.exception.ResourceNotFoundException;
import io.rdfforge.common.model.Pipeline;
import io.rdfforge.engine.operation.OperationRegistry;
import io.rdfforge.pipeline.entity.PipelineEntity;
import io.rdfforge.pipeline.repository.PipelineRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PipelineService Tests")
class PipelineServiceTest {

    @Mock
    private PipelineRepository pipelineRepository;

    @Mock
    private OperationRegistry operationRegistry;

    @InjectMocks
    private PipelineService pipelineService;

    private UUID projectId;
    private UUID pipelineId;
    private UUID userId;
    private PipelineEntity sampleEntity;
    private Pipeline samplePipeline;

    @BeforeEach
    void setUp() {
        projectId = UUID.randomUUID();
        pipelineId = UUID.randomUUID();
        userId = UUID.randomUUID();

        sampleEntity = PipelineEntity.builder()
            .id(pipelineId)
            .projectId(projectId)
            .name("Test Pipeline")
            .description("A test pipeline")
            .definitionFormat("JSON")
            .definition("{\"steps\": [{\"id\": \"step1\", \"operation\": \"CSV_READ\", \"name\": \"Read CSV\"}]}")
            .variables(Map.of("key", "value"))
            .version(1)
            .isTemplate(false)
            .createdBy(userId)
            .createdAt(Instant.now())
            .build();

        samplePipeline = Pipeline.builder()
            .id(pipelineId)
            .projectId(projectId)
            .name("Test Pipeline")
            .description("A test pipeline")
            .definitionFormat(Pipeline.DefinitionFormat.JSON)
            .definition("{\"steps\": [{\"id\": \"step1\", \"operation\": \"CSV_READ\", \"name\": \"Read CSV\"}]}")
            .variables(Map.of("key", "value"))
            .version(1)
            .template(false)
            .createdBy(userId)
            .build();
    }

    @Nested
    @DisplayName("findById Tests")
    class FindByIdTests {
        
        @Test
        @DisplayName("Should return pipeline when found")
        void findById_WhenFound_ReturnsPipeline() {
            when(pipelineRepository.findById(pipelineId)).thenReturn(Optional.of(sampleEntity));

            Pipeline result = pipelineService.getById(pipelineId);

            assertNotNull(result);
            assertEquals(pipelineId, result.getId());
            assertEquals("Test Pipeline", result.getName());
            verify(pipelineRepository).findById(pipelineId);
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when not found")
        void findById_WhenNotFound_ThrowsException() {
            when(pipelineRepository.findById(pipelineId)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () -> 
                pipelineService.getById(pipelineId)
            );
        }
    }

    @Nested
    @DisplayName("create Tests")
    class CreateTests {

        @Test
        @DisplayName("Should create pipeline successfully with valid definition")
        void create_WithValidPipeline_ReturnsCreatedPipeline() {
            when(operationRegistry.get("CSV_READ")).thenReturn(Optional.of(mock(Object.class)));
            when(pipelineRepository.save(any(PipelineEntity.class))).thenReturn(sampleEntity);

            Pipeline result = pipelineService.create(samplePipeline);

            assertNotNull(result);
            assertEquals("Test Pipeline", result.getName());
            verify(pipelineRepository).save(any(PipelineEntity.class));
        }

        @Test
        @DisplayName("Should throw PipelineValidationException when name is blank")
        void create_WithBlankName_ThrowsValidationException() {
            Pipeline invalidPipeline = Pipeline.builder()
                .name("")
                .definition("{\"steps\": []}")
                .definitionFormat(Pipeline.DefinitionFormat.JSON)
                .build();

            assertThrows(PipelineValidationException.class, () -> 
                pipelineService.create(invalidPipeline)
            );
        }

        @Test
        @DisplayName("Should throw PipelineValidationException when definition is null")
        void create_WithNullDefinition_ThrowsValidationException() {
            Pipeline invalidPipeline = Pipeline.builder()
                .name("Test")
                .definition(null)
                .definitionFormat(Pipeline.DefinitionFormat.JSON)
                .build();

            assertThrows(PipelineValidationException.class, () -> 
                pipelineService.create(invalidPipeline)
            );
        }

        @Test
        @DisplayName("Should throw PipelineValidationException when steps array is empty")
        void create_WithEmptySteps_ThrowsValidationException() {
            Pipeline invalidPipeline = Pipeline.builder()
                .name("Test")
                .definition("{\"steps\": []}")
                .definitionFormat(Pipeline.DefinitionFormat.JSON)
                .build();

            assertThrows(PipelineValidationException.class, () -> 
                pipelineService.create(invalidPipeline)
            );
        }

        @Test
        @DisplayName("Should throw PipelineValidationException for unknown operation type")
        void create_WithUnknownOperationType_ThrowsValidationException() {
            Pipeline pipeline = Pipeline.builder()
                .name("Test")
                .definition("{\"steps\": [{\"id\": \"step1\", \"operation\": \"UNKNOWN_OP\"}]}")
                .definitionFormat(Pipeline.DefinitionFormat.JSON)
                .build();

            when(operationRegistry.get("UNKNOWN_OP")).thenReturn(Optional.empty());

            assertThrows(PipelineValidationException.class, () -> 
                pipelineService.create(pipeline)
            );
        }

        @Test
        @DisplayName("Should throw PipelineValidationException for duplicate step IDs")
        void create_WithDuplicateStepIds_ThrowsValidationException() {
            Pipeline pipeline = Pipeline.builder()
                .name("Test")
                .definition("{\"steps\": [{\"id\": \"step1\", \"operation\": \"CSV_READ\"}, {\"id\": \"step1\", \"operation\": \"CSV_READ\"}]}")
                .definitionFormat(Pipeline.DefinitionFormat.JSON)
                .build();

            when(operationRegistry.get("CSV_READ")).thenReturn(Optional.of(mock(Object.class)));

            assertThrows(PipelineValidationException.class, () -> 
                pipelineService.create(pipeline)
            );
        }
    }

    @Nested
    @DisplayName("list Tests")
    class ListTests {

        @Test
        @DisplayName("Should return page of pipelines for project")
        void list_ReturnsPageOfPipelines() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<PipelineEntity> entityPage = new PageImpl<>(List.of(sampleEntity), pageable, 1);
            
            when(pipelineRepository.findByProjectId(projectId, pageable)).thenReturn(entityPage);

            Page<Pipeline> result = pipelineService.list(projectId, pageable);

            assertNotNull(result);
            assertEquals(1, result.getTotalElements());
            assertEquals("Test Pipeline", result.getContent().get(0).getName());
        }

        @Test
        @DisplayName("Should return empty page when no pipelines exist")
        void list_WhenEmpty_ReturnsEmptyPage() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<PipelineEntity> emptyPage = new PageImpl<>(List.of(), pageable, 0);
            
            when(pipelineRepository.findByProjectId(projectId, pageable)).thenReturn(emptyPage);

            Page<Pipeline> result = pipelineService.list(projectId, pageable);

            assertNotNull(result);
            assertEquals(0, result.getTotalElements());
        }
    }

    @Nested
    @DisplayName("search Tests")
    class SearchTests {

        @Test
        @DisplayName("Should return matching pipelines for search query")
        void search_WithQuery_ReturnsMatchingPipelines() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<PipelineEntity> entityPage = new PageImpl<>(List.of(sampleEntity), pageable, 1);
            
            when(pipelineRepository.searchByProjectId(projectId, "Test", pageable)).thenReturn(entityPage);

            Page<Pipeline> result = pipelineService.search(projectId, "Test", pageable);

            assertNotNull(result);
            assertEquals(1, result.getTotalElements());
        }
    }

    @Nested
    @DisplayName("update Tests")
    class UpdateTests {

        @Test
        @DisplayName("Should update pipeline successfully")
        void update_WithValidData_ReturnsUpdatedPipeline() {
            when(pipelineRepository.findById(pipelineId)).thenReturn(Optional.of(sampleEntity));
            when(operationRegistry.get("CSV_READ")).thenReturn(Optional.of(mock(Object.class)));
            
            PipelineEntity updatedEntity = PipelineEntity.builder()
                .id(pipelineId)
                .projectId(projectId)
                .name("Updated Pipeline")
                .description("Updated description")
                .definitionFormat("JSON")
                .definition("{\"steps\": [{\"id\": \"step1\", \"operation\": \"CSV_READ\", \"name\": \"Read CSV\"}]}")
                .variables(Map.of("key", "value"))
                .version(2)
                .createdBy(userId)
                .build();
            
            when(pipelineRepository.save(any(PipelineEntity.class))).thenReturn(updatedEntity);

            Pipeline updateRequest = Pipeline.builder()
                .name("Updated Pipeline")
                .description("Updated description")
                .definitionFormat(Pipeline.DefinitionFormat.JSON)
                .definition("{\"steps\": [{\"id\": \"step1\", \"operation\": \"CSV_READ\", \"name\": \"Read CSV\"}]}")
                .variables(Map.of("key", "value"))
                .build();

            Pipeline result = pipelineService.update(pipelineId, updateRequest);

            assertNotNull(result);
            assertEquals("Updated Pipeline", result.getName());
            assertEquals(2, result.getVersion());
            verify(pipelineRepository).save(any(PipelineEntity.class));
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when pipeline doesn't exist")
        void update_WhenNotFound_ThrowsException() {
            when(pipelineRepository.findById(pipelineId)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () -> 
                pipelineService.update(pipelineId, samplePipeline)
            );
        }

        @Test
        @DisplayName("Should increment version on update")
        void update_ShouldIncrementVersion() {
            when(pipelineRepository.findById(pipelineId)).thenReturn(Optional.of(sampleEntity));
            when(operationRegistry.get("CSV_READ")).thenReturn(Optional.of(mock(Object.class)));
            
            ArgumentCaptor<PipelineEntity> captor = ArgumentCaptor.forClass(PipelineEntity.class);
            when(pipelineRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

            pipelineService.update(pipelineId, samplePipeline);

            assertEquals(2, captor.getValue().getVersion());
        }
    }

    @Nested
    @DisplayName("delete Tests")
    class DeleteTests {

        @Test
        @DisplayName("Should delete pipeline successfully")
        void delete_WhenExists_DeletesPipeline() {
            when(pipelineRepository.existsById(pipelineId)).thenReturn(true);

            assertDoesNotThrow(() -> pipelineService.delete(pipelineId));

            verify(pipelineRepository).deleteById(pipelineId);
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when pipeline doesn't exist")
        void delete_WhenNotFound_ThrowsException() {
            when(pipelineRepository.existsById(pipelineId)).thenReturn(false);

            assertThrows(ResourceNotFoundException.class, () -> 
                pipelineService.delete(pipelineId)
            );
        }
    }

    @Nested
    @DisplayName("getTemplates Tests")
    class GetTemplatesTests {

        @Test
        @DisplayName("Should return list of template pipelines")
        void getTemplates_ReturnsTemplates() {
            PipelineEntity templateEntity = PipelineEntity.builder()
                .id(UUID.randomUUID())
                .name("Template Pipeline")
                .isTemplate(true)
                .definitionFormat("JSON")
                .definition("{\"steps\": []}")
                .build();
            
            when(pipelineRepository.findByIsTemplateTrue()).thenReturn(List.of(templateEntity));

            List<Pipeline> result = pipelineService.getTemplates();

            assertNotNull(result);
            assertEquals(1, result.size());
            assertTrue(result.get(0).isTemplate());
        }
    }

    @Nested
    @DisplayName("duplicate Tests")
    class DuplicateTests {

        @Test
        @DisplayName("Should duplicate pipeline with new name")
        void duplicate_WithNewName_CreatesCopy() {
            when(pipelineRepository.findById(pipelineId)).thenReturn(Optional.of(sampleEntity));
            when(operationRegistry.get("CSV_READ")).thenReturn(Optional.of(mock(Object.class)));
            
            PipelineEntity duplicatedEntity = PipelineEntity.builder()
                .id(UUID.randomUUID())
                .name("Duplicated Pipeline")
                .projectId(projectId)
                .definitionFormat("JSON")
                .definition(sampleEntity.getDefinition())
                .variables(new HashMap<>(sampleEntity.getVariables()))
                .isTemplate(false)
                .build();
            
            when(pipelineRepository.save(any(PipelineEntity.class))).thenReturn(duplicatedEntity);

            Pipeline result = pipelineService.duplicate(pipelineId, "Duplicated Pipeline");

            assertNotNull(result);
            assertEquals("Duplicated Pipeline", result.getName());
            assertFalse(result.isTemplate());
        }

        @Test
        @DisplayName("Should duplicate pipeline with default name when new name is null")
        void duplicate_WithNullName_UsesDefaultName() {
            when(pipelineRepository.findById(pipelineId)).thenReturn(Optional.of(sampleEntity));
            when(operationRegistry.get("CSV_READ")).thenReturn(Optional.of(mock(Object.class)));
            
            ArgumentCaptor<PipelineEntity> captor = ArgumentCaptor.forClass(PipelineEntity.class);
            when(pipelineRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

            pipelineService.duplicate(pipelineId, null);

            assertTrue(captor.getValue().getName().endsWith(" (copy)"));
        }
    }

    @Nested
    @DisplayName("validate Tests")
    class ValidateTests {

        @Test
        @DisplayName("Should detect circular dependencies")
        void validate_WithCircularDependencies_ThrowsException() {
            Pipeline circularPipeline = Pipeline.builder()
                .name("Circular Pipeline")
                .definition("{\"steps\": [{\"id\": \"step1\", \"operation\": \"CSV_READ\", \"inputs\": [\"step2\"]}, {\"id\": \"step2\", \"operation\": \"CSV_READ\", \"inputs\": [\"step1\"]}]}")
                .definitionFormat(Pipeline.DefinitionFormat.JSON)
                .build();

            when(operationRegistry.get("CSV_READ")).thenReturn(Optional.of(mock(Object.class)));

            assertThrows(PipelineValidationException.class, () -> 
                pipelineService.validate(circularPipeline)
            );
        }

        @Test
        @DisplayName("Should detect invalid input references")
        void validate_WithInvalidInputReference_ThrowsException() {
            Pipeline invalidPipeline = Pipeline.builder()
                .name("Invalid Pipeline")
                .definition("{\"steps\": [{\"id\": \"step1\", \"operation\": \"CSV_READ\", \"inputs\": [\"nonexistent\"]}]}")
                .definitionFormat(Pipeline.DefinitionFormat.JSON)
                .build();

            when(operationRegistry.get("CSV_READ")).thenReturn(Optional.of(mock(Object.class)));

            assertThrows(PipelineValidationException.class, () -> 
                pipelineService.validate(invalidPipeline)
            );
        }

        @Test
        @DisplayName("Should validate YAML format definitions")
        void validate_WithYamlFormat_ValidatesSuccessfully() {
            Pipeline yamlPipeline = Pipeline.builder()
                .name("YAML Pipeline")
                .definition("steps:\n  - id: step1\n    operation: CSV_READ")
                .definitionFormat(Pipeline.DefinitionFormat.YAML)
                .build();

            when(operationRegistry.get("CSV_READ")).thenReturn(Optional.of(mock(Object.class)));
            when(pipelineRepository.save(any(PipelineEntity.class))).thenReturn(sampleEntity);

            assertDoesNotThrow(() -> pipelineService.create(yamlPipeline));
        }

        @Test
        @DisplayName("Should throw PipelineValidationException for malformed JSON")
        void validate_WithMalformedJson_ThrowsException() {
            Pipeline malformedPipeline = Pipeline.builder()
                .name("Malformed Pipeline")
                .definition("{invalid json}")
                .definitionFormat(Pipeline.DefinitionFormat.JSON)
                .build();

            assertThrows(PipelineValidationException.class, () -> 
                pipelineService.validate(malformedPipeline)
            );
        }
    }

    @Nested
    @DisplayName("Optional findById Tests")
    class OptionalFindByIdTests {
        
        @Test
        @DisplayName("Repository findById should work correctly")
        void findById_RepositoryMethod_ReturnsOptional() {
            when(pipelineRepository.findById(pipelineId)).thenReturn(Optional.of(sampleEntity));

            Optional<PipelineEntity> result = pipelineRepository.findById(pipelineId);

            assertTrue(result.isPresent());
            assertEquals(pipelineId, result.get().getId());
        }
    }
}
