package io.rdfforge.dimension.service;

import io.rdfforge.common.exception.ResourceNotFoundException;
import io.rdfforge.dimension.entity.DimensionValueEntity;
import io.rdfforge.dimension.entity.HierarchyEntity;
import io.rdfforge.dimension.entity.HierarchyEntity.HierarchyScheme;
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

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive tests for HierarchyService.
 * Tests hierarchy management, circular reference detection, and tree operations.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("HierarchyService Tests")
class HierarchyServiceTest {

    @Mock
    private HierarchyRepository hierarchyRepository;

    @Mock
    private DimensionValueRepository valueRepository;

    private HierarchyService hierarchyService;

    private UUID hierarchyId;
    private UUID dimensionId;
    private UUID valueId;
    private UUID parentId;
    private UUID childId;
    private HierarchyEntity sampleHierarchy;
    private DimensionValueEntity sampleValue;
    private DimensionValueEntity parentValue;
    private DimensionValueEntity childValue;

    @BeforeEach
    void setUp() {
        hierarchyService = new HierarchyService(hierarchyRepository, valueRepository);

        hierarchyId = UUID.randomUUID();
        dimensionId = UUID.randomUUID();
        valueId = UUID.randomUUID();
        parentId = UUID.randomUUID();
        childId = UUID.randomUUID();

        sampleHierarchy = new HierarchyEntity();
        sampleHierarchy.setId(hierarchyId);
        sampleHierarchy.setDimensionId(dimensionId);
        sampleHierarchy.setUri("http://example.org/hierarchies/time");
        sampleHierarchy.setName("Time Hierarchy");
        sampleHierarchy.setDescription("Temporal hierarchy for years and quarters");
        sampleHierarchy.setHierarchyType(HierarchyScheme.SKOS_CONCEPT_SCHEME);
        sampleHierarchy.setMaxDepth(3);
        sampleHierarchy.setIsDefault(true);
        sampleHierarchy.setCreatedAt(Instant.now());

        sampleValue = new DimensionValueEntity();
        sampleValue.setId(valueId);
        sampleValue.setDimensionId(dimensionId);
        sampleValue.setCode("2024");
        sampleValue.setLabel("Year 2024");
        sampleValue.setUri("http://example.org/dimensions/time/2024");
        sampleValue.setHierarchyLevel(0);
        sampleValue.setSortOrder(0);
        sampleValue.setCreatedAt(Instant.now());

        parentValue = new DimensionValueEntity();
        parentValue.setId(parentId);
        parentValue.setDimensionId(dimensionId);
        parentValue.setCode("2024");
        parentValue.setLabel("Year 2024");
        parentValue.setHierarchyLevel(0);

        childValue = new DimensionValueEntity();
        childValue.setId(childId);
        childValue.setDimensionId(dimensionId);
        childValue.setCode("Q1-2024");
        childValue.setLabel("Q1 2024");
        childValue.setParentId(parentId);
        childValue.setHierarchyLevel(1);
    }

    @Nested
    @DisplayName("create Tests")
    class CreateTests {

        @Test
        @DisplayName("Should create hierarchy successfully")
        void create_WithValidData_CreatesHierarchy() {
            when(hierarchyRepository.findByDimensionIdAndName(dimensionId, "Time Hierarchy"))
                .thenReturn(Optional.empty());
            when(hierarchyRepository.existsByDimensionIdAndUri(dimensionId, sampleHierarchy.getUri()))
                .thenReturn(false);
            when(hierarchyRepository.save(any(HierarchyEntity.class))).thenAnswer(inv -> {
                HierarchyEntity entity = inv.getArgument(0);
                entity.setId(hierarchyId);
                return entity;
            });

            HierarchyEntity result = hierarchyService.create(sampleHierarchy);

            assertNotNull(result);
            assertEquals(hierarchyId, result.getId());
            assertEquals("Time Hierarchy", result.getName());
            assertNotNull(result.getCreatedAt());
        }

        @Test
        @DisplayName("Should throw exception when dimension ID is null")
        void create_WithNullDimensionId_ThrowsException() {
            sampleHierarchy.setDimensionId(null);

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                hierarchyService.create(sampleHierarchy)
            );

            assertEquals("Dimension ID is required", exception.getMessage());
        }

        @Test
        @DisplayName("Should throw exception for duplicate name in same dimension")
        void create_WithDuplicateName_ThrowsException() {
            when(hierarchyRepository.findByDimensionIdAndName(dimensionId, "Time Hierarchy"))
                .thenReturn(Optional.of(sampleHierarchy));

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                hierarchyService.create(sampleHierarchy)
            );

            assertTrue(exception.getMessage().contains("already exists"));
        }

        @Test
        @DisplayName("Should throw exception for duplicate URI")
        void create_WithDuplicateUri_ThrowsException() {
            when(hierarchyRepository.findByDimensionIdAndName(dimensionId, "Time Hierarchy"))
                .thenReturn(Optional.empty());
            when(hierarchyRepository.existsByDimensionIdAndUri(dimensionId, sampleHierarchy.getUri()))
                .thenReturn(true);

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                hierarchyService.create(sampleHierarchy)
            );

            assertTrue(exception.getMessage().contains("URI already exists"));
        }

        @Test
        @DisplayName("Should unset previous default when creating new default hierarchy")
        void create_WithDefault_UnsetsPreviousDefault() {
            HierarchyEntity existingDefault = new HierarchyEntity();
            existingDefault.setId(UUID.randomUUID());
            existingDefault.setDimensionId(dimensionId);
            existingDefault.setIsDefault(true);

            when(hierarchyRepository.findByDimensionIdAndName(any(), any()))
                .thenReturn(Optional.empty());
            when(hierarchyRepository.existsByDimensionIdAndUri(any(), any()))
                .thenReturn(false);
            when(hierarchyRepository.findByDimensionIdAndIsDefaultTrue(dimensionId))
                .thenReturn(Optional.of(existingDefault));
            when(hierarchyRepository.save(any(HierarchyEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            hierarchyService.create(sampleHierarchy);

            verify(hierarchyRepository).save(argThat(h ->
                !h.getIsDefault() && h.getId().equals(existingDefault.getId())
            ));
        }
    }

    @Nested
    @DisplayName("findById Tests")
    class FindByIdTests {

        @Test
        @DisplayName("Should return hierarchy when found")
        void findById_WhenFound_ReturnsHierarchy() {
            when(hierarchyRepository.findById(hierarchyId)).thenReturn(Optional.of(sampleHierarchy));

            Optional<HierarchyEntity> result = hierarchyService.findById(hierarchyId);

            assertTrue(result.isPresent());
            assertEquals(hierarchyId, result.get().getId());
        }

        @Test
        @DisplayName("Should return empty when not found")
        void findById_WhenNotFound_ReturnsEmpty() {
            when(hierarchyRepository.findById(hierarchyId)).thenReturn(Optional.empty());

            Optional<HierarchyEntity> result = hierarchyService.findById(hierarchyId);

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("findByDimension Tests")
    class FindByDimensionTests {

        @Test
        @DisplayName("Should return hierarchies ordered by default first")
        void findByDimension_ReturnsOrderedHierarchies() {
            List<HierarchyEntity> expected = List.of(sampleHierarchy);
            when(hierarchyRepository.findByDimensionIdOrderedByDefault(dimensionId))
                .thenReturn(expected);

            List<HierarchyEntity> result = hierarchyService.findByDimension(dimensionId);

            assertEquals(1, result.size());
            verify(hierarchyRepository).findByDimensionIdOrderedByDefault(dimensionId);
        }
    }

    @Nested
    @DisplayName("findDefault Tests")
    class FindDefaultTests {

        @Test
        @DisplayName("Should return default hierarchy")
        void findDefault_WhenExists_ReturnsDefault() {
            when(hierarchyRepository.findByDimensionIdAndIsDefaultTrue(dimensionId))
                .thenReturn(Optional.of(sampleHierarchy));

            Optional<HierarchyEntity> result = hierarchyService.findDefault(dimensionId);

            assertTrue(result.isPresent());
            assertTrue(result.get().getIsDefault());
        }
    }

    @Nested
    @DisplayName("update Tests")
    class UpdateTests {

        @Test
        @DisplayName("Should update hierarchy successfully")
        void update_WithValidData_UpdatesHierarchy() {
            when(hierarchyRepository.findById(hierarchyId)).thenReturn(Optional.of(sampleHierarchy));
            when(hierarchyRepository.save(any(HierarchyEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            HierarchyEntity updates = new HierarchyEntity();
            updates.setName("Updated Hierarchy");
            updates.setDescription("Updated description");
            updates.setMaxDepth(5);

            HierarchyEntity result = hierarchyService.update(hierarchyId, updates);

            assertEquals("Updated Hierarchy", result.getName());
            assertEquals("Updated description", result.getDescription());
            assertEquals(5, result.getMaxDepth());
            assertNotNull(result.getUpdatedAt());
        }

        @Test
        @DisplayName("Should unset previous default when setting new default")
        void update_SetDefault_UnsetsPreviousDefault() {
            HierarchyEntity existingDefault = new HierarchyEntity();
            existingDefault.setId(UUID.randomUUID());
            existingDefault.setDimensionId(dimensionId);
            existingDefault.setIsDefault(true);

            sampleHierarchy.setIsDefault(false);
            when(hierarchyRepository.findById(hierarchyId)).thenReturn(Optional.of(sampleHierarchy));
            when(hierarchyRepository.findByDimensionIdAndIsDefaultTrue(dimensionId))
                .thenReturn(Optional.of(existingDefault));
            when(hierarchyRepository.save(any(HierarchyEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            HierarchyEntity updates = new HierarchyEntity();
            updates.setIsDefault(true);

            hierarchyService.update(hierarchyId, updates);

            verify(hierarchyRepository).save(argThat(h ->
                h.getId().equals(existingDefault.getId()) && !h.getIsDefault()
            ));
        }

        @Test
        @DisplayName("Should throw exception when hierarchy not found")
        void update_WhenNotFound_ThrowsException() {
            when(hierarchyRepository.findById(hierarchyId)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () ->
                hierarchyService.update(hierarchyId, new HierarchyEntity())
            );
        }
    }

    @Nested
    @DisplayName("delete Tests")
    class DeleteTests {

        @Test
        @DisplayName("Should delete hierarchy")
        void delete_DeletesHierarchy() {
            hierarchyService.delete(hierarchyId);

            verify(hierarchyRepository).deleteById(hierarchyId);
        }
    }

    @Nested
    @DisplayName("setParent Tests")
    class SetParentTests {

        @Test
        @DisplayName("Should set parent successfully")
        void setParent_WithValidParent_SetsParent() {
            when(valueRepository.findById(valueId)).thenReturn(Optional.of(sampleValue));
            when(valueRepository.findById(parentId)).thenReturn(Optional.of(parentValue));
            when(valueRepository.save(any(DimensionValueEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            hierarchyService.setParent(valueId, parentId);

            verify(valueRepository).save(argThat(v ->
                v.getId().equals(valueId) &&
                v.getParentId().equals(parentId) &&
                v.getHierarchyLevel() == 1
            ));
        }

        @Test
        @DisplayName("Should remove parent when parentId is null")
        void setParent_WithNullParent_RemovesParent() {
            sampleValue.setParentId(parentId);
            sampleValue.setHierarchyLevel(1);

            when(valueRepository.findById(valueId)).thenReturn(Optional.of(sampleValue));
            when(valueRepository.save(any(DimensionValueEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            hierarchyService.setParent(valueId, null);

            verify(valueRepository).save(argThat(v ->
                v.getId().equals(valueId) &&
                v.getParentId() == null &&
                v.getHierarchyLevel() == 0
            ));
        }

        @Test
        @DisplayName("Should throw exception when value not found")
        void setParent_ValueNotFound_ThrowsException() {
            when(valueRepository.findById(valueId)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () ->
                hierarchyService.setParent(valueId, parentId)
            );
        }

        @Test
        @DisplayName("Should throw exception when parent not found")
        void setParent_ParentNotFound_ThrowsException() {
            when(valueRepository.findById(valueId)).thenReturn(Optional.of(sampleValue));
            when(valueRepository.findById(parentId)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () ->
                hierarchyService.setParent(valueId, parentId)
            );
        }

        @Test
        @DisplayName("Should throw exception when parent is in different dimension")
        void setParent_DifferentDimension_ThrowsException() {
            parentValue.setDimensionId(UUID.randomUUID()); // Different dimension

            when(valueRepository.findById(valueId)).thenReturn(Optional.of(sampleValue));
            when(valueRepository.findById(parentId)).thenReturn(Optional.of(parentValue));

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                hierarchyService.setParent(valueId, parentId)
            );

            assertTrue(exception.getMessage().contains("same dimension"));
        }

        @Test
        @DisplayName("Should throw exception when setting parent would create cycle")
        void setParent_WouldCreateCycle_ThrowsException() {
            // Create a scenario where A -> B -> C, and we try to set C as parent of A
            DimensionValueEntity valueA = new DimensionValueEntity();
            valueA.setId(valueId);
            valueA.setDimensionId(dimensionId);
            valueA.setParentId(parentId);
            valueA.setHierarchyLevel(1);

            DimensionValueEntity valueB = new DimensionValueEntity();
            valueB.setId(parentId);
            valueB.setDimensionId(dimensionId);
            valueB.setParentId(childId); // B's parent is C
            valueB.setHierarchyLevel(2);

            DimensionValueEntity valueC = new DimensionValueEntity();
            valueC.setId(childId);
            valueC.setDimensionId(dimensionId);
            valueC.setParentId(null);
            valueC.setHierarchyLevel(0);

            when(valueRepository.findById(valueId)).thenReturn(Optional.of(valueA));
            when(valueRepository.findById(childId)).thenReturn(Optional.of(valueC));
            when(valueRepository.findById(parentId)).thenReturn(Optional.of(valueB));

            // A -> B -> C. Setting A as parent of C would create
            // A -> B -> C -> A (a real cycle).
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                hierarchyService.setParent(childId, valueId)
            );

            assertTrue(exception.getMessage().contains("cycle"));
        }

        @Test
        @DisplayName("Should detect self-referencing cycle")
        void setParent_SelfReference_ThrowsException() {
            when(valueRepository.findById(valueId)).thenReturn(Optional.of(sampleValue));

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                hierarchyService.setParent(valueId, valueId)
            );

            assertTrue(exception.getMessage().contains("cycle"));
        }

        @Test
        @DisplayName("Should update child levels when setting parent")
        void setParent_UpdatesChildLevels() {
            DimensionValueEntity grandchild = new DimensionValueEntity();
            grandchild.setId(UUID.randomUUID());
            grandchild.setDimensionId(dimensionId);
            grandchild.setParentId(valueId);
            grandchild.setHierarchyLevel(2);

            sampleValue.setParentId(null);
            sampleValue.setHierarchyLevel(0);

            when(valueRepository.findById(valueId)).thenReturn(Optional.of(sampleValue));
            when(valueRepository.findById(parentId)).thenReturn(Optional.of(parentValue));
            when(valueRepository.findByParentId(valueId)).thenReturn(List.of(grandchild));
            when(valueRepository.save(any(DimensionValueEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            hierarchyService.setParent(valueId, parentId);

            // Verify grandchild's level was updated
            verify(valueRepository).save(argThat(v ->
                v.getId().equals(grandchild.getId()) &&
                v.getHierarchyLevel() == 2
            ));
        }
    }

    @Nested
    @DisplayName("Circular Reference Detection Tests")
    class CircularReferenceTests {

        @Test
        @DisplayName("Should detect direct cycle")
        void wouldCreateCycle_DirectCycle_ReturnsTrue() {
            // A -> B and trying to set A as parent of B (B -> A)
            when(valueRepository.findById(valueId)).thenReturn(Optional.of(sampleValue));

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                hierarchyService.setParent(valueId, valueId)
            );

            assertTrue(exception.getMessage().contains("cycle"));
        }

        @Test
        @DisplayName("Should detect indirect cycle")
        void wouldCreateCycle_IndirectCycle_ReturnsTrue() {
            // A -> B -> C and trying to set A as parent of C (C -> A)
            UUID valueA = UUID.randomUUID();
            UUID valueB = UUID.randomUUID();
            UUID valueC = UUID.randomUUID();

            DimensionValueEntity entityA = new DimensionValueEntity();
            entityA.setId(valueA);
            entityA.setDimensionId(dimensionId);
            entityA.setParentId(valueB);

            DimensionValueEntity entityB = new DimensionValueEntity();
            entityB.setId(valueB);
            entityB.setDimensionId(dimensionId);
            entityB.setParentId(valueC);

            DimensionValueEntity entityC = new DimensionValueEntity();
            entityC.setId(valueC);
            entityC.setDimensionId(dimensionId);
            entityC.setParentId(null);

            when(valueRepository.findById(valueA)).thenReturn(Optional.of(entityA));
            when(valueRepository.findById(valueC)).thenReturn(Optional.of(entityC));
            when(valueRepository.findById(valueB)).thenReturn(Optional.of(entityB));

            // A -> B -> C already; setting A as parent of C closes the loop.
            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () ->
                hierarchyService.setParent(valueC, valueA)
            );

            assertTrue(exception.getMessage().contains("cycle"));
        }

        @Test
        @DisplayName("Should allow valid parent assignment")
        void wouldCreateCycle_ValidAssignment_ReturnsFalse() {
            // Two separate trees: A and B, setting B as parent of A is valid
            sampleValue.setParentId(null);
            parentValue.setParentId(null);

            when(valueRepository.findById(valueId)).thenReturn(Optional.of(sampleValue));
            when(valueRepository.findById(parentId)).thenReturn(Optional.of(parentValue));
            when(valueRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            assertDoesNotThrow(() -> hierarchyService.setParent(valueId, parentId));
        }
    }

    @Nested
    @DisplayName("getRoots Tests")
    class GetRootsTests {

        @Test
        @DisplayName("Should return root values")
        void getRoots_ReturnsRootValues() {
            when(valueRepository.findByDimensionIdAndParentIdIsNull(dimensionId))
                .thenReturn(List.of(sampleValue));

            List<DimensionValueEntity> result = hierarchyService.getRoots(dimensionId);

            assertEquals(1, result.size());
            assertNull(result.get(0).getParentId());
        }
    }

    @Nested
    @DisplayName("getChildren Tests")
    class GetChildrenTests {

        @Test
        @DisplayName("Should return children of parent")
        void getChildren_ReturnsChildren() {
            when(valueRepository.findByParentId(parentId)).thenReturn(List.of(childValue));

            List<DimensionValueEntity> result = hierarchyService.getChildren(parentId);

            assertEquals(1, result.size());
            assertEquals(parentId, result.get(0).getParentId());
        }
    }

    @Nested
    @DisplayName("getAncestors Tests")
    class GetAncestorsTests {

        @Test
        @DisplayName("Should return all ancestors")
        void getAncestors_ReturnsAncestors() {
            UUID grandparentId = UUID.randomUUID();
            DimensionValueEntity grandparent = new DimensionValueEntity();
            grandparent.setId(grandparentId);
            grandparent.setParentId(null);

            parentValue.setParentId(grandparentId);
            childValue.setParentId(parentId);

            when(valueRepository.findById(childId)).thenReturn(Optional.of(childValue));
            when(valueRepository.findById(parentId)).thenReturn(Optional.of(parentValue));
            when(valueRepository.findById(grandparentId)).thenReturn(Optional.of(grandparent));

            List<DimensionValueEntity> result = hierarchyService.getAncestors(childId);

            assertEquals(2, result.size());
            assertEquals(grandparentId, result.get(0).getId());
            assertEquals(parentId, result.get(1).getId());
        }

        @Test
        @DisplayName("Should return empty list for root value")
        void getAncestors_RootValue_ReturnsEmpty() {
            sampleValue.setParentId(null);
            when(valueRepository.findById(valueId)).thenReturn(Optional.of(sampleValue));

            List<DimensionValueEntity> result = hierarchyService.getAncestors(valueId);

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("getDescendants Tests")
    class GetDescendantsTests {

        @Test
        @DisplayName("Should return all descendants")
        void getDescendants_ReturnsDescendants() {
            UUID grandchildId = UUID.randomUUID();
            DimensionValueEntity grandchild = new DimensionValueEntity();
            grandchild.setId(grandchildId);
            grandchild.setParentId(childId);

            when(valueRepository.findByParentId(parentId)).thenReturn(List.of(childValue));
            when(valueRepository.findByParentId(childId)).thenReturn(List.of(grandchild));
            when(valueRepository.findByParentId(grandchildId)).thenReturn(List.of());

            List<DimensionValueEntity> result = hierarchyService.getDescendants(parentId);

            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("Should return empty list for leaf value")
        void getDescendants_LeafValue_ReturnsEmpty() {
            when(valueRepository.findByParentId(valueId)).thenReturn(List.of());

            List<DimensionValueEntity> result = hierarchyService.getDescendants(valueId);

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("exportHierarchyToSkos Tests")
    class ExportToSkosTests {

        @Test
        @DisplayName("Should export hierarchy to SKOS format")
        void exportHierarchyToSkos_ReturnsSkos() {
            when(hierarchyRepository.findById(hierarchyId)).thenReturn(Optional.of(sampleHierarchy));
            when(valueRepository.findActiveValuesByDimensionId(dimensionId))
                .thenReturn(List.of(sampleValue));

            String result = hierarchyService.exportHierarchyToSkos(dimensionId, hierarchyId);

            assertNotNull(result);
            assertTrue(result.contains("@prefix skos:"));
            assertTrue(result.contains("skos:ConceptScheme"));
            assertTrue(result.contains("skos:Concept"));
            assertTrue(result.contains("Time Hierarchy"));
        }

        @Test
        @DisplayName("Should throw exception when hierarchy not found")
        void exportHierarchyToSkos_HierarchyNotFound_ThrowsException() {
            when(hierarchyRepository.findById(hierarchyId)).thenReturn(Optional.empty());

            assertThrows(ResourceNotFoundException.class, () ->
                hierarchyService.exportHierarchyToSkos(dimensionId, hierarchyId)
            );
        }
    }

    @Nested
    @DisplayName("reorderChildren Tests")
    class ReorderChildrenTests {

        @Test
        @DisplayName("Should reorder children by specified order")
        void reorderChildren_UpdatesSortOrder() {
            UUID child1Id = UUID.randomUUID();
            UUID child2Id = UUID.randomUUID();
            UUID child3Id = UUID.randomUUID();

            DimensionValueEntity child1 = new DimensionValueEntity();
            child1.setId(child1Id);
            child1.setSortOrder(0);

            DimensionValueEntity child2 = new DimensionValueEntity();
            child2.setId(child2Id);
            child2.setSortOrder(1);

            DimensionValueEntity child3 = new DimensionValueEntity();
            child3.setId(child3Id);
            child3.setSortOrder(2);

            when(valueRepository.findByParentId(parentId))
                .thenReturn(List.of(child1, child2, child3));
            when(valueRepository.save(any(DimensionValueEntity.class))).thenAnswer(inv -> inv.getArgument(0));

            // Reorder: child2, child3, child1
            hierarchyService.reorderChildren(parentId, List.of(child2Id, child3Id, child1Id));

            ArgumentCaptor<DimensionValueEntity> captor = ArgumentCaptor.forClass(DimensionValueEntity.class);
            verify(valueRepository, times(3)).save(captor.capture());

            List<DimensionValueEntity> saved = captor.getAllValues();
            assertEquals(0, saved.stream().filter(c -> c.getId().equals(child2Id)).findFirst().get().getSortOrder());
            assertEquals(1, saved.stream().filter(c -> c.getId().equals(child3Id)).findFirst().get().getSortOrder());
            assertEquals(2, saved.stream().filter(c -> c.getId().equals(child1Id)).findFirst().get().getSortOrder());
        }

        @Test
        @DisplayName("Should ignore unknown child IDs")
        void reorderChildren_UnknownChildId_Ignores() {
            when(valueRepository.findByParentId(parentId)).thenReturn(List.of(childValue));
            when(valueRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // Include unknown ID
            hierarchyService.reorderChildren(parentId, List.of(UUID.randomUUID(), childId));

            verify(valueRepository, times(1)).save(any());
        }
    }
}
