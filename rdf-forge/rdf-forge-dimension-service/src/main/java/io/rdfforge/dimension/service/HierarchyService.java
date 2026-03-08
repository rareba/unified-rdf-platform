package io.rdfforge.dimension.service;

import io.rdfforge.dimension.entity.DimensionValueEntity;
import io.rdfforge.dimension.entity.HierarchyEntity;
import io.rdfforge.dimension.entity.HierarchyEntity.HierarchyScheme;
import io.rdfforge.dimension.repository.DimensionValueRepository;
import io.rdfforge.dimension.repository.HierarchyRepository;
import io.rdfforge.common.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
@Transactional
public class HierarchyService {
    
    private static final Logger log = LoggerFactory.getLogger(HierarchyService.class);
    
    private final HierarchyRepository hierarchyRepository;
    private final DimensionValueRepository valueRepository;
    
    public HierarchyService(HierarchyRepository hierarchyRepository, DimensionValueRepository valueRepository) {
        this.hierarchyRepository = hierarchyRepository;
        this.valueRepository = valueRepository;
    }
    
    public HierarchyEntity create(HierarchyEntity hierarchy) {
        log.info("Creating hierarchy: {} for dimension {}", hierarchy.getName(), hierarchy.getDimensionId());

        // Validate dimension ID
        if (hierarchy.getDimensionId() == null) {
            throw new IllegalArgumentException("Dimension ID is required");
        }

        // Check for duplicate name in same dimension
        if (hierarchy.getName() != null) {
            hierarchyRepository.findByDimensionIdAndName(
                    hierarchy.getDimensionId(), hierarchy.getName())
                .ifPresent(existing -> {
                    throw new IllegalArgumentException(
                        "Hierarchy with name '" + hierarchy.getName() + "' already exists in this dimension");
                });
        }

        // Check for duplicate URI
        if (hierarchy.getUri() != null &&
            hierarchyRepository.existsByDimensionIdAndUri(hierarchy.getDimensionId(), hierarchy.getUri())) {
            throw new IllegalArgumentException("Hierarchy with URI already exists: " + hierarchy.getUri());
        }

        // Handle default hierarchy logic
        if (Boolean.TRUE.equals(hierarchy.getIsDefault())) {
            hierarchyRepository.findByDimensionIdAndIsDefaultTrue(hierarchy.getDimensionId())
                .ifPresent(existing -> {
                    existing.setIsDefault(false);
                    hierarchyRepository.save(existing);
                });
        }

        hierarchy.setCreatedAt(Instant.now());
        HierarchyEntity saved = hierarchyRepository.save(hierarchy);
        log.info("Created hierarchy: {} ({}) for dimension {}",
            saved.getName(), saved.getId(), saved.getDimensionId());
        return saved;
    }
    
    @Transactional(readOnly = true)
    public Optional<HierarchyEntity> findById(UUID id) {
        return hierarchyRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public List<HierarchyEntity> findByDimension(UUID dimensionId) {
        return hierarchyRepository.findByDimensionIdOrderedByDefault(dimensionId);
    }

    @Transactional(readOnly = true)
    public Optional<HierarchyEntity> findDefault(UUID dimensionId) {
        return hierarchyRepository.findByDimensionIdAndIsDefaultTrue(dimensionId);
    }
    
    public HierarchyEntity update(UUID id, HierarchyEntity updates) {
        log.info("Updating hierarchy: {}", id);
        
        HierarchyEntity existing = hierarchyRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Hierarchy", id.toString()));
        
        if (updates.getName() != null) existing.setName(updates.getName());
        if (updates.getDescription() != null) existing.setDescription(updates.getDescription());
        if (updates.getHierarchyType() != null) existing.setHierarchyType(updates.getHierarchyType());
        if (updates.getMaxDepth() != null) existing.setMaxDepth(updates.getMaxDepth());
        if (updates.getRootConceptUri() != null) existing.setRootConceptUri(updates.getRootConceptUri());
        if (updates.getSkosContent() != null) existing.setSkosContent(updates.getSkosContent());
        if (updates.getProperties() != null) existing.setProperties(updates.getProperties());
        
        if (updates.getIsDefault() != null && updates.getIsDefault() && !existing.getIsDefault()) {
            hierarchyRepository.findByDimensionIdAndIsDefaultTrue(existing.getDimensionId())
                .ifPresent(prev -> {
                    prev.setIsDefault(false);
                    hierarchyRepository.save(prev);
                });
            existing.setIsDefault(true);
        }
        
        existing.setUpdatedAt(Instant.now());
        return hierarchyRepository.save(existing);
    }
    
    public void delete(UUID id) {
        log.info("Deleting hierarchy: {}", id);
        hierarchyRepository.deleteById(id);
    }
    
    public void setParent(UUID valueId, UUID parentId) {
        DimensionValueEntity value = valueRepository.findById(valueId)
            .orElseThrow(() -> new ResourceNotFoundException("DimensionValue", valueId.toString()));
        
        if (parentId != null) {
            DimensionValueEntity parent = valueRepository.findById(parentId)
                .orElseThrow(() -> new ResourceNotFoundException("DimensionValue", parentId.toString()));
            
            if (!value.getDimensionId().equals(parent.getDimensionId())) {
                throw new IllegalArgumentException("Parent must be in the same dimension");
            }
            
            if (wouldCreateCycle(valueId, parentId)) {
                throw new IllegalArgumentException("Setting this parent would create a cycle");
            }
            
            value.setParentId(parentId);
            value.setHierarchyLevel(parent.getHierarchyLevel() + 1);
        } else {
            value.setParentId(null);
            value.setHierarchyLevel(0);
        }
        
        value.setUpdatedAt(Instant.now());
        valueRepository.save(value);
        
        updateChildLevels(value);
    }
    
    private boolean wouldCreateCycle(UUID valueId, UUID newParentId) {
        Set<UUID> visited = new HashSet<>();
        UUID current = newParentId;
        
        while (current != null) {
            if (current.equals(valueId)) {
                return true;
            }
            if (!visited.add(current)) {
                return true;
            }
            final UUID c = current;
            current = valueRepository.findById(c).map(DimensionValueEntity::getParentId).orElse(null);
        }
        return false;
    }
    
    private void updateChildLevels(DimensionValueEntity parent) {
        List<DimensionValueEntity> children = valueRepository.findByParentId(parent.getId());
        for (DimensionValueEntity child : children) {
            child.setHierarchyLevel(parent.getHierarchyLevel() + 1);
            child.setUpdatedAt(Instant.now());
            valueRepository.save(child);
            updateChildLevels(child);
        }
    }
    
    @Transactional(readOnly = true)
    public List<DimensionValueEntity> getRoots(UUID dimensionId) {
        return valueRepository.findByDimensionIdAndParentIdIsNull(dimensionId);
    }
    
    @Transactional(readOnly = true)
    public List<DimensionValueEntity> getChildren(UUID parentId) {
        return valueRepository.findByParentId(parentId);
    }
    
    @Transactional(readOnly = true)
    public List<DimensionValueEntity> getAncestors(UUID valueId) {
        List<DimensionValueEntity> ancestors = new ArrayList<>();
        DimensionValueEntity current = valueRepository.findById(valueId).orElse(null);
        
        while (current != null && current.getParentId() != null) {
            DimensionValueEntity parent = valueRepository.findById(current.getParentId()).orElse(null);
            if (parent != null) {
                ancestors.add(0, parent);
            }
            current = parent;
        }
        return ancestors;
    }
    
    @Transactional(readOnly = true)
    public List<DimensionValueEntity> getDescendants(UUID valueId) {
        List<DimensionValueEntity> descendants = new ArrayList<>();
        collectDescendants(valueId, descendants);
        return descendants;
    }
    
    private void collectDescendants(UUID parentId, List<DimensionValueEntity> result) {
        List<DimensionValueEntity> children = valueRepository.findByParentId(parentId);
        for (DimensionValueEntity child : children) {
            result.add(child);
            collectDescendants(child.getId(), result);
        }
    }
    
    @Transactional(readOnly = true)
    public String exportHierarchyToSkos(UUID dimensionId, UUID hierarchyId) {
        log.info("Exporting hierarchy {} to SKOS", hierarchyId);
        
        HierarchyEntity hierarchy = hierarchyRepository.findById(hierarchyId)
            .orElseThrow(() -> new ResourceNotFoundException("Hierarchy", hierarchyId.toString()));
        
        List<DimensionValueEntity> values = valueRepository.findActiveValuesByDimensionId(dimensionId);
        
        StringBuilder skos = new StringBuilder();
        skos.append("@prefix skos: <http://www.w3.org/2004/02/skos/core#> .\n");
        skos.append("@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .\n\n");
        
        skos.append("<").append(hierarchy.getUri()).append(">\n");
        skos.append("  a skos:ConceptScheme ;\n");
        skos.append("  rdfs:label \"").append(escape(hierarchy.getName())).append("\"@en .\n\n");
        
        Map<UUID, DimensionValueEntity> valueMap = new HashMap<>();
        for (DimensionValueEntity v : values) {
            valueMap.put(v.getId(), v);
        }
        
        for (DimensionValueEntity value : values) {
            skos.append("<").append(value.getUri()).append(">\n");
            skos.append("  a skos:Concept ;\n");
            skos.append("  skos:inScheme <").append(hierarchy.getUri()).append("> ;\n");
            skos.append("  skos:prefLabel \"").append(escape(value.getLabel())).append("\"@").append(value.getLabelLang() != null ? value.getLabelLang() : "en");
            
            if (value.getSkosNotation() != null) {
                skos.append(" ;\n  skos:notation \"").append(value.getSkosNotation()).append("\"");
            }
            
            if (value.getParentId() != null && valueMap.containsKey(value.getParentId())) {
                skos.append(" ;\n  skos:broader <").append(valueMap.get(value.getParentId()).getUri()).append(">");
            } else {
                skos.append(" ;\n  skos:topConceptOf <").append(hierarchy.getUri()).append(">");
            }
            
            skos.append(" .\n\n");
        }
        
        return skos.toString();
    }
    
    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
    
    public void reorderChildren(UUID parentId, List<UUID> orderedValueIds) {
        List<DimensionValueEntity> children = valueRepository.findByParentId(parentId);
        Map<UUID, DimensionValueEntity> childMap = new HashMap<>();
        for (DimensionValueEntity c : children) {
            childMap.put(c.getId(), c);
        }
        
        int order = 0;
        for (UUID id : orderedValueIds) {
            DimensionValueEntity child = childMap.get(id);
            if (child != null) {
                child.setSortOrder(order++);
                child.setUpdatedAt(Instant.now());
                valueRepository.save(child);
            }
        }
    }
}
