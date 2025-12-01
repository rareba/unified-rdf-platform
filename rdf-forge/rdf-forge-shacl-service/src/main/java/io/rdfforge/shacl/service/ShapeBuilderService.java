package io.rdfforge.shacl.service;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ShapeBuilderService {
    private static final String SHACL_NS = "http://www.w3.org/ns/shacl#";
    private static final String XSD_NS = "http://www.w3.org/2001/XMLSchema#";
    private static final String RDF_NS = "http://www.w3.org/1999/02/22-rdf-syntax-ns#";
    private static final String RDFS_NS = "http://www.w3.org/2000/01/rdf-schema#";

    public String buildShape(ShapeDefinition definition) {
        StringBuilder turtle = new StringBuilder();
        
        turtle.append("@prefix sh: <").append(SHACL_NS).append("> .\n");
        turtle.append("@prefix xsd: <").append(XSD_NS).append("> .\n");
        turtle.append("@prefix rdf: <").append(RDF_NS).append("> .\n");
        turtle.append("@prefix rdfs: <").append(RDFS_NS).append("> .\n");
        
        for (String prefix : definition.getPrefixes().keySet()) {
            turtle.append("@prefix ").append(prefix).append(": <")
                  .append(definition.getPrefixes().get(prefix)).append("> .\n");
        }
        turtle.append("\n");

        turtle.append("<").append(definition.getUri()).append(">\n");
        turtle.append("  a sh:NodeShape ;\n");
        
        if (definition.getLabel() != null) {
            turtle.append("  rdfs:label \"").append(escapeString(definition.getLabel())).append("\" ;\n");
        }
        
        if (definition.getDescription() != null) {
            turtle.append("  rdfs:comment \"").append(escapeString(definition.getDescription())).append("\" ;\n");
        }

        if (definition.getTargetClass() != null) {
            turtle.append("  sh:targetClass <").append(definition.getTargetClass()).append("> ;\n");
        } else if (definition.getTargetNode() != null) {
            turtle.append("  sh:targetNode <").append(definition.getTargetNode()).append("> ;\n");
        }

        if (definition.isClosed()) {
            turtle.append("  sh:closed true ;\n");
            if (definition.getIgnoredProperties() != null && !definition.getIgnoredProperties().isEmpty()) {
                turtle.append("  sh:ignoredProperties ( ");
                turtle.append(definition.getIgnoredProperties().stream()
                    .map(p -> "<" + p + ">")
                    .collect(Collectors.joining(" ")));
                turtle.append(" ) ;\n");
            }
        }

        if (definition.getSeverity() != null) {
            turtle.append("  sh:severity sh:").append(definition.getSeverity()).append(" ;\n");
        }

        for (int i = 0; i < definition.getProperties().size(); i++) {
            PropertyShapeDefinition prop = definition.getProperties().get(i);
            turtle.append("  sh:property [\n");
            turtle.append(buildPropertyShape(prop, "    "));
            turtle.append("  ]");
            turtle.append(i < definition.getProperties().size() - 1 ? " ;\n" : " .\n");
        }

        return turtle.toString();
    }

    private String buildPropertyShape(PropertyShapeDefinition prop, String indent) {
        StringBuilder sb = new StringBuilder();
        
        sb.append(indent).append("sh:path <").append(prop.getPath()).append("> ;\n");

        if (prop.getName() != null) {
            sb.append(indent).append("sh:name \"").append(escapeString(prop.getName())).append("\" ;\n");
        }

        if (prop.getDescription() != null) {
            sb.append(indent).append("sh:description \"").append(escapeString(prop.getDescription())).append("\" ;\n");
        }

        if (prop.getDatatype() != null) {
            sb.append(indent).append("sh:datatype xsd:").append(prop.getDatatype()).append(" ;\n");
        }

        if (prop.getNodeKind() != null) {
            sb.append(indent).append("sh:nodeKind sh:").append(prop.getNodeKind()).append(" ;\n");
        }

        if (prop.getClassConstraint() != null) {
            sb.append(indent).append("sh:class <").append(prop.getClassConstraint()).append("> ;\n");
        }

        if (prop.getMinCount() != null) {
            sb.append(indent).append("sh:minCount ").append(prop.getMinCount()).append(" ;\n");
        }

        if (prop.getMaxCount() != null) {
            sb.append(indent).append("sh:maxCount ").append(prop.getMaxCount()).append(" ;\n");
        }

        if (prop.getMinLength() != null) {
            sb.append(indent).append("sh:minLength ").append(prop.getMinLength()).append(" ;\n");
        }

        if (prop.getMaxLength() != null) {
            sb.append(indent).append("sh:maxLength ").append(prop.getMaxLength()).append(" ;\n");
        }

        if (prop.getPattern() != null) {
            sb.append(indent).append("sh:pattern \"").append(escapeString(prop.getPattern())).append("\" ;\n");
            if (prop.getFlags() != null) {
                sb.append(indent).append("sh:flags \"").append(prop.getFlags()).append("\" ;\n");
            }
        }

        if (prop.getMinInclusive() != null) {
            sb.append(indent).append("sh:minInclusive ").append(prop.getMinInclusive()).append(" ;\n");
        }

        if (prop.getMaxInclusive() != null) {
            sb.append(indent).append("sh:maxInclusive ").append(prop.getMaxInclusive()).append(" ;\n");
        }

        if (prop.getMinExclusive() != null) {
            sb.append(indent).append("sh:minExclusive ").append(prop.getMinExclusive()).append(" ;\n");
        }

        if (prop.getMaxExclusive() != null) {
            sb.append(indent).append("sh:maxExclusive ").append(prop.getMaxExclusive()).append(" ;\n");
        }

        if (prop.getInValues() != null && !prop.getInValues().isEmpty()) {
            sb.append(indent).append("sh:in ( ");
            sb.append(prop.getInValues().stream()
                .map(v -> "\"" + escapeString(v.toString()) + "\"")
                .collect(Collectors.joining(" ")));
            sb.append(" ) ;\n");
        }

        if (prop.getHasValue() != null) {
            if (prop.getHasValue() instanceof String) {
                sb.append(indent).append("sh:hasValue \"").append(escapeString((String)prop.getHasValue())).append("\" ;\n");
            } else {
                sb.append(indent).append("sh:hasValue ").append(prop.getHasValue()).append(" ;\n");
            }
        }

        if (prop.getNodeShape() != null) {
            sb.append(indent).append("sh:node <").append(prop.getNodeShape()).append("> ;\n");
        }

        if (prop.getMessage() != null) {
            sb.append(indent).append("sh:message \"").append(escapeString(prop.getMessage())).append("\" ;\n");
        }

        if (prop.getSeverity() != null) {
            sb.append(indent).append("sh:severity sh:").append(prop.getSeverity()).append(" ;\n");
        }

        String result = sb.toString();
        if (result.endsWith(" ;\n")) {
            result = result.substring(0, result.length() - 2) + "\n";
        }
        return result;
    }

    private String escapeString(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }

    @Data
    @Builder
    public static class ShapeDefinition {
        private String uri;
        private String label;
        private String description;
        private String targetClass;
        private String targetNode;
        private boolean closed;
        private List<String> ignoredProperties;
        private String severity;
        private java.util.Map<String, String> prefixes;
        @Builder.Default
        private List<PropertyShapeDefinition> properties = new ArrayList<>();
    }

    @Data
    @Builder
    public static class PropertyShapeDefinition {
        private String path;
        private String name;
        private String description;
        private String datatype;
        private String nodeKind;
        private String classConstraint;
        private Integer minCount;
        private Integer maxCount;
        private Integer minLength;
        private Integer maxLength;
        private String pattern;
        private String flags;
        private Number minInclusive;
        private Number maxInclusive;
        private Number minExclusive;
        private Number maxExclusive;
        private List<Object> inValues;
        private Object hasValue;
        private String nodeShape;
        private String message;
        private String severity;
    }
}
