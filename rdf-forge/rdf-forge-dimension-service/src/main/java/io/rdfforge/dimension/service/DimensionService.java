package io.rdfforge.dimension.service;

import io.rdfforge.dimension.entity.DimensionEntity;
import io.rdfforge.dimension.entity.DimensionEntity.DimensionType;
import io.rdfforge.dimension.entity.DimensionValueEntity;
import io.rdfforge.dimension.repository.DimensionRepository;
import io.rdfforge.dimension.repository.DimensionValueRepository;
import io.rdfforge.dimension.repository.HierarchyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.BufferedReader;
import java.io.StringReader;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class DimensionService {
    
    private static final Logger log = LoggerFactory.getLogger(DimensionService.class);
    
    private final DimensionRepository dimensionRepository;
    private final DimensionValueRepository valueRepository;
    private final HierarchyRepository hierarchyRepository;
    
    public DimensionService(
            DimensionRepository dimensionRepository,
            DimensionValueRepository valueRepository,
            HierarchyRepository hierarchyRepository) {
        this.dimensionRepository = dimensionRepository;
        this.valueRepository = valueRepository;
        this.hierarchyRepository = hierarchyRepository;
    }
    
    public DimensionEntity create(DimensionEntity dimension) {
        log.info("Creating dimension: {} ({})", dimension.getName(), dimension.getUri());

        if (dimensionRepository.existsByProjectIdAndUri(dimension.getProjectId(), dimension.getUri())) {
            throw new IllegalArgumentException("Dimension with URI already exists: " + dimension.getUri());
        }

        dimension.setCreatedAt(Instant.now());
        dimension.setVersion(1);
        dimension.setValueCount(0L);

        // Dimensions without a project are shared globally
        if (dimension.getProjectId() == null && (dimension.getIsShared() == null || !dimension.getIsShared())) {
            dimension.setIsShared(true);
        }

        return dimensionRepository.save(dimension);
    }
    
    @Cacheable(value = "dimensions", key = "#id")
    public Optional<DimensionEntity> findById(UUID id) {
        return dimensionRepository.findById(id);
    }
    
    public Optional<DimensionEntity> findByProjectAndUri(UUID projectId, String uri) {
        return dimensionRepository.findByProjectIdAndUri(projectId, uri);
    }
    
    public Page<DimensionEntity> findByProject(UUID projectId, Pageable pageable) {
        return dimensionRepository.findByProjectId(projectId, pageable);
    }
    
    public Page<DimensionEntity> search(UUID projectId, DimensionType type, String search, Pageable pageable) {
        String typeStr = type != null ? type.name() : null;
        return dimensionRepository.findByFilters(projectId, typeStr, search, pageable);
    }

    public Page<DimensionEntity> findShared(DimensionType type, String search, Pageable pageable) {
        String typeStr = type != null ? type.name() : null;
        return dimensionRepository.findSharedByFilters(typeStr, search, pageable);
    }
    
    @CacheEvict(value = "dimensions", key = "#id")
    public DimensionEntity update(UUID id, DimensionEntity updates) {
        log.info("Updating dimension: {}", id);
        
        DimensionEntity existing = dimensionRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Dimension not found: " + id));
        
        if (updates.getName() != null) existing.setName(updates.getName());
        if (updates.getDescription() != null) existing.setDescription(updates.getDescription());
        if (updates.getType() != null) existing.setType(updates.getType());
        if (updates.getHierarchyType() != null) existing.setHierarchyType(updates.getHierarchyType());
        if (updates.getContent() != null) existing.setContent(updates.getContent());
        if (updates.getBaseUri() != null) existing.setBaseUri(updates.getBaseUri());
        if (updates.getMetadata() != null) existing.setMetadata(updates.getMetadata());
        if (updates.getIsShared() != null) existing.setIsShared(updates.getIsShared());
        
        existing.setVersion(existing.getVersion() + 1);
        existing.setUpdatedAt(Instant.now());
        
        return dimensionRepository.save(existing);
    }
    
    @CacheEvict(value = "dimensions", key = "#id")
    public void delete(UUID id) {
        log.info("Deleting dimension: {}", id);
        
        hierarchyRepository.deleteByDimensionId(id);
        valueRepository.deleteByDimensionId(id);
        dimensionRepository.deleteById(id);
    }
    
    public List<DimensionValueEntity> getValues(UUID dimensionId) {
        return valueRepository.findByDimensionId(dimensionId);
    }
    
    public Page<DimensionValueEntity> getValues(UUID dimensionId, String search, Pageable pageable) {
        return valueRepository.searchValues(dimensionId, search, pageable);
    }
    
    public DimensionValueEntity addValue(UUID dimensionId, DimensionValueEntity value) {
        log.debug("Adding value to dimension {}: {}", dimensionId, value.getCode());
        
        if (valueRepository.existsByDimensionIdAndCode(dimensionId, value.getCode())) {
            throw new IllegalArgumentException("Value with code already exists: " + value.getCode());
        }
        
        value.setDimensionId(dimensionId);
        value.setCreatedAt(Instant.now());
        
        DimensionValueEntity saved = valueRepository.save(value);
        
        updateValueCount(dimensionId);
        
        return saved;
    }
    
    public List<DimensionValueEntity> addValues(UUID dimensionId, List<DimensionValueEntity> values) {
        log.info("Adding {} values to dimension {}", values.size(), dimensionId);
        
        values.forEach(v -> {
            v.setDimensionId(dimensionId);
            v.setCreatedAt(Instant.now());
        });
        
        List<DimensionValueEntity> saved = valueRepository.saveAll(values);
        
        updateValueCount(dimensionId);
        
        return saved;
    }
    
    public DimensionValueEntity updateValue(UUID valueId, DimensionValueEntity updates) {
        DimensionValueEntity existing = valueRepository.findById(valueId)
            .orElseThrow(() -> new NoSuchElementException("Value not found: " + valueId));
        
        if (updates.getLabel() != null) existing.setLabel(updates.getLabel());
        if (updates.getDescription() != null) existing.setDescription(updates.getDescription());
        if (updates.getParentId() != null) existing.setParentId(updates.getParentId());
        if (updates.getHierarchyLevel() != null) existing.setHierarchyLevel(updates.getHierarchyLevel());
        if (updates.getSortOrder() != null) existing.setSortOrder(updates.getSortOrder());
        if (updates.getMetadata() != null) existing.setMetadata(updates.getMetadata());
        if (updates.getAltLabels() != null) existing.setAltLabels(updates.getAltLabels());
        if (updates.getSkosNotation() != null) existing.setSkosNotation(updates.getSkosNotation());
        if (updates.getIsDeprecated() != null) existing.setIsDeprecated(updates.getIsDeprecated());
        if (updates.getReplacedBy() != null) existing.setReplacedBy(updates.getReplacedBy());
        
        existing.setUpdatedAt(Instant.now());
        
        return valueRepository.save(existing);
    }
    
    public void deleteValue(UUID valueId) {
        DimensionValueEntity value = valueRepository.findById(valueId)
            .orElseThrow(() -> new NoSuchElementException("Value not found: " + valueId));
        
        UUID dimensionId = value.getDimensionId();
        valueRepository.deleteById(valueId);
        updateValueCount(dimensionId);
    }
    
    public List<DimensionValueEntity> importFromCsv(UUID dimensionId, String csvContent) {
        log.info("Importing values from CSV to dimension: {}", dimensionId);
        
        List<DimensionValueEntity> values = new ArrayList<>();
        
        try (BufferedReader reader = new BufferedReader(new StringReader(csvContent))) {
            String headerLine = reader.readLine();
            if (headerLine == null) return values;
            
            String[] headers = headerLine.split(",");
            Map<String, Integer> headerIndex = new HashMap<>();
            for (int i = 0; i < headers.length; i++) {
                headerIndex.put(headers[i].trim().toLowerCase(), i);
            }
            
            String line;
            int order = 0;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length < 2) continue;
                
                DimensionValueEntity value = new DimensionValueEntity();
                value.setDimensionId(dimensionId);
                value.setCode(getColumn(parts, headerIndex, "code", 0));
                value.setLabel(getColumn(parts, headerIndex, "label", 1));
                value.setUri(getColumn(parts, headerIndex, "uri", -1));
                value.setDescription(getColumn(parts, headerIndex, "description", -1));
                value.setSkosNotation(getColumn(parts, headerIndex, "notation", -1));
                value.setSortOrder(order++);
                value.setCreatedAt(Instant.now());
                
                if (value.getUri() == null || value.getUri().isBlank()) {
                    DimensionEntity dim = dimensionRepository.findById(dimensionId).orElse(null);
                    String baseUri = dim != null && dim.getBaseUri() != null ? dim.getBaseUri() : "";
                    value.setUri(baseUri + "/" + value.getCode());
                }
                
                values.add(value);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse CSV: " + e.getMessage(), e);
        }
        
        List<DimensionValueEntity> saved = valueRepository.saveAll(values);
        updateValueCount(dimensionId);
        
        log.info("Imported {} values to dimension {}", saved.size(), dimensionId);
        return saved;
    }
    
    private String getColumn(String[] parts, Map<String, Integer> headerIndex, String name, int defaultIndex) {
        Integer idx = headerIndex.get(name);
        if (idx != null && idx < parts.length) {
            return parts[idx].trim();
        }
        if (defaultIndex >= 0 && defaultIndex < parts.length) {
            return parts[defaultIndex].trim();
        }
        return null;
    }
    
    public String exportToTurtle(UUID dimensionId) {
        log.info("Exporting dimension {} to Turtle", dimensionId);
        
        DimensionEntity dimension = dimensionRepository.findById(dimensionId)
            .orElseThrow(() -> new NoSuchElementException("Dimension not found: " + dimensionId));
        
        List<DimensionValueEntity> values = valueRepository.findActiveValuesByDimensionId(dimensionId);
        
        StringBuilder ttl = new StringBuilder();
        ttl.append("@prefix skos: <http://www.w3.org/2004/02/skos/core#> .\n");
        ttl.append("@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .\n");
        ttl.append("@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .\n");
        ttl.append("@prefix dim: <").append(dimension.getBaseUri() != null ? dimension.getBaseUri() : dimension.getUri()).append("> .\n\n");
        
        ttl.append("<").append(dimension.getUri()).append(">\n");
        ttl.append("  a skos:ConceptScheme ;\n");
        ttl.append("  rdfs:label \"").append(escape(dimension.getName())).append("\"@en");
        if (dimension.getDescription() != null) {
            ttl.append(" ;\n  skos:definition \"").append(escape(dimension.getDescription())).append("\"@en");
        }
        ttl.append(" .\n\n");
        
        for (DimensionValueEntity value : values) {
            ttl.append("<").append(value.getUri()).append(">\n");
            ttl.append("  a skos:Concept ;\n");
            ttl.append("  skos:inScheme <").append(dimension.getUri()).append("> ;\n");
            ttl.append("  skos:prefLabel \"").append(escape(value.getLabel())).append("\"@").append(value.getLabelLang() != null ? value.getLabelLang() : "en");
            
            if (value.getSkosNotation() != null) {
                ttl.append(" ;\n  skos:notation \"").append(escape(value.getSkosNotation())).append("\"");
            }
            if (value.getDescription() != null) {
                ttl.append(" ;\n  skos:definition \"").append(escape(value.getDescription())).append("\"@en");
            }
            if (value.getParentId() != null) {
                valueRepository.findById(value.getParentId()).ifPresent(parent -> 
                    ttl.append(" ;\n  skos:broader <").append(parent.getUri()).append(">")
                );
            }
            if (value.getAltLabels() != null && !value.getAltLabels().isEmpty()) {
                for (Map.Entry<String, String> alt : value.getAltLabels().entrySet()) {
                    ttl.append(" ;\n  skos:altLabel \"").append(escape(alt.getValue())).append("\"@").append(alt.getKey());
                }
            }
            ttl.append(" .\n\n");
        }
        
        return ttl.toString();
    }
    
    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
    
    public List<DimensionValueEntity> getHierarchyTree(UUID dimensionId) {
        List<DimensionValueEntity> rootValues = valueRepository.findByDimensionIdAndParentIdIsNull(dimensionId);
        return buildTree(rootValues);
    }
    
    private List<DimensionValueEntity> buildTree(List<DimensionValueEntity> values) {
        for (DimensionValueEntity value : values) {
            List<DimensionValueEntity> children = valueRepository.findByParentId(value.getId());
            if (!children.isEmpty()) {
                buildTree(children);
            }
        }
        return values;
    }
    
    public Optional<DimensionValueEntity> lookupValue(UUID dimensionId, String codeOrUri) {
        Optional<DimensionValueEntity> byCode = valueRepository.findByDimensionIdAndCode(dimensionId, codeOrUri);
        if (byCode.isPresent()) return byCode;
        return valueRepository.findByDimensionIdAndUri(dimensionId, codeOrUri);
    }
    
    public Map<String, Object> getStats(UUID projectId) {
        Map<String, Object> stats = new HashMap<>();
        stats.put("dimensionCount", dimensionRepository.countByProjectId(projectId));
        stats.put("totalValues", dimensionRepository.sumValueCountByProjectId(projectId));
        
        List<DimensionEntity> dimensions = dimensionRepository.findByProjectId(projectId);
        Map<String, Long> byType = dimensions.stream()
            .collect(Collectors.groupingBy(d -> d.getType().name(), Collectors.counting()));
        stats.put("byType", byType);
        
        return stats;
    }
    
    private void updateValueCount(UUID dimensionId) {
        long count = valueRepository.countByDimensionId(dimensionId);
        dimensionRepository.findById(dimensionId).ifPresent(dim -> {
            dim.setValueCount(count);
            dimensionRepository.save(dim);
        });
    }
}
