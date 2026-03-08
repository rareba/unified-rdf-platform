package io.rdfforge.engine.cube;

import io.rdfforge.engine.operation.Operation;
import io.rdfforge.engine.operation.OperationException;
import io.rdfforge.engine.operation.PluginInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.XSD;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;

/**
 * Operation to build a complete RDF Data Cube with Data Structure Definition.
 *
 * This operation takes a cube definition and generates:
 * 1. qb:DataSet - the cube dataset
 * 2. qb:DataStructureDefinition - the cube structure
 * 3. qb:DimensionProperty - dimension definitions
 * 4. qb:MeasureProperty - measure definitions
 * 5. qb:AttributeProperty - attribute definitions
 *
 * The cube definition can be loaded from a JSON configuration or passed directly.
 */
@Slf4j
@Component
@PluginInfo(
    author = "RDF Forge",
    version = "1.0.0",
    tags = {"cube", "datacube", "rdf", "qb", "structure"},
    documentation = "https://cube.link/",
    builtIn = true
)
public class BuildCubeOperation implements Operation {

    private static final String QB_NS = "http://purl.org/linked-data/cube#";
    private static final String CUBE_NS = "https://cube.link/";
    private static final String DCT_NS = "http://purl.org/dc/terms/";
    private static final String SCHEMA_NS = "https://schema.org/";

    @Override
    public String getId() {
        return "build-cube";
    }

    @Override
    public String getName() {
        return "Build Cube";
    }

    @Override
    public String getDescription() {
        return "Build a complete RDF Data Cube with Data Structure Definition from a cube definition";
    }

    @Override
    public OperationType getType() {
        return OperationType.CUBE;
    }

    @Override
    public Map<String, ParameterSpec> getParameters() {
        return Map.of(
            "cubeDefinition", new ParameterSpec("cubeDefinition", "Cube definition JSON object", Map.class, true, null),
            "cubeDefinitionId", new ParameterSpec("cubeDefinitionId", "ID of a stored cube definition to load", String.class, false, null),
            "baseUri", new ParameterSpec("baseUri", "Base URI for the cube resources", String.class, false, null),
            "cubeUri", new ParameterSpec("cubeUri", "Full URI for the cube dataset", String.class, false, null),
            "includeMetadata", new ParameterSpec("includeMetadata", "Include DC metadata on dataset", Boolean.class, false, true)
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public OperationResult execute(OperationContext context) throws OperationException {
        // Get cube definition from parameters
        Map<String, Object> cubeDef = (Map<String, Object>) context.parameters().get("cubeDefinition");
        String cubeDefinitionId = (String) context.parameters().get("cubeDefinitionId");
        String baseUri = (String) context.parameters().get("baseUri");
        String cubeUri = (String) context.parameters().get("cubeUri");
        Boolean includeMetadata = (Boolean) context.parameters().getOrDefault("includeMetadata", true);

        // Load from ID if provided
        if (cubeDef == null && cubeDefinitionId != null) {
            cubeDef = loadCubeDefinition(cubeDefinitionId);
        }

        if (cubeDef == null) {
            throw new OperationException(getId(), "No cube definition provided. Provide either 'cubeDefinition' or 'cubeDefinitionId'");
        }

        // Determine URIs
        String name = (String) cubeDef.get("name");
        String description = (String) cubeDef.get("description");
        Map<String, Object> metadata = (Map<String, Object>) cubeDef.get("metadata");

        if (cubeUri == null) {
            if (baseUri == null) {
                baseUri = cubeDef.containsKey("baseUri") ? (String) cubeDef.get("baseUri") : "https://example.org/cube/";
            }
            if (!baseUri.endsWith("/")) {
                baseUri = baseUri + "/";
            }
            String cubeId = name != null ? slugify(name) : "cube-" + UUID.randomUUID().toString().substring(0, 8);
            cubeUri = baseUri + cubeId;
        }

        log.info("Building cube structure for: {}", cubeUri);

        Model model = ModelFactory.createDefaultModel();

        // Set prefixes
        model.setNsPrefix("qb", QB_NS);
        model.setNsPrefix("cube", CUBE_NS);
        model.setNsPrefix("dct", DCT_NS);
        model.setNsPrefix("schema", SCHEMA_NS);
        model.setNsPrefix("xsd", XSD.NS);

        // Build the cube structure
        Resource cube = buildCubeStructure(model, cubeUri, name, description, cubeDef, includeMetadata, metadata);

        log.info("Generated cube structure with {} triples", model.size());

        Map<String, Object> resultMetadata = new HashMap<>();
        resultMetadata.put("cubeUri", cubeUri);
        resultMetadata.put("name", name);
        resultMetadata.put("triplesGenerated", model.size());
        resultMetadata.put("dimensionsCount", getDimensionsFromDef(cubeDef).size());
        resultMetadata.put("measuresCount", getMeasuresFromDef(cubeDef).size());

        return new OperationResult(
            true,
            null,
            model,
            resultMetadata,
            null
        );
    }

    @SuppressWarnings("unchecked")
    private Resource buildCubeStructure(Model model, String cubeUri, String name, String description,
                                        Map<String, Object> cubeDef, boolean includeMetadata,
                                        Map<String, Object> metadata) {
        // Create resources
        Resource cubeResource = model.createResource(cubeUri);
        String dsdUri = cubeUri + "/structure";
        Resource dsdResource = model.createResource(dsdUri);

        Resource qbDataSet = model.createResource(QB_NS + "DataSet");
        Resource qbDataStructureDefinition = model.createResource(QB_NS + "DataStructureDefinition");
        Resource qbDimensionProperty = model.createResource(QB_NS + "DimensionProperty");
        Resource qbMeasureProperty = model.createResource(QB_NS + "MeasureProperty");
        Resource qbAttributeProperty = model.createResource(QB_NS + "AttributeProperty");
        Resource qbKeyDimension = model.createResource(CUBE_NS + "KeyDimension");
        Resource qbMeasureDimension = model.createResource(CUBE_NS + "MeasureDimension");

        Property qbStructure = model.createProperty(QB_NS, "structure");
        Property qbComponent = model.createProperty(QB_NS, "component");
        Property qbDimension = model.createProperty(QB_NS, "dimension");
        Property qbMeasure = model.createProperty(QB_NS, "measure");
        Property qbAttribute = model.createProperty(QB_NS, "attribute");
        Property qbConcept = model.createProperty(QB_NS, "concept");

        Property dctTitle = model.createProperty(DCT_NS, "title");
        Property dctDescription = model.createProperty(DCT_NS, "description");
        Property dctPublisher = model.createProperty(DCT_NS, "publisher");
        Property dctLicense = model.createProperty(DCT_NS, "license");
        Property dctIssued = model.createProperty(DCT_NS, "issued");
        Property dctModified = model.createProperty(DCT_NS, "modified");
        Property dctCreator = model.createProperty(DCT_NS, "creator");

        // Create DataSet
        model.add(cubeResource, RDF.type, qbDataSet);
        if (name != null) {
            model.add(cubeResource, RDFS.label, model.createLiteral(name, "en"));
            if (includeMetadata) {
                model.add(cubeResource, dctTitle, model.createLiteral(name, "en"));
            }
        }
        if (description != null) {
            model.add(cubeResource, RDFS.comment, model.createLiteral(description, "en"));
            if (includeMetadata) {
                model.add(cubeResource, dctDescription, model.createLiteral(description, "en"));
            }
        }
        model.add(cubeResource, qbStructure, dsdResource);

        // Add metadata if provided
        if (includeMetadata && metadata != null) {
            addMetadata(model, cubeResource, metadata, dctPublisher, dctLicense, dctIssued, dctModified, dctCreator);
        }

        // Create Data Structure Definition
        model.add(dsdResource, RDF.type, qbDataStructureDefinition);
        if (name != null) {
            model.add(dsdResource, RDFS.label, model.createLiteral(name + " Structure", "en"));
        }

        // Get dimensions, measures, attributes from cube definition
        List<Map<String, Object>> dimensions = getDimensionsFromDef(cubeDef);
        List<Map<String, Object>> measures = getMeasuresFromDef(cubeDef);
        List<Map<String, Object>> attributes = getAttributesFromDef(cubeDef);

        // Add dimensions to DSD
        int order = 1;
        for (Map<String, Object> dim : dimensions) {
            String dimName = (String) dim.get("name");
            String dimUri = (String) dim.getOrDefault("uri", cubeUri + "/dimension/" + slugify(dimName));
            String conceptUri = (String) dim.get("conceptUri");
            Boolean isKey = (Boolean) dim.getOrDefault("keyDimension", true);

            Resource dimResource = model.createResource(dimUri);
            model.add(dimResource, RDF.type, qbDimensionProperty);
            model.add(dimResource, RDFS.label, model.createLiteral(dimName, "en"));

            if (isKey) {
                model.add(dimResource, RDF.type, qbKeyDimension);
            }

            if (conceptUri != null) {
                model.add(dimResource, qbConcept, model.createResource(conceptUri));
            }

            // Add component to DSD
            Resource component = model.createResource();
            model.add(dsdResource, qbComponent, component);
            model.add(component, qbDimension, dimResource);
            model.add(component, model.createProperty(QB_NS + "order"), model.createTypedLiteral(order++));
        }

        // Add measures to DSD
        for (Map<String, Object> meas : measures) {
            String measName = (String) meas.get("name");
            String measUri = (String) meas.getOrDefault("uri", cubeUri + "/measure/" + slugify(measName));
            String conceptUri = (String) meas.get("conceptUri");
            String unitUri = (String) meas.get("unitUri");
            String dataType = (String) meas.get("dataType");

            Resource measResource = model.createResource(measUri);
            model.add(measResource, RDF.type, qbMeasureProperty);
            model.add(measResource, RDF.type, qbMeasureDimension);
            model.add(measResource, RDFS.label, model.createLiteral(measName, "en"));

            if (conceptUri != null) {
                model.add(measResource, qbConcept, model.createResource(conceptUri));
            }

            if (dataType != null) {
                Resource range = model.createResource(dataType);
                model.add(measResource, RDFS.range, range);
            }

            if (unitUri != null) {
                model.add(measResource, model.createProperty(CUBE_NS + "unit"), model.createResource(unitUri));
            }

            // Add component to DSD
            Resource component = model.createResource();
            model.add(dsdResource, qbComponent, component);
            model.add(component, qbMeasure, measResource);
        }

        // Add attributes to DSD
        for (Map<String, Object> attr : attributes) {
            String attrName = (String) attr.get("name");
            String attrUri = (String) attr.getOrDefault("uri", cubeUri + "/attribute/" + slugify(attrName));

            Resource attrResource = model.createResource(attrUri);
            model.add(attrResource, RDF.type, qbAttributeProperty);
            model.add(attrResource, RDFS.label, model.createLiteral(attrName, "en"));

            // Add component to DSD
            Resource component = model.createResource();
            model.add(dsdResource, qbComponent, component);
            model.add(component, qbAttribute, attrResource);
        }

        return cubeResource;
    }

    private void addMetadata(Model model, Resource cubeResource, Map<String, Object> metadata,
                            Property dctPublisher, Property dctLicense, Property dctIssued,
                            Property dctModified, Property dctCreator) {
        if (metadata.get("publisher") != null) {
            String publisher = (String) metadata.get("publisher");
            model.add(cubeResource, dctPublisher, model.createLiteral(publisher));
        }
        if (metadata.get("publisherUri") != null) {
            model.add(cubeResource, dctPublisher, model.createResource((String) metadata.get("publisherUri")));
        }
        if (metadata.get("license") != null) {
            String license = (String) metadata.get("license");
            if (license.startsWith("http")) {
                model.add(cubeResource, dctLicense, model.createResource(license));
            } else {
                model.add(cubeResource, dctLicense, model.createLiteral(license));
            }
        }
        if (metadata.get("issued") != null) {
            model.add(cubeResource, dctIssued, model.createTypedLiteral((String) metadata.get("issued"), XSD.date));
        }
        if (metadata.get("modified") != null) {
            model.add(cubeResource, dctModified, model.createTypedLiteral((String) metadata.get("modified"), XSD.date));
        }
        if (metadata.get("creator") != null) {
            model.add(cubeResource, dctCreator, model.createLiteral((String) metadata.get("creator")));
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getDimensionsFromDef(Map<String, Object> cubeDef) {
        if (cubeDef.containsKey("dimensions")) {
            return (List<Map<String, Object>>) cubeDef.get("dimensions");
        }
        if (cubeDef.containsKey("columnMappings")) {
            // Parse from column mappings
            List<Map<String, Object>> mappings = (List<Map<String, Object>>) cubeDef.get("columnMappings");
            List<Map<String, Object>> dims = new ArrayList<>();
            for (Map<String, Object> mapping : mappings) {
                if ("dimension".equals(mapping.get("role"))) {
                    Map<String, Object> dim = new HashMap<>();
                    dim.put("name", mapping.get("name"));
                    dim.put("uri", mapping.get("predicateUri"));
                    dim.put("keyDimension", true);
                    dims.add(dim);
                }
            }
            return dims;
        }
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getMeasuresFromDef(Map<String, Object> cubeDef) {
        if (cubeDef.containsKey("measures")) {
            return (List<Map<String, Object>>) cubeDef.get("measures");
        }
        if (cubeDef.containsKey("columnMappings")) {
            // Parse from column mappings
            List<Map<String, Object>> mappings = (List<Map<String, Object>>) cubeDef.get("columnMappings");
            List<Map<String, Object>> meas = new ArrayList<>();
            for (Map<String, Object> mapping : mappings) {
                if ("measure".equals(mapping.get("role"))) {
                    Map<String, Object> m = new HashMap<>();
                    m.put("name", mapping.get("name"));
                    m.put("uri", mapping.get("predicateUri"));
                    m.put("dataType", mapping.get("datatype"));
                    meas.add(m);
                }
            }
            return meas;
        }
        return Collections.emptyList();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getAttributesFromDef(Map<String, Object> cubeDef) {
        if (cubeDef.containsKey("attributes")) {
            return (List<Map<String, Object>>) cubeDef.get("attributes");
        }
        if (cubeDef.containsKey("columnMappings")) {
            // Parse from column mappings
            List<Map<String, Object>> mappings = (List<Map<String, Object>>) cubeDef.get("columnMappings");
            List<Map<String, Object>> attrs = new ArrayList<>();
            for (Map<String, Object> mapping : mappings) {
                if ("attribute".equals(mapping.get("role"))) {
                    Map<String, Object> attr = new HashMap<>();
                    attr.put("name", mapping.get("name"));
                    attr.put("uri", mapping.get("predicateUri"));
                    attrs.add(attr);
                }
            }
            return attrs;
        }
        return Collections.emptyList();
    }

    private Map<String, Object> loadCubeDefinition(String cubeDefinitionId) {
        // In a real implementation, this would load from a cube definition repository
        // For now, return null - the operation should receive the definition directly
        log.warn("Loading cube definition by ID not yet implemented: {}", cubeDefinitionId);
        return null;
    }

    private String slugify(String text) {
        if (text == null) return "unnamed";
        return text.toLowerCase()
            .replaceAll("[^a-z0-9-]+", "-")
            .replaceAll("^-+|-+$", "")
            .replaceAll("-+", "-");
    }
}
