package io.rdfforge.engine.cube;

import io.rdfforge.engine.operation.Operation;
import io.rdfforge.engine.operation.OperationException;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.XSD;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;

@Slf4j
@Component
public class CreateObservationOperation implements Operation {
    private static final String CUBE_NS = "https://cube.link/";
    private static final String QB_NS = "http://purl.org/linked-data/cube#";

    @Override
    public String getId() {
        return "create-observation";
    }

    @Override
    public String getName() {
        return "Create Observation";
    }

    @Override
    public String getDescription() {
        return "Generate RDF Cube observations from tabular data";
    }

    @Override
    public OperationType getType() {
        return OperationType.CUBE;
    }

    @Override
    public Map<String, ParameterSpec> getParameters() {
        return Map.of(
            "cubeUri", new ParameterSpec("cubeUri", "URI of the cube", String.class, true, null),
            "observationBaseUri", new ParameterSpec("observationBaseUri", "Base URI for observations", String.class, false, null),
            "dimensions", new ParameterSpec("dimensions", "Dimension column mappings", Map.class, true, null),
            "measures", new ParameterSpec("measures", "Measure column mappings", Map.class, true, null),
            "attributes", new ParameterSpec("attributes", "Attribute column mappings", Map.class, false, null),
            "dateFormat", new ParameterSpec("dateFormat", "Date format pattern", String.class, false, "yyyy-MM-dd"),
            "emitUndefined", new ParameterSpec("emitUndefined", "Emit cube:Undefined for NULL values instead of skipping", Boolean.class, false, false)
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public OperationResult execute(OperationContext context) throws OperationException {
        String cubeUri = (String) context.parameters().get("cubeUri");
        String observationBaseUri = (String) context.parameters().getOrDefault("observationBaseUri", cubeUri + "/observation/");
        Map<String, DimensionConfig> dimensions = (Map<String, DimensionConfig>) context.parameters().get("dimensions");
        Map<String, MeasureConfig> measures = (Map<String, MeasureConfig>) context.parameters().get("measures");
        Map<String, AttributeConfig> attributes = (Map<String, AttributeConfig>) context.parameters().getOrDefault("attributes", Collections.emptyMap());
        String dateFormat = (String) context.parameters().getOrDefault("dateFormat", "yyyy-MM-dd");
        Boolean emitUndefined = (Boolean) context.parameters().getOrDefault("emitUndefined", false);

        if (context.inputStream() == null) {
            throw new OperationException(getId(), "No input stream provided");
        }

        Model model = ModelFactory.createDefaultModel();
        model.setNsPrefix("cube", CUBE_NS);
        model.setNsPrefix("qb", QB_NS);

        Resource cubeResource = model.createResource(cubeUri);
        Property observedBy = model.createProperty(CUBE_NS, "observedBy");
        Property observationProp = model.createProperty(CUBE_NS, "Observation");
        
        // cube:Undefined resource for missing values (as per cube-link spec)
        Resource undefinedValue = model.createResource(CUBE_NS + "Undefined");

        long[] counter = {0};
        long[] undefinedCounter = {0};
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern(dateFormat);

        Stream<?> inputStream = context.inputStream();

        inputStream
            .filter(item -> item instanceof Map)
            .forEach(item -> {
                Map<String, Object> row = (Map<String, Object>) item;
                counter[0]++;

                String obsUri = generateObservationUri(observationBaseUri, row, dimensions, counter[0]);
                Resource observation = model.createResource(obsUri);

                model.add(observation, RDF.type, observationProp);
                model.add(observation, observedBy, cubeResource);

                for (Map.Entry<String, DimensionConfig> dim : dimensions.entrySet()) {
                    String column = dim.getKey();
                    DimensionConfig config = dim.getValue();
                    Object value = row.get(column);

                    Property dimProperty = model.createProperty(config.propertyUri);
                    if (value != null && !isNullOrEmpty(value)) {
                        RDFNode dimValue = createDimensionValue(model, value, config, dtf);
                        model.add(observation, dimProperty, dimValue);
                    } else if (emitUndefined) {
                        // Emit cube:Undefined for missing dimension values
                        model.add(observation, dimProperty, undefinedValue);
                        undefinedCounter[0]++;
                    }
                }

                for (Map.Entry<String, MeasureConfig> meas : measures.entrySet()) {
                    String column = meas.getKey();
                    MeasureConfig config = meas.getValue();
                    Object value = row.get(column);

                    Property measProperty = model.createProperty(config.propertyUri);
                    if (value != null && !isNullOrEmpty(value)) {
                        Literal measValue = createMeasureValue(model, value, config);
                        model.add(observation, measProperty, measValue);
                    } else if (emitUndefined) {
                        // Emit cube:Undefined for missing measure values
                        model.add(observation, measProperty, undefinedValue);
                        undefinedCounter[0]++;
                    }
                }

                for (Map.Entry<String, AttributeConfig> attr : attributes.entrySet()) {
                    String column = attr.getKey();
                    AttributeConfig config = attr.getValue();
                    Object value = row.get(column);

                    Property attrProperty = model.createProperty(config.propertyUri);
                    if (value != null && !isNullOrEmpty(value)) {
                        Literal attrValue = model.createLiteral(value.toString());
                        model.add(observation, attrProperty, attrValue);
                    } else if (emitUndefined) {
                        // Emit cube:Undefined for missing attribute values
                        model.add(observation, attrProperty, undefinedValue);
                        undefinedCounter[0]++;
                    }
                }

                if (context.callback() != null && counter[0] % 1000 == 0) {
                    context.callback().onProgress(counter[0], -1);
                }
            });

        if (context.callback() != null) {
            context.callback().onLog("INFO", "Created " + counter[0] + " observations");
            if (undefinedCounter[0] > 0) {
                context.callback().onLog("INFO", "Emitted " + undefinedCounter[0] + " cube:Undefined values for missing data");
            }
            context.callback().onMetric("observationCount", counter[0]);
            context.callback().onMetric("triplesGenerated", model.size());
            context.callback().onMetric("undefinedCount", undefinedCounter[0]);
        }

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("observationCount", counter[0]);
        metadata.put("triplesGenerated", model.size());
        metadata.put("undefinedCount", undefinedCounter[0]);
        metadata.put("cubeUri", cubeUri);

        return new OperationResult(true, null, model, metadata, null);
    }

    private String generateObservationUri(String baseUri, Map<String, Object> row, 
                                           Map<String, DimensionConfig> dimensions, long rowNumber) {
        StringBuilder uriBuilder = new StringBuilder(baseUri);
        List<String> keyParts = new ArrayList<>();
        
        for (Map.Entry<String, DimensionConfig> dim : dimensions.entrySet()) {
            if (dim.getValue().keyDimension) {
                Object value = row.get(dim.getKey());
                if (value != null) {
                    keyParts.add(sanitizeUri(value.toString()));
                }
            }
        }

        if (keyParts.isEmpty()) {
            uriBuilder.append(rowNumber);
        } else {
            uriBuilder.append(String.join("-", keyParts));
        }

        return uriBuilder.toString();
    }

    private String sanitizeUri(String value) {
        return value.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    /**
     * Check if a value is null, empty, or represents a null-like value.
     * Handles common CSV representations of NULL values.
     */
    private boolean isNullOrEmpty(Object value) {
        if (value == null) {
            return true;
        }
        String str = value.toString().trim();
        if (str.isEmpty()) {
            return true;
        }
        // Common NULL representations in CSV files
        String lower = str.toLowerCase();
        return lower.equals("null") || 
               lower.equals("na") || 
               lower.equals("n/a") || 
               lower.equals("-") ||
               lower.equals(".");
    }


    private RDFNode createDimensionValue(Model model, Object value, DimensionConfig config, DateTimeFormatter dtf) {
        if (config.valueUri != null) {
            String uri = config.valueUri.replace("{value}", sanitizeUri(value.toString()));
            return model.createResource(uri);
        }

        if ("date".equalsIgnoreCase(config.datatype)) {
            try {
                LocalDate date = LocalDate.parse(value.toString(), dtf);
                return model.createTypedLiteral(date.toString(), XSD.date.getURI());
            } catch (Exception e) {
                return model.createLiteral(value.toString());
            }
        }

        return model.createLiteral(value.toString());
    }

    private Literal createMeasureValue(Model model, Object value, MeasureConfig config) {
        if (value instanceof Number num) {
            if (value instanceof Integer || value instanceof Long) {
                return model.createTypedLiteral(num.longValue());
            } else {
                return model.createTypedLiteral(num.doubleValue());
            }
        }

        try {
            double d = Double.parseDouble(value.toString());
            return model.createTypedLiteral(d);
        } catch (NumberFormatException e) {
            return model.createLiteral(value.toString());
        }
    }

    @lombok.Data
    @lombok.Builder
    public static class DimensionConfig {
        private String propertyUri;
        private String valueUri;
        private String datatype;
        private boolean keyDimension;
        private String sharedDimensionUri;
    }

    @lombok.Data
    @lombok.Builder
    public static class MeasureConfig {
        private String propertyUri;
        private String datatype;
        private String unit;
    }

    @lombok.Data
    @lombok.Builder
    public static class AttributeConfig {
        private String propertyUri;
        private String datatype;
    }
}
