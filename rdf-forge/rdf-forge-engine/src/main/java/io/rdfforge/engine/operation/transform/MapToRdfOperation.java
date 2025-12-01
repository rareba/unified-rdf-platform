package io.rdfforge.engine.operation.transform;

import io.rdfforge.engine.operation.Operation;
import io.rdfforge.engine.operation.OperationException;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.datatypes.xsd.XSDDatatype;
import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.RDF;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Stream;

@Slf4j
@Component
public class MapToRdfOperation implements Operation {

    @Override
    public String getId() {
        return "map-to-rdf";
    }

    @Override
    public String getName() {
        return "Map to RDF";
    }

    @Override
    public String getDescription() {
        return "Convert tabular data to RDF triples using mapping rules";
    }

    @Override
    public OperationType getType() {
        return OperationType.TRANSFORM;
    }

    @Override
    public Map<String, ParameterSpec> getParameters() {
        return Map.of(
            "baseUri", new ParameterSpec("baseUri", "Base URI for generated resources", String.class, true, null),
            "subjectColumn", new ParameterSpec("subjectColumn", "Column to use for subject URI", String.class, false, null),
            "subjectTemplate", new ParameterSpec("subjectTemplate", "Template for subject URI (e.g., {id})", String.class, false, null),
            "typeUri", new ParameterSpec("typeUri", "RDF type URI for generated resources", String.class, false, null),
            "propertyMappings", new ParameterSpec("propertyMappings", "Map of column -> property URI", Map.class, true, null),
            "datatypeMappings", new ParameterSpec("datatypeMappings", "Map of column -> XSD datatype", Map.class, false, null)
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public OperationResult execute(OperationContext context) throws OperationException {
        String baseUri = (String) context.parameters().get("baseUri");
        String subjectColumn = (String) context.parameters().get("subjectColumn");
        String subjectTemplate = (String) context.parameters().get("subjectTemplate");
        String typeUri = (String) context.parameters().get("typeUri");
        Map<String, String> propertyMappings = (Map<String, String>) context.parameters().get("propertyMappings");
        Map<String, String> datatypeMappings = (Map<String, String>) context.parameters().getOrDefault("datatypeMappings", Collections.emptyMap());

        if (!baseUri.endsWith("/") && !baseUri.endsWith("#")) {
            baseUri = baseUri + "/";
        }

        Model model = ModelFactory.createDefaultModel();
        final String finalBaseUri = baseUri;

        if (context.inputStream() == null) {
            throw new OperationException(getId(), "No input stream provided");
        }

        Stream<?> inputStream = context.inputStream();
        long[] counter = {0};

        Stream<Statement> statementStream = inputStream
            .filter(item -> item instanceof Map)
            .flatMap(item -> {
                Map<String, Object> row = (Map<String, Object>) item;
                counter[0]++;

                String subjectUri = generateSubjectUri(finalBaseUri, row, subjectColumn, subjectTemplate, counter[0]);
                Resource subject = model.createResource(subjectUri);

                List<Statement> statements = new ArrayList<>();

                if (typeUri != null && !typeUri.isEmpty()) {
                    statements.add(model.createStatement(subject, RDF.type, model.createResource(typeUri)));
                }

                for (Map.Entry<String, String> mapping : propertyMappings.entrySet()) {
                    String column = mapping.getKey();
                    String propertyUri = mapping.getValue();
                    
                    Object value = row.get(column);
                    if (value != null && !value.toString().isEmpty()) {
                        Property property = model.createProperty(propertyUri);
                        RDFNode object = createRdfNode(model, value, datatypeMappings.get(column));
                        statements.add(model.createStatement(subject, property, object));
                    }
                }

                if (context.callback() != null && counter[0] % 1000 == 0) {
                    context.callback().onProgress(counter[0], -1);
                }

                return statements.stream();
            });

        statementStream.forEach(model::add);

        if (context.callback() != null) {
            context.callback().onLog("INFO", "Mapped " + counter[0] + " rows to RDF");
            context.callback().onMetric("rowsProcessed", counter[0]);
            context.callback().onMetric("triplesGenerated", model.size());
        }

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("rowsProcessed", counter[0]);
        metadata.put("triplesGenerated", model.size());

        return new OperationResult(true, null, model, metadata, null);
    }

    private String generateSubjectUri(String baseUri, Map<String, Object> row, 
                                       String subjectColumn, String subjectTemplate, long rowNumber) {
        if (subjectTemplate != null && !subjectTemplate.isEmpty()) {
            String uri = subjectTemplate;
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                uri = uri.replace("{" + entry.getKey() + "}", 
                    entry.getValue() != null ? sanitizeUri(entry.getValue().toString()) : "");
            }
            if (!uri.startsWith("http://") && !uri.startsWith("https://")) {
                uri = baseUri + uri;
            }
            return uri;
        } else if (subjectColumn != null && row.containsKey(subjectColumn)) {
            return baseUri + sanitizeUri(row.get(subjectColumn).toString());
        } else {
            return baseUri + "row/" + rowNumber;
        }
    }

    private String sanitizeUri(String value) {
        return value.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    private RDFNode createRdfNode(Model model, Object value, String datatypeUri) {
        if (value instanceof String strValue) {
            if (strValue.startsWith("http://") || strValue.startsWith("https://")) {
                return model.createResource(strValue);
            }
            
            if (datatypeUri != null) {
                return model.createTypedLiteral(strValue, new XSDDatatype(datatypeUri.substring(datatypeUri.lastIndexOf('#') + 1)));
            }
            return model.createLiteral(strValue);
        } else if (value instanceof Number numValue) {
            if (value instanceof Integer || value instanceof Long) {
                return model.createTypedLiteral(numValue.longValue());
            } else {
                return model.createTypedLiteral(numValue.doubleValue());
            }
        } else if (value instanceof Boolean boolValue) {
            return model.createTypedLiteral(boolValue);
        } else {
            return model.createLiteral(value.toString());
        }
    }
}
