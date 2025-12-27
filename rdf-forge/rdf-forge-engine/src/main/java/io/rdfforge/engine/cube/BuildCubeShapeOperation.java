package io.rdfforge.engine.cube;

import io.rdfforge.engine.operation.Operation;
import io.rdfforge.engine.operation.OperationException;
import io.rdfforge.engine.operation.PluginInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.datatypes.TypeMapper;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.XSD;
import org.springframework.stereotype.Component;

import java.io.StringWriter;
import java.util.*;

/**
 * Operation to build a SHACL cube:Constraint from an observation stream.
 *
 * This operation analyzes a stream of observations and generates a SHACL shape
 * describing their structure. It is equivalent to barnard59's buildCubeShape operation.
 *
 * The operation:
 * 1. Collects information about all properties used in observations
 * 2. Analyzes datatypes, cardinality, and value sets
 * 3. Generates a cube:Constraint that is also a sh:NodeShape
 * 4. Outputs the constraint as an RDF Model
 */
@Slf4j
@Component
@PluginInfo(
    author = "RDF Forge",
    version = "1.0.0",
    tags = {"cube", "shacl", "constraint", "shape"},
    documentation = "https://cube.link/",
    builtIn = true
)
public class BuildCubeShapeOperation implements Operation {

    private static final String CUBE_NS = "https://cube.link/";
    private static final String SHACL_NS = "http://www.w3.org/ns/shacl#";
    private static final String SCHEMA_NS = "https://schema.org/";
    private static final String META_NS = "https://cube.link/meta/";

    @Override
    public String getId() {
        return "build-cube-shape";
    }

    @Override
    public String getName() {
        return "Build Cube Shape";
    }

    @Override
    public String getDescription() {
        return "Build SHACL constraint shape from observation stream (equivalent to barnard59 buildCubeShape)";
    }

    @Override
    public OperationType getType() {
        return OperationType.CUBE;
    }

    @Override
    public Map<String, ParameterSpec> getParameters() {
        return Map.of(
            "cubeUri", new ParameterSpec("cubeUri", "URI of the cube", String.class, true, null),
            "constraintUri", new ParameterSpec("constraintUri", "URI for the generated constraint (default: cubeUri + /constraint)", String.class, false, null),
            "inferDimensionRoles", new ParameterSpec("inferDimensionRoles", "Infer KeyDimension/MeasureDimension roles from data", Boolean.class, false, true),
            "includeValueEnumeration", new ParameterSpec("includeValueEnumeration", "Include sh:in for small value sets (max 50 values)", Boolean.class, false, true),
            "maxEnumValues", new ParameterSpec("maxEnumValues", "Maximum values to include in sh:in constraint", Integer.class, false, 50)
        );
    }

    @Override
    public OperationResult execute(OperationContext context) throws OperationException {
        String cubeUri = (String) context.parameters().get("cubeUri");
        String constraintUri = (String) context.parameters().getOrDefault("constraintUri", cubeUri + "/constraint");
        Boolean inferRoles = (Boolean) context.parameters().getOrDefault("inferDimensionRoles", true);
        Boolean includeEnum = (Boolean) context.parameters().getOrDefault("includeValueEnumeration", true);
        Integer maxEnumValues = (Integer) context.parameters().getOrDefault("maxEnumValues", 50);

        Model inputModel = context.inputModel();
        if (inputModel == null || inputModel.isEmpty()) {
            throw new OperationException(getId(), "No input model provided - need observations to analyze");
        }

        log.info("Building cube shape from observations for cube: {}", cubeUri);

        // Collect observations
        List<Resource> observations = collectObservations(inputModel);
        if (observations.isEmpty()) {
            throw new OperationException(getId(), "No cube:Observation resources found in input model");
        }

        log.info("Analyzing {} observations", observations.size());

        // Analyze property statistics
        Map<String, PropertyStats> propertyStats = analyzeObservations(inputModel, observations);
        log.info("Detected {} distinct properties", propertyStats.size());

        // Build the constraint model
        Model constraintModel = buildConstraint(
            cubeUri,
            constraintUri,
            propertyStats,
            inferRoles,
            includeEnum,
            maxEnumValues
        );

        // Log constraint summary
        StringWriter writer = new StringWriter();
        RDFDataMgr.write(writer, constraintModel, RDFFormat.TURTLE_PRETTY);
        log.debug("Generated constraint:\n{}", writer);

        // Return result with the constraint model
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("observationsAnalyzed", observations.size());
        metadata.put("propertiesDetected", propertyStats.size());
        metadata.put("constraintUri", constraintUri);
        metadata.put("constraintTriples", constraintModel.size());

        return new OperationResult(
            true,
            null,
            constraintModel,
            metadata,
            null
        );
    }

    /**
     * Collect all cube:Observation resources from the model.
     */
    private List<Resource> collectObservations(Model model) {
        List<Resource> observations = new ArrayList<>();
        Resource observationType = model.createResource(CUBE_NS + "Observation");

        ResIterator iter = model.listSubjectsWithProperty(RDF.type, observationType);
        while (iter.hasNext()) {
            observations.add(iter.next());
        }

        return observations;
    }

    /**
     * Analyze all observations to collect property statistics.
     */
    private Map<String, PropertyStats> analyzeObservations(Model model, List<Resource> observations) {
        Map<String, PropertyStats> stats = new LinkedHashMap<>();

        for (Resource obs : observations) {
            StmtIterator stmts = model.listStatements(obs, null, (RDFNode) null);
            while (stmts.hasNext()) {
                Statement stmt = stmts.next();
                Property prop = stmt.getPredicate();

                // Skip rdf:type
                if (prop.equals(RDF.type)) continue;

                String propUri = prop.getURI();
                stats.computeIfAbsent(propUri, k -> new PropertyStats(propUri)).addValue(stmt.getObject());
            }
        }

        return stats;
    }

    /**
     * Build the SHACL constraint model.
     */
    private Model buildConstraint(
            String cubeUri,
            String constraintUri,
            Map<String, PropertyStats> stats,
            boolean inferRoles,
            boolean includeEnum,
            int maxEnumValues) {

        Model model = ModelFactory.createDefaultModel();

        // Set prefixes
        model.setNsPrefix("sh", SHACL_NS);
        model.setNsPrefix("cube", CUBE_NS);
        model.setNsPrefix("meta", META_NS);
        model.setNsPrefix("xsd", XSD.NS);
        model.setNsPrefix("schema", SCHEMA_NS);
        model.setNsPrefix("rdfs", RDFS.uri);

        // Create the constraint resource
        Resource constraint = model.createResource(constraintUri);

        // Add types: cube:Constraint and sh:NodeShape
        model.add(constraint, RDF.type, model.createResource(CUBE_NS + "Constraint"));
        model.add(constraint, RDF.type, model.createResource(SHACL_NS + "NodeShape"));

        // Target cube:Observation
        model.add(constraint, model.createProperty(SHACL_NS, "targetClass"),
            model.createResource(CUBE_NS + "Observation"));

        // Link constraint to cube
        Resource cube = model.createResource(cubeUri);
        model.add(cube, model.createProperty(CUBE_NS, "observationConstraint"), constraint);

        // Add property shapes
        int order = 1;
        for (Map.Entry<String, PropertyStats> entry : stats.entrySet()) {
            String propUri = entry.getKey();
            PropertyStats propStats = entry.getValue();

            Resource propShape = addPropertyShape(model, constraint, propUri, propStats,
                inferRoles, includeEnum, maxEnumValues, order++);
        }

        // Add closed shape constraint
        model.add(constraint, model.createProperty(SHACL_NS, "closed"),
            model.createTypedLiteral(false));

        return model;
    }

    /**
     * Add a property shape to the constraint.
     */
    private Resource addPropertyShape(
            Model model,
            Resource constraint,
            String propUri,
            PropertyStats stats,
            boolean inferRoles,
            boolean includeEnum,
            int maxEnumValues,
            int order) {

        Resource propShape = model.createResource();
        model.add(constraint, model.createProperty(SHACL_NS, "property"), propShape);

        // sh:path
        Resource property = model.createResource(propUri);
        model.add(propShape, model.createProperty(SHACL_NS, "path"), property);

        // sh:minCount and sh:maxCount (observations must have exactly one value per dimension)
        model.add(propShape, model.createProperty(SHACL_NS, "minCount"),
            model.createTypedLiteral(1));
        model.add(propShape, model.createProperty(SHACL_NS, "maxCount"),
            model.createTypedLiteral(1));

        // sh:datatype if consistent
        if (stats.hasConsistentDatatype()) {
            model.add(propShape, model.createProperty(SHACL_NS, "datatype"),
                model.createResource(stats.getDatatype()));
        }

        // sh:nodeKind for IRIs
        if (stats.allValuesAreIris()) {
            model.add(propShape, model.createProperty(SHACL_NS, "nodeKind"),
                model.createResource(SHACL_NS + "IRI"));
        }

        // sh:in for small enumerated value sets
        if (includeEnum && stats.hasEnumeratedValues() && stats.getUniqueCount() <= maxEnumValues) {
            RDFList valueList = createValueList(model, stats);
            if (valueList != null) {
                model.add(propShape, model.createProperty(SHACL_NS, "in"), valueList);
            }
        }

        // sh:order
        model.add(propShape, model.createProperty(SHACL_NS, "order"),
            model.createTypedLiteral(order));

        // Infer dimension roles
        if (inferRoles) {
            addDimensionRole(model, property, stats);
        }

        // Add property metadata (name from local name)
        String localName = propUri.contains("#")
            ? propUri.substring(propUri.lastIndexOf('#') + 1)
            : propUri.substring(propUri.lastIndexOf('/') + 1);
        model.add(property, model.createProperty(SCHEMA_NS, "name"),
            model.createLiteral(formatPropertyName(localName)));

        return propShape;
    }

    /**
     * Create an RDF list of values for sh:in.
     */
    private RDFList createValueList(Model model, PropertyStats stats) {
        List<RDFNode> values = new ArrayList<>();

        for (String value : stats.getValues()) {
            if (stats.allValuesAreIris()) {
                values.add(model.createResource(value));
            } else if (stats.hasConsistentDatatype()) {
                values.add(model.createTypedLiteral(value,
                    TypeMapper.getInstance().getSafeTypeByName(stats.getDatatype())));
            } else {
                values.add(model.createLiteral(value));
            }
        }

        if (values.isEmpty()) return null;
        return model.createList(values.iterator());
    }

    /**
     * Add dimension role (KeyDimension or MeasureDimension) to property.
     */
    private void addDimensionRole(Model model, Resource property, PropertyStats stats) {
        // Heuristic: numeric properties are likely measures, others are dimensions
        String datatype = stats.getDatatype();
        boolean isNumeric = datatype != null && (
            datatype.equals(XSD.integer.getURI()) ||
            datatype.equals(XSD.decimal.getURI()) ||
            datatype.equals(XSD.xdouble.getURI()) ||
            datatype.equals(XSD.xfloat.getURI()) ||
            datatype.equals(XSD.xint.getURI()) ||
            datatype.equals(XSD.xlong.getURI())
        );

        if (isNumeric && stats.getUniqueCount() > 10) {
            // Likely a measure
            model.add(property, RDF.type, model.createResource(CUBE_NS + "MeasureDimension"));
        } else {
            // Likely a key dimension
            model.add(property, RDF.type, model.createResource(CUBE_NS + "KeyDimension"));
        }
    }

    /**
     * Format property local name to human-readable name.
     */
    private String formatPropertyName(String localName) {
        // Convert camelCase or snake_case to words
        return localName
            .replaceAll("([a-z])([A-Z])", "$1 $2")
            .replaceAll("_", " ")
            .toLowerCase();
    }

    /**
     * Internal class to track property statistics.
     */
    private static class PropertyStats {
        private final String propertyUri;
        private final Set<String> datatypes = new HashSet<>();
        private final Set<String> values = new LinkedHashSet<>();
        private int count = 0;
        private boolean hasIris = false;
        private boolean hasLiterals = false;
        private boolean hasBlankNodes = false;

        PropertyStats(String propertyUri) {
            this.propertyUri = propertyUri;
        }

        void addValue(RDFNode value) {
            count++;
            if (value.isLiteral()) {
                hasLiterals = true;
                Literal lit = value.asLiteral();
                if (lit.getDatatypeURI() != null) {
                    datatypes.add(lit.getDatatypeURI());
                } else {
                    datatypes.add(XSD.xstring.getURI());
                }
                // Store value (limit to prevent memory issues)
                if (values.size() < 1000) {
                    values.add(lit.getString());
                }
            } else if (value.isURIResource()) {
                hasIris = true;
                if (values.size() < 1000) {
                    values.add(value.asResource().getURI());
                }
            } else if (value.isAnon()) {
                hasBlankNodes = true;
            }
        }

        boolean hasConsistentDatatype() {
            return datatypes.size() == 1 && hasLiterals && !hasIris;
        }

        String getDatatype() {
            if (datatypes.isEmpty()) return null;
            return datatypes.iterator().next();
        }

        boolean hasEnumeratedValues() {
            return values.size() < 100;
        }

        int getUniqueCount() {
            return values.size();
        }

        Set<String> getValues() {
            return values;
        }

        boolean allValuesAreIris() {
            return hasIris && !hasLiterals && !hasBlankNodes;
        }

        int getCount() {
            return count;
        }
    }
}
