package io.rdfforge.dimension.service;

import io.rdfforge.dimension.entity.CubeEntity;
import io.rdfforge.dimension.repository.CubeRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class CubeService {

    private final CubeRepository cubeRepository;

    public CubeService(CubeRepository cubeRepository) {
        this.cubeRepository = cubeRepository;
    }

    public Page<CubeEntity> search(UUID projectId, String search, Pageable pageable) {
        return cubeRepository.search(projectId, search, pageable);
    }

    public Optional<CubeEntity> findById(UUID id) {
        return cubeRepository.findById(id);
    }

    public CubeEntity create(CubeEntity cube) {
        cube.setCreatedAt(Instant.now());
        return cubeRepository.save(cube);
    }

    public CubeEntity update(UUID id, CubeEntity updates) {
        CubeEntity cube = cubeRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Cube not found: " + id));

        if (updates.getName() != null) cube.setName(updates.getName());
        if (updates.getUri() != null) cube.setUri(updates.getUri());
        if (updates.getDescription() != null) cube.setDescription(updates.getDescription());
        if (updates.getSourceDataId() != null) cube.setSourceDataId(updates.getSourceDataId());
        if (updates.getPipelineId() != null) cube.setPipelineId(updates.getPipelineId());
        if (updates.getShapeId() != null) cube.setShapeId(updates.getShapeId());
        if (updates.getTriplestoreId() != null) cube.setTriplestoreId(updates.getTriplestoreId());
        if (updates.getGraphUri() != null) cube.setGraphUri(updates.getGraphUri());
        if (updates.getMetadata() != null) cube.setMetadata(updates.getMetadata());

        cube.setUpdatedAt(Instant.now());
        return cubeRepository.save(cube);
    }

    public void delete(UUID id) {
        cubeRepository.deleteById(id);
    }

    public CubeEntity markPublished(UUID id, Long observationCount) {
        CubeEntity cube = cubeRepository.findById(id)
            .orElseThrow(() -> new NoSuchElementException("Cube not found: " + id));

        cube.setLastPublished(Instant.now());
        if (observationCount != null) {
            cube.setObservationCount(observationCount);
        }
        cube.setUpdatedAt(Instant.now());
        return cubeRepository.save(cube);
    }
}
