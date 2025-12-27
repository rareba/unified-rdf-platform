package io.rdfforge.dimension.service;

import io.rdfforge.common.model.Pipeline;
import io.rdfforge.common.model.Shape;
import io.rdfforge.dimension.dto.GeneratedArtifact;
import io.rdfforge.dimension.entity.CubeEntity;
import io.rdfforge.dimension.repository.CubeRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
public class CubeService {

    private final CubeRepository cubeRepository;
    private final RestTemplate restTemplate;

    @Value("${rdf-forge.services.shacl.url:http://shacl-service:8080}")
    private String shaclServiceUrl;

    @Value("${rdf-forge.services.pipeline.url:http://pipeline-service:8080}")
    private String pipelineServiceUrl;

    public CubeService(CubeRepository cubeRepository, RestTemplateBuilder restTemplateBuilder) {
        this.cubeRepository = cubeRepository;
        this.restTemplate = restTemplateBuilder.build();
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

        if (updates.getName() != null)
            cube.setName(updates.getName());
        if (updates.getUri() != null)
            cube.setUri(updates.getUri());
        if (updates.getDescription() != null)
            cube.setDescription(updates.getDescription());
        if (updates.getSourceDataId() != null)
            cube.setSourceDataId(updates.getSourceDataId());
        if (updates.getPipelineId() != null)
            cube.setPipelineId(updates.getPipelineId());
        if (updates.getShapeId() != null)
            cube.setShapeId(updates.getShapeId());
        if (updates.getTriplestoreId() != null)
            cube.setTriplestoreId(updates.getTriplestoreId());
        if (updates.getGraphUri() != null)
            cube.setGraphUri(updates.getGraphUri());
        if (updates.getMetadata() != null)
            cube.setMetadata(updates.getMetadata());

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

    // ===== New methods for cube definition architecture =====

    /**
     * Generate a SHACL shape from the cube definition's column mappings.
     * The generated shape validates cube observations against the defined
     * structure.
     */
    public GeneratedArtifact generateShape(UUID cubeId, String shapeName, String targetClass) {
        CubeEntity cube = cubeRepository.findById(cubeId)
                .orElseThrow(() -> new NoSuchElementException("Cube not found: " + cubeId));

        // Generate shape name if not provided
        String finalShapeName = shapeName != null ? shapeName : cube.getName() + " Validation Shape";
        String finalTargetClass = targetClass != null ? targetClass : "http://purl.org/linked-data/cube#Observation";

        // Build SHACL shape Turtle from cube metadata
        String shapeContent = buildShaclShape(cube, finalShapeName, finalTargetClass);

        // Create shape object
        Shape shape = Shape.builder()
                .name(finalShapeName)
                .projectId(cube.getProjectId())
                .targetClass(finalTargetClass)
                .contentFormat(Shape.ContentFormat.TURTLE)
                .content(shapeContent)
                .category("Cube Validation")
                .description("Auto-generated shape for cube: " + cube.getName())
                .build();

        // Call SHACL Service
        Shape createdShape;
        try {
            createdShape = restTemplate.postForObject(
                    shaclServiceUrl + "/api/v1/shapes",
                    shape,
                    Shape.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create shape in SHACL service: " + e.getMessage(), e);
        }

        if (createdShape == null || createdShape.getId() == null) {
            throw new RuntimeException("SHACL service returned null shape or ID");
        }

        // Link the shape to the cube
        cube.setShapeId(createdShape.getId());
        cube.setUpdatedAt(Instant.now());
        cubeRepository.save(cube);

        return new GeneratedArtifact(
                createdShape.getId(), finalShapeName, "SHACL_SHAPE");
    }

    /**
     * Generate a draft ETL pipeline from the cube definition.
     * The pipeline includes steps for loading data, mapping to RDF, and publishing.
     */
    public GeneratedArtifact generatePipeline(UUID cubeId, String pipelineName, UUID triplestoreId, String graphUri) {
        CubeEntity cube = cubeRepository.findById(cubeId)
                .orElseThrow(() -> new NoSuchElementException("Cube not found: " + cubeId));

        // Generate pipeline name if not provided
        String finalPipelineName = pipelineName != null ? pipelineName : "Pipeline: " + cube.getName();

        // Build pipeline definition JSON from cube metadata
        String pipelineDefinition = buildPipelineDefinition(cube, triplestoreId, graphUri);

        // Create pipeline object
        Pipeline pipeline = Pipeline.builder()
                .name(finalPipelineName)
                .projectId(cube.getProjectId())
                .description("Auto-generated pipeline for cube: " + cube.getName())
                .definitionFormat(Pipeline.DefinitionFormat.JSON)
                .definition(pipelineDefinition)
                .tags(Collections.singletonList("AUTO_GENERATED"))
                .build();

        // Call Pipeline Service
        Pipeline createdPipeline;
        try {
            createdPipeline = restTemplate.postForObject(
                    pipelineServiceUrl + "/api/v1/pipelines",
                    pipeline,
                    Pipeline.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create pipeline in Pipeline service: " + e.getMessage(), e);
        }

        if (createdPipeline == null || createdPipeline.getId() == null) {
            throw new RuntimeException("Pipeline service returned null pipeline or ID");
        }

        // Link the pipeline to the cube
        cube.setPipelineId(createdPipeline.getId());
        if (triplestoreId != null) {
            cube.setTriplestoreId(triplestoreId);
        }
        if (graphUri != null) {
            cube.setGraphUri(graphUri);
        }
        cube.setUpdatedAt(Instant.now());
        cubeRepository.save(cube);

        return new GeneratedArtifact(
                createdPipeline.getId(), finalPipelineName, "PIPELINE");
    }

    /**
     * Link an existing SHACL shape to the cube.
     */
    public CubeEntity linkShape(UUID cubeId, UUID shapeId) {
        CubeEntity cube = cubeRepository.findById(cubeId)
                .orElseThrow(() -> new NoSuchElementException("Cube not found: " + cubeId));

        cube.setShapeId(shapeId);
        cube.setUpdatedAt(Instant.now());
        return cubeRepository.save(cube);
    }

    /**
     * Link an existing pipeline to the cube.
     */
    public CubeEntity linkPipeline(UUID cubeId, UUID pipelineId) {
        CubeEntity cube = cubeRepository.findById(cubeId)
                .orElseThrow(() -> new NoSuchElementException("Cube not found: " + cubeId));

        cube.setPipelineId(pipelineId);
        cube.setUpdatedAt(Instant.now());
        return cubeRepository.save(cube);
    }

    /**
     * Remove the shape link from the cube.
     */
    public CubeEntity unlinkShape(UUID cubeId) {
        CubeEntity cube = cubeRepository.findById(cubeId)
                .orElseThrow(() -> new NoSuchElementException("Cube not found: " + cubeId));

        cube.setShapeId(null);
        cube.setUpdatedAt(Instant.now());
        return cubeRepository.save(cube);
    }

    /**
     * Remove the pipeline link from the cube.
     */
    public CubeEntity unlinkPipeline(UUID cubeId) {
        CubeEntity cube = cubeRepository.findById(cubeId)
                .orElseThrow(() -> new NoSuchElementException("Cube not found: " + cubeId));

        cube.setPipelineId(null);
        cube.setUpdatedAt(Instant.now());
        return cubeRepository.save(cube);
    }

    // ===== Helper methods for artifact generation =====

    @SuppressWarnings("unchecked")
    private String buildShaclShape(CubeEntity cube, String shapeName, String targetClass) {
        StringBuilder turtle = new StringBuilder();
        String baseUri = cube.getUri();
        if (baseUri == null)
            baseUri = "http://example.org/cube";

        // Prefixes
        turtle.append("@prefix sh: <http://www.w3.org/ns/shacl#> .\n");
        turtle.append("@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .\n");
        turtle.append("@prefix qb: <http://purl.org/linked-data/cube#> .\n");
        turtle.append("@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .\n");
        turtle.append("@prefix : <").append(baseUri).append("#> .\n\n");

        // Node shape for observations
        String shapeId = shapeName.toLowerCase().replace(" ", "-").replaceAll("[^a-z0-9-]", "");
        turtle.append(":").append(shapeId).append("\n");
        turtle.append("    a sh:NodeShape ;\n");
        turtle.append("    sh:targetClass <").append(targetClass).append("> ;\n");
        turtle.append("    rdfs:label \"").append(shapeName).append("\"@en ;\n");

        // Add property shapes from column mappings
        Map<String, Object> metadata = cube.getMetadata();
        if (metadata != null && metadata.containsKey("columnMappings")) {
            Object mappingsObj = metadata.get("columnMappings");
            if (mappingsObj instanceof java.util.List<?> mappings) {
                for (Object m : mappings) {
                    if (m instanceof Map<?, ?> mapping) {
                        String role = (String) mapping.get("role");
                        if (role != null && !"ignore".equals(role)) {
                            String columnName = (String) mapping.get("name");
                            String datatype = (String) mapping.get("datatype");
                            String propUri = baseUri + "#"
                                    + columnName.toLowerCase().replace(" ", "-").replaceAll("[^a-z0-9-]", "");

                            turtle.append("    sh:property [\n");
                            turtle.append("        sh:path <").append(propUri).append("> ;\n");
                            turtle.append("        sh:minCount 1 ;\n");
                            if (datatype != null) {
                                turtle.append("        sh:datatype ").append(datatype).append(" ;\n");
                            }
                            turtle.append("    ] ;\n");
                        }
                    }
                }
            }
        }

        turtle.append("    .\n");
        return turtle.toString();
    }

    private String buildPipelineDefinition(CubeEntity cube, UUID triplestoreId, String graphUri) {
        String finalGraphUri = graphUri != null ? graphUri : cube.getGraphUri();
        if (finalGraphUri == null) {
            finalGraphUri = cube.getUri();
        }

        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"name\": \"Pipeline: ").append(cube.getName()).append("\",\n");
        json.append("  \"description\": \"Auto-generated pipeline for cube: ").append(cube.getName()).append("\",\n");
        json.append("  \"steps\": [\n");

        // Step 1: Load data
        json.append("    {\n");
        json.append("      \"id\": \"step-1\",\n");
        json.append("      \"operation\": \"load-csv\",\n");
        json.append("      \"name\": \"Load Source Data\",\n");
        json.append("      \"params\": {\n");
        json.append("        \"sourceId\": \"").append(cube.getSourceDataId()).append("\"\n");
        json.append("      }\n");
        json.append("    },\n");

        // Step 2: Map to RDF
        json.append("    {\n");
        json.append("      \"id\": \"step-2\",\n");
        json.append("      \"operation\": \"create-observation\",\n");
        json.append("      \"name\": \"Create Cube Observations\",\n");
        json.append("      \"params\": {\n");
        json.append("        \"cubeUri\": \"").append(cube.getUri()).append("\",\n");
        json.append("        \"cubeId\": \"").append(cube.getId()).append("\"\n");
        json.append("      }\n");
        json.append("    },\n");

        // Step 3: Validate (if shape exists)
        if (cube.getShapeId() != null) {
            json.append("    {\n");
            json.append("      \"id\": \"step-3\",\n");
            json.append("      \"operation\": \"validate-shacl\",\n");
            json.append("      \"name\": \"Validate Against SHACL Shape\",\n");
            json.append("      \"params\": {\n");
            json.append("        \"shapeId\": \"").append(cube.getShapeId()).append("\"\n");
            json.append("      }\n");
            json.append("    },\n");
        }

        // Step 4: Publish to triplestore
        json.append("    {\n");
        json.append("      \"id\": \"step-4\",\n");
        json.append("      \"operation\": \"publish-rdf\",\n");
        json.append("      \"name\": \"Publish to Triplestore\",\n");
        json.append("      \"params\": {\n");
        if (triplestoreId != null) {
            json.append("        \"triplestoreId\": \"").append(triplestoreId).append("\",\n");
        }
        json.append("        \"graphUri\": \"").append(finalGraphUri).append("\"\n");
        json.append("      }\n");
        json.append("    }\n");

        json.append("  ]\n");
        json.append("}\n");

        return json.toString();
    }
}
