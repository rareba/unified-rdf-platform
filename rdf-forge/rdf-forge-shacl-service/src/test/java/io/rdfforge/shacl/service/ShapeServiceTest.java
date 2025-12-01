package io.rdfforge.shacl.service;

import io.rdfforge.common.exception.ResourceNotFoundException;
import io.rdfforge.common.exception.ShaclValidationException;
import io.rdfforge.common.model.Shape;
import io.rdfforge.engine.shacl.ShaclValidator;
import io.rdfforge.shacl.entity.ShapeEntity;
import io.rdfforge.shacl.repository.ShapeRepository;
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
@DisplayName("ShapeService Tests")
class ShapeServiceTest {

    @Mock
    private ShapeRepository shapeRepository;

    @Mock
    private ShaclValidator shaclValidator;

    @InjectMocks
    private ShapeService shapeService;

    private UUID projectId;
    private UUID shapeId;
    private UUID userId;
    private ShapeEntity sampleEntity;
    private Shape sampleShape;

    private static final String VALID_SHACL_CONTENT = """
        @prefix sh: <http://www.w3.org/ns/shacl#> .
        @prefix ex: <http://example.org/> .
        ex:PersonShape a sh:NodeShape ;
            sh:targetClass ex:Person ;
            sh:property [
                sh:path ex:name ;
                sh:datatype xsd:string ;
                sh:minCount 1 ;
            ] .
        """;

    @BeforeEach
    void setUp() {
        projectId = UUID.randomUUID();
        shapeId = UUID.randomUUID();
        userId = UUID.randomUUID();

        sampleEntity = ShapeEntity.builder()
            .id(shapeId)
            .projectId(projectId)
            .uri("http://example.org/shapes/PersonShape")
            .name("Person Shape")
            .description("Shape for validating persons")
            .targetClass("http://example.org/Person")
            .contentFormat("TURTLE")
            .content(VALID_SHACL_CONTENT)
            .isTemplate(false)
            .category("Person")
            .tags(new String[]{"person", "validation"})
            .version(1)
            .createdBy(userId)
            .createdAt(Instant.now())
            .build();

        sampleShape = Shape.builder()
            .id(shapeId)
            .projectId(projectId)
            .uri("http://example.org/shapes/PersonShape")
            .name("Person Shape")
            .description("Shape for validating persons")
            .targetClass("http://example.org/Person")
            .contentFormat(Shape.ContentFormat.TURTLE)
            .content(VALID_SHACL_CONTENT)
            .template(false)
            .category("Person")
            .tags(List.of("person", "validation"))
            .version(1)
            .createdBy(userId)
            .build();
    }

    @Nested
    @DisplayName("findById Tests")
    class FindByIdTests {

        @Test
        @DisplayName("Should return shape when found")
        void findById_WhenFound_ReturnsShape() {
            when(shapeRepository.findById(shapeId)).thenReturn(Optional.of(sampleEntity));

            Shape result = shapeService.getById(shapeId);

            assertNotNull(result);
            assertEquals(shapeId, result.getId());
            assertEquals("Person Shape", result.getName());
            verify(shapeRepository).findById(shapeId);
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when not found")
        void findById_WhenNotFound_ThrowsException() {
            when(shapeRepository.findById(shapeId)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () -> 
                shapeService.getById(shapeId)
            );
        }
    }

    @Nested
    @DisplayName("create Tests")
    class CreateTests {

        @Test
        @DisplayName("Should create shape with valid SHACL content")
        void create_WithValidContent_ReturnsCreatedShape() {
            when(shaclValidator.validateSyntax(VALID_SHACL_CONTENT)).thenReturn(true);
            when(shapeRepository.save(any(ShapeEntity.class))).thenReturn(sampleEntity);

            Shape result = shapeService.create(sampleShape);

            assertNotNull(result);
            assertEquals("Person Shape", result.getName());
            assertEquals("http://example.org/shapes/PersonShape", result.getUri());
            verify(shaclValidator).validateSyntax(VALID_SHACL_CONTENT);
            verify(shapeRepository).save(any(ShapeEntity.class));
        }

        @Test
        @DisplayName("Should throw ShaclValidationException for invalid SHACL syntax")
        void create_WithInvalidSyntax_ThrowsValidationException() {
            when(shaclValidator.validateSyntax(any())).thenReturn(false);

            assertThrows(ShaclValidationException.class, () -> 
                shapeService.create(sampleShape)
            );
            verify(shapeRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should create shape with tags")
        void create_WithTags_PreservesTags() {
            when(shaclValidator.validateSyntax(any())).thenReturn(true);
            
            ArgumentCaptor<ShapeEntity> captor = ArgumentCaptor.forClass(ShapeEntity.class);
            when(shapeRepository.save(captor.capture())).thenReturn(sampleEntity);

            shapeService.create(sampleShape);

            assertNotNull(captor.getValue().getTags());
            assertEquals(2, captor.getValue().getTags().length);
        }
    }

    @Nested
    @DisplayName("list Tests")
    class ListTests {

        @Test
        @DisplayName("Should return page of shapes for project")
        void list_ReturnsPageOfShapes() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<ShapeEntity> entityPage = new PageImpl<>(List.of(sampleEntity), pageable, 1);
            
            when(shapeRepository.findByProjectId(projectId, pageable)).thenReturn(entityPage);

            Page<Shape> result = shapeService.list(projectId, pageable);

            assertNotNull(result);
            assertEquals(1, result.getTotalElements());
            assertEquals("Person Shape", result.getContent().get(0).getName());
        }

        @Test
        @DisplayName("Should return empty page when no shapes exist")
        void list_WhenEmpty_ReturnsEmptyPage() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<ShapeEntity> emptyPage = new PageImpl<>(List.of(), pageable, 0);
            
            when(shapeRepository.findByProjectId(projectId, pageable)).thenReturn(emptyPage);

            Page<Shape> result = shapeService.list(projectId, pageable);

            assertNotNull(result);
            assertEquals(0, result.getTotalElements());
        }
    }

    @Nested
    @DisplayName("search Tests")
    class SearchTests {

        @Test
        @DisplayName("Should return matching shapes for search query")
        void search_WithQuery_ReturnsMatchingShapes() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<ShapeEntity> entityPage = new PageImpl<>(List.of(sampleEntity), pageable, 1);
            
            when(shapeRepository.searchByProjectId(projectId, "Person", pageable)).thenReturn(entityPage);

            Page<Shape> result = shapeService.search(projectId, "Person", pageable);

            assertNotNull(result);
            assertEquals(1, result.getTotalElements());
        }
    }

    @Nested
    @DisplayName("update Tests")
    class UpdateTests {

        @Test
        @DisplayName("Should update shape successfully")
        void update_WithValidData_ReturnsUpdatedShape() {
            when(shapeRepository.findById(shapeId)).thenReturn(Optional.of(sampleEntity));
            when(shaclValidator.validateSyntax(any())).thenReturn(true);
            
            ShapeEntity updatedEntity = ShapeEntity.builder()
                .id(shapeId)
                .projectId(projectId)
                .uri("http://example.org/shapes/PersonShape")
                .name("Updated Person Shape")
                .description("Updated description")
                .targetClass("http://example.org/Person")
                .contentFormat("TURTLE")
                .content(VALID_SHACL_CONTENT)
                .version(2)
                .createdBy(userId)
                .build();
            
            when(shapeRepository.save(any(ShapeEntity.class))).thenReturn(updatedEntity);

            Shape updateRequest = Shape.builder()
                .name("Updated Person Shape")
                .description("Updated description")
                .uri("http://example.org/shapes/PersonShape")
                .targetClass("http://example.org/Person")
                .contentFormat(Shape.ContentFormat.TURTLE)
                .content(VALID_SHACL_CONTENT)
                .tags(List.of("person", "updated"))
                .build();

            Shape result = shapeService.update(shapeId, updateRequest);

            assertNotNull(result);
            assertEquals("Updated Person Shape", result.getName());
            assertEquals(2, result.getVersion());
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when shape doesn't exist")
        void update_WhenNotFound_ThrowsException() {
            when(shapeRepository.findById(shapeId)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () -> 
                shapeService.update(shapeId, sampleShape)
            );
        }

        @Test
        @DisplayName("Should throw ShaclValidationException for invalid SHACL content")
        void update_WithInvalidContent_ThrowsValidationException() {
            when(shapeRepository.findById(shapeId)).thenReturn(Optional.of(sampleEntity));
            when(shaclValidator.validateSyntax(any())).thenReturn(false);

            assertThrows(ShaclValidationException.class, () -> 
                shapeService.update(shapeId, sampleShape)
            );
        }

        @Test
        @DisplayName("Should increment version on update")
        void update_ShouldIncrementVersion() {
            when(shapeRepository.findById(shapeId)).thenReturn(Optional.of(sampleEntity));
            when(shaclValidator.validateSyntax(any())).thenReturn(true);
            
            ArgumentCaptor<ShapeEntity> captor = ArgumentCaptor.forClass(ShapeEntity.class);
            when(shapeRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

            shapeService.update(shapeId, sampleShape);

            assertEquals(2, captor.getValue().getVersion());
        }
    }

    @Nested
    @DisplayName("delete Tests")
    class DeleteTests {

        @Test
        @DisplayName("Should delete shape successfully")
        void delete_WhenExists_DeletesShape() {
            when(shapeRepository.existsById(shapeId)).thenReturn(true);

            assertDoesNotThrow(() -> shapeService.delete(shapeId));

            verify(shapeRepository).deleteById(shapeId);
        }

        @Test
        @DisplayName("Should throw ResourceNotFoundException when shape doesn't exist")
        void delete_WhenNotFound_ThrowsException() {
            when(shapeRepository.existsById(shapeId)).thenReturn(false);

            assertThrows(ResourceNotFoundException.class, () -> 
                shapeService.delete(shapeId)
            );
        }
    }

    @Nested
    @DisplayName("getTemplates Tests")
    class GetTemplatesTests {

        @Test
        @DisplayName("Should return list of template shapes")
        void getTemplates_ReturnsTemplates() {
            ShapeEntity templateEntity = ShapeEntity.builder()
                .id(UUID.randomUUID())
                .name("Template Shape")
                .isTemplate(true)
                .contentFormat("TURTLE")
                .content(VALID_SHACL_CONTENT)
                .build();
            
            when(shapeRepository.findByIsTemplateTrue()).thenReturn(List.of(templateEntity));

            List<Shape> result = shapeService.getTemplates();

            assertNotNull(result);
            assertEquals(1, result.size());
            assertTrue(result.get(0).isTemplate());
        }

        @Test
        @DisplayName("Should return empty list when no templates exist")
        void getTemplates_WhenNoTemplates_ReturnsEmptyList() {
            when(shapeRepository.findByIsTemplateTrue()).thenReturn(List.of());

            List<Shape> result = shapeService.getTemplates();

            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("getCategories Tests")
    class GetCategoriesTests {

        @Test
        @DisplayName("Should return list of categories for project")
        void getCategories_ReturnsCategoryList() {
            List<String> categories = List.of("Person", "Organization", "Location");
            when(shapeRepository.findCategoriesByProjectId(projectId)).thenReturn(categories);

            List<String> result = shapeService.getCategories(projectId);

            assertNotNull(result);
            assertEquals(3, result.size());
            assertTrue(result.contains("Person"));
        }
    }

    @Nested
    @DisplayName("Entity/Model Conversion Tests")
    class ConversionTests {

        @Test
        @DisplayName("Should correctly convert tags from entity to model")
        void toModel_WithTags_ConvertsCorrectly() {
            when(shapeRepository.findById(shapeId)).thenReturn(Optional.of(sampleEntity));

            Shape result = shapeService.getById(shapeId);

            assertNotNull(result.getTags());
            assertEquals(2, result.getTags().size());
            assertTrue(result.getTags().contains("person"));
        }

        @Test
        @DisplayName("Should handle null tags in entity")
        void toModel_WithNullTags_ReturnsEmptyList() {
            ShapeEntity entityWithNullTags = ShapeEntity.builder()
                .id(shapeId)
                .projectId(projectId)
                .name("Shape Without Tags")
                .contentFormat("TURTLE")
                .content(VALID_SHACL_CONTENT)
                .tags(null)
                .build();
            
            when(shapeRepository.findById(shapeId)).thenReturn(Optional.of(entityWithNullTags));

            Shape result = shapeService.getById(shapeId);

            assertNotNull(result.getTags());
            assertTrue(result.getTags().isEmpty());
        }
    }
}
