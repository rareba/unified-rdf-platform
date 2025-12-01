package io.rdfforge.shacl.service;

import io.rdfforge.common.exception.ResourceNotFoundException;
import io.rdfforge.common.exception.ShaclValidationException;
import io.rdfforge.common.model.Shape;
import io.rdfforge.engine.shacl.ShaclValidator;
import io.rdfforge.shacl.entity.ShapeEntity;
import io.rdfforge.shacl.repository.ShapeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ShapeService {
    private final ShapeRepository shapeRepository;
    private final ShaclValidator shaclValidator;

    @Transactional
    public Shape create(Shape shape) {
        if (!shaclValidator.validateSyntax(shape.getContent())) {
            throw new ShaclValidationException("Invalid SHACL syntax");
        }
        
        ShapeEntity entity = toEntity(shape);
        entity = shapeRepository.save(entity);
        
        log.info("Created shape: {} ({})", entity.getName(), entity.getId());
        return toModel(entity);
    }

    @Transactional(readOnly = true)
    public Shape getById(UUID id) {
        ShapeEntity entity = shapeRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Shape", id.toString()));
        return toModel(entity);
    }

    @Transactional(readOnly = true)
    public Page<Shape> list(UUID projectId, Pageable pageable) {
        Page<ShapeEntity> entities = shapeRepository.findByProjectId(projectId, pageable);
        return entities.map(this::toModel);
    }

    @Transactional(readOnly = true)
    public Page<Shape> search(UUID projectId, String query, Pageable pageable) {
        Page<ShapeEntity> entities = shapeRepository.searchByProjectId(projectId, query, pageable);
        return entities.map(this::toModel);
    }

    @Transactional
    public Shape update(UUID id, Shape shape) {
        ShapeEntity existing = shapeRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Shape", id.toString()));
        
        if (!shaclValidator.validateSyntax(shape.getContent())) {
            throw new ShaclValidationException("Invalid SHACL syntax");
        }
        
        existing.setName(shape.getName());
        existing.setDescription(shape.getDescription());
        existing.setUri(shape.getUri());
        existing.setTargetClass(shape.getTargetClass());
        existing.setContent(shape.getContent());
        existing.setContentFormat(shape.getContentFormat().name());
        existing.setCategory(shape.getCategory());
        existing.setTags(shape.getTags() != null ? shape.getTags().toArray(new String[0]) : null);
        existing.setVersion(existing.getVersion() + 1);
        
        existing = shapeRepository.save(existing);
        log.info("Updated shape: {} ({})", existing.getName(), existing.getId());
        return toModel(existing);
    }

    @Transactional
    public void delete(UUID id) {
        if (!shapeRepository.existsById(id)) {
            throw new ResourceNotFoundException("Shape", id.toString());
        }
        shapeRepository.deleteById(id);
        log.info("Deleted shape: {}", id);
    }

    @Transactional(readOnly = true)
    public List<Shape> getTemplates() {
        return shapeRepository.findByIsTemplateTrue().stream()
            .map(this::toModel)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<String> getCategories(UUID projectId) {
        return shapeRepository.findCategoriesByProjectId(projectId);
    }

    private ShapeEntity toEntity(Shape model) {
        return ShapeEntity.builder()
            .id(model.getId())
            .projectId(model.getProjectId())
            .uri(model.getUri())
            .name(model.getName())
            .description(model.getDescription())
            .targetClass(model.getTargetClass())
            .contentFormat(model.getContentFormat() != null ? model.getContentFormat().name() : "TURTLE")
            .content(model.getContent())
            .isTemplate(model.isTemplate())
            .category(model.getCategory())
            .tags(model.getTags() != null ? model.getTags().toArray(new String[0]) : null)
            .version(model.getVersion())
            .createdBy(model.getCreatedBy())
            .build();
    }

    private Shape toModel(ShapeEntity entity) {
        return Shape.builder()
            .id(entity.getId())
            .projectId(entity.getProjectId())
            .uri(entity.getUri())
            .name(entity.getName())
            .description(entity.getDescription())
            .targetClass(entity.getTargetClass())
            .contentFormat(Shape.ContentFormat.valueOf(entity.getContentFormat()))
            .content(entity.getContent())
            .template(entity.getIsTemplate())
            .category(entity.getCategory())
            .tags(entity.getTags() != null ? Arrays.asList(entity.getTags()) : Collections.emptyList())
            .version(entity.getVersion())
            .createdBy(entity.getCreatedBy())
            .createdAt(entity.getCreatedAt())
            .updatedAt(entity.getUpdatedAt())
            .build();
    }
}
