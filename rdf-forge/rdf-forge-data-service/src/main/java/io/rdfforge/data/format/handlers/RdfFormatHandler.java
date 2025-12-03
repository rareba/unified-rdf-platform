package io.rdfforge.data.format.handlers;

import io.rdfforge.data.format.DataFormatHandler;
import io.rdfforge.data.format.DataFormatInfo;
import io.rdfforge.data.format.DataFormatInfo.FormatOption;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.springframework.stereotype.Component;

import java.io.*;
import java.util.*;

import static io.rdfforge.data.format.DataFormatInfo.*;

/**
 * Handler for RDF formats (Turtle, JSON-LD, N-Triples, RDF/XML).
 *
 * Converts RDF triples to tabular format with columns:
 * - subject, predicate, object (for raw triples)
 * - Or flattened properties per subject (for resource view)
 *
 * Supports:
 * - Turtle (.ttl)
 * - JSON-LD (.jsonld)
 * - N-Triples (.nt)
 * - RDF/XML (.rdf)
 * - N-Quads (.nq)
 */
@Component
public class RdfFormatHandler implements DataFormatHandler {

    private static final DataFormatInfo INFO = new DataFormatInfo(
        "rdf",
        "RDF (Resource Description Framework)",
        "Semantic web format for linked data. Supports Turtle, JSON-LD, N-Triples, and RDF/XML.",
        "text/turtle",
        List.of("ttl", "turtle", "jsonld", "nt", "ntriples", "rdf", "xml", "nq"),
        true,
        true,
        true,
        Map.of(
            "format", new FormatOption("format", "RDF Format", "select",
                "RDF serialization format", "turtle",
                List.of("turtle", "jsonld", "ntriples", "rdfxml", "nquads")),
            "viewMode", new FormatOption("viewMode", "View Mode", "select",
                "How to present RDF data", "triples",
                List.of("triples", "resources")),
            "baseUri", new FormatOption("baseUri", "Base URI", "string",
                "Base URI for relative URIs", ""),
            "prefixHandling", new FormatOption("prefixHandling", "Prefix Handling", "select",
                "How to handle URI prefixes in output", "full",
                List.of("full", "prefixed", "localName"))
        ),
        List.of(
            CAPABILITY_READ,
            CAPABILITY_WRITE,
            CAPABILITY_ANALYZE,
            CAPABILITY_PREVIEW,
            CAPABILITY_STREAMING,
            CAPABILITY_SCHEMA_INFERENCE
        )
    );

    @Override
    public DataFormatInfo getFormatInfo() {
        return INFO;
    }

    @Override
    public boolean supportsExtension(String extension) {
        return extension != null && List.of(
            "ttl", "turtle", "jsonld", "nt", "ntriples", "rdf", "xml", "nq"
        ).contains(extension.toLowerCase());
    }

    @Override
    public boolean supportsMimeType(String mimeType) {
        return mimeType != null && (
            mimeType.equalsIgnoreCase("text/turtle") ||
            mimeType.equalsIgnoreCase("application/ld+json") ||
            mimeType.equalsIgnoreCase("application/n-triples") ||
            mimeType.equalsIgnoreCase("application/rdf+xml") ||
            mimeType.equalsIgnoreCase("application/n-quads")
        );
    }

    @Override
    public PreviewResult preview(InputStream input, Map<String, Object> options, int maxRows) {
        try {
            String viewMode = getOption(options, "viewMode", "triples");
            Model model = loadModel(input, options);

            if ("resources".equals(viewMode)) {
                return previewResources(model, maxRows, options);
            } else {
                return previewTriples(model, maxRows, options);
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to preview RDF: " + e.getMessage(), e);
        }
    }

    private PreviewResult previewTriples(Model model, int maxRows, Map<String, Object> options) {
        String prefixHandling = getOption(options, "prefixHandling", "full");
        List<String> columns = List.of("subject", "predicate", "object", "objectType");
        List<Map<String, Object>> rows = new ArrayList<>();

        StmtIterator iter = model.listStatements();
        int count = 0;

        while (iter.hasNext() && count < maxRows) {
            Statement stmt = iter.next();
            Map<String, Object> row = new LinkedHashMap<>();

            row.put("subject", formatResource(stmt.getSubject(), prefixHandling, model));
            row.put("predicate", formatResource(stmt.getPredicate(), prefixHandling, model));

            RDFNode object = stmt.getObject();
            if (object.isLiteral()) {
                Literal lit = object.asLiteral();
                row.put("object", lit.getValue());
                row.put("objectType", "literal:" + (lit.getDatatype() != null ?
                    lit.getDatatype().getURI() : "string"));
            } else {
                row.put("object", formatResource(object.asResource(), prefixHandling, model));
                row.put("objectType", "uri");
            }

            rows.add(row);
            count++;
        }

        long totalRows = model.size();
        boolean hasMore = totalRows > maxRows;

        return new PreviewResult(columns, rows, totalRows, hasMore);
    }

    private PreviewResult previewResources(Model model, int maxRows, Map<String, Object> options) {
        String prefixHandling = getOption(options, "prefixHandling", "full");

        // Get unique subjects
        Set<Resource> subjects = new LinkedHashSet<>();
        StmtIterator iter = model.listStatements();
        while (iter.hasNext()) {
            subjects.add(iter.next().getSubject());
        }

        // Collect all unique predicates
        Set<String> predicateSet = new LinkedHashSet<>();
        predicateSet.add("uri");

        for (Resource subject : subjects) {
            StmtIterator stmts = model.listStatements(subject, null, (RDFNode) null);
            while (stmts.hasNext()) {
                Statement stmt = stmts.next();
                predicateSet.add(formatResource(stmt.getPredicate(), "localName", model));
            }
        }

        List<String> columns = new ArrayList<>(predicateSet);
        List<Map<String, Object>> rows = new ArrayList<>();
        int count = 0;

        for (Resource subject : subjects) {
            if (count >= maxRows) break;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("uri", formatResource(subject, prefixHandling, model));

            StmtIterator stmts = model.listStatements(subject, null, (RDFNode) null);
            while (stmts.hasNext()) {
                Statement stmt = stmts.next();
                String predKey = formatResource(stmt.getPredicate(), "localName", model);

                RDFNode object = stmt.getObject();
                Object value;
                if (object.isLiteral()) {
                    value = object.asLiteral().getValue();
                } else {
                    value = formatResource(object.asResource(), prefixHandling, model);
                }

                // Handle multiple values for same predicate
                Object existing = row.get(predKey);
                if (existing != null) {
                    if (existing instanceof List) {
                        ((List<Object>) existing).add(value);
                    } else {
                        List<Object> list = new ArrayList<>();
                        list.add(existing);
                        list.add(value);
                        row.put(predKey, list);
                    }
                } else {
                    row.put(predKey, value);
                }
            }

            rows.add(row);
            count++;
        }

        long totalRows = subjects.size();
        boolean hasMore = totalRows > maxRows;

        return new PreviewResult(columns, rows, totalRows, hasMore);
    }

    @Override
    public AnalysisResult analyze(InputStream input, Map<String, Object> options) {
        try {
            Model model = loadModel(input, options);

            // Analyze predicates (properties)
            Map<Property, Integer> predicateCounts = new LinkedHashMap<>();
            Map<Property, Set<Object>> predicateSamples = new LinkedHashMap<>();
            Map<Property, String> predicateTypes = new LinkedHashMap<>();

            StmtIterator iter = model.listStatements();
            while (iter.hasNext()) {
                Statement stmt = iter.next();
                Property pred = stmt.getPredicate();

                predicateCounts.merge(pred, 1, Integer::sum);

                Set<Object> samples = predicateSamples.computeIfAbsent(pred, k -> new LinkedHashSet<>());
                RDFNode object = stmt.getObject();
                if (samples.size() < 5) {
                    if (object.isLiteral()) {
                        samples.add(object.asLiteral().getValue());
                    } else {
                        samples.add(object.asResource().getURI());
                    }
                }

                // Detect type
                String type = object.isLiteral() ? "literal" : "uri";
                String existing = predicateTypes.get(pred);
                if (existing == null) {
                    predicateTypes.put(pred, type);
                } else if (!existing.equals(type)) {
                    predicateTypes.put(pred, "mixed");
                }
            }

            // Build column info for predicates
            List<ColumnInfo> columns = new ArrayList<>();
            columns.add(new ColumnInfo("subject", "uri", 0,
                model.listSubjects().toSet().size(), List.of(), Map.of()));

            for (Map.Entry<Property, Integer> entry : predicateCounts.entrySet()) {
                Property pred = entry.getKey();
                columns.add(new ColumnInfo(
                    pred.getLocalName(),
                    predicateTypes.getOrDefault(pred, "string"),
                    0,
                    predicateSamples.getOrDefault(pred, Set.of()).size(),
                    new ArrayList<>(predicateSamples.getOrDefault(pred, Set.of())),
                    Map.of("uri", pred.getURI(), "occurrences", entry.getValue())
                ));
            }

            // Get namespaces
            Map<String, String> prefixes = model.getNsPrefixMap();

            return new AnalysisResult(columns, model.size(), Map.of(
                "tripleCount", model.size(),
                "subjectCount", model.listSubjects().toSet().size(),
                "predicateCount", predicateCounts.size(),
                "prefixes", prefixes
            ));

        } catch (Exception e) {
            throw new RuntimeException("Failed to analyze RDF: " + e.getMessage(), e);
        }
    }

    @Override
    public Iterator<Map<String, Object>> readIterator(InputStream input, Map<String, Object> options) {
        try {
            String viewMode = getOption(options, "viewMode", "triples");
            String prefixHandling = getOption(options, "prefixHandling", "full");
            Model model = loadModel(input, options);

            if ("resources".equals(viewMode)) {
                Set<Resource> subjects = new LinkedHashSet<>();
                StmtIterator iter = model.listStatements();
                while (iter.hasNext()) {
                    subjects.add(iter.next().getSubject());
                }

                Iterator<Resource> subjectIter = subjects.iterator();

                return new Iterator<>() {
                    @Override
                    public boolean hasNext() {
                        return subjectIter.hasNext();
                    }

                    @Override
                    public Map<String, Object> next() {
                        Resource subject = subjectIter.next();
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("uri", formatResource(subject, prefixHandling, model));

                        StmtIterator stmts = model.listStatements(subject, null, (RDFNode) null);
                        while (stmts.hasNext()) {
                            Statement stmt = stmts.next();
                            String predKey = stmt.getPredicate().getLocalName();
                            RDFNode object = stmt.getObject();
                            Object value = object.isLiteral() ?
                                object.asLiteral().getValue() :
                                formatResource(object.asResource(), prefixHandling, model);
                            row.put(predKey, value);
                        }

                        return row;
                    }
                };
            } else {
                StmtIterator stmtIter = model.listStatements();

                return new Iterator<>() {
                    @Override
                    public boolean hasNext() {
                        return stmtIter.hasNext();
                    }

                    @Override
                    public Map<String, Object> next() {
                        Statement stmt = stmtIter.next();
                        Map<String, Object> row = new LinkedHashMap<>();

                        row.put("subject", formatResource(stmt.getSubject(), prefixHandling, model));
                        row.put("predicate", formatResource(stmt.getPredicate(), prefixHandling, model));

                        RDFNode object = stmt.getObject();
                        if (object.isLiteral()) {
                            row.put("object", object.asLiteral().getValue());
                            row.put("objectType", "literal");
                        } else {
                            row.put("object", formatResource(object.asResource(), prefixHandling, model));
                            row.put("objectType", "uri");
                        }

                        return row;
                    }
                };
            }

        } catch (Exception e) {
            throw new RuntimeException("Failed to read RDF: " + e.getMessage(), e);
        }
    }

    @Override
    public void write(List<Map<String, Object>> data, List<String> columns, OutputStream output, Map<String, Object> options) {
        try {
            String format = getOption(options, "format", "turtle");
            String baseUri = getOption(options, "baseUri", "http://example.org/");

            Model model = ModelFactory.createDefaultModel();

            // Set common prefixes
            model.setNsPrefix("ex", baseUri);
            model.setNsPrefix("xsd", "http://www.w3.org/2001/XMLSchema#");
            model.setNsPrefix("rdfs", "http://www.w3.org/2000/01/rdf-schema#");

            // Check if data is in triples format or resources format
            boolean isTriplesFormat = columns.contains("subject") &&
                                      columns.contains("predicate") &&
                                      columns.contains("object");

            if (isTriplesFormat) {
                // Write triples directly
                for (Map<String, Object> row : data) {
                    String subjectUri = String.valueOf(row.get("subject"));
                    String predicateUri = String.valueOf(row.get("predicate"));
                    Object objectValue = row.get("object");
                    String objectType = String.valueOf(row.getOrDefault("objectType", "literal"));

                    Resource subject = model.createResource(expandUri(subjectUri, baseUri));
                    Property predicate = model.createProperty(expandUri(predicateUri, baseUri));

                    if ("uri".equals(objectType)) {
                        Resource object = model.createResource(expandUri(String.valueOf(objectValue), baseUri));
                        model.add(subject, predicate, object);
                    } else {
                        Literal object = model.createLiteral(String.valueOf(objectValue));
                        model.add(subject, predicate, object);
                    }
                }
            } else {
                // Write resources with properties
                String subjectColumn = columns.contains("uri") ? "uri" : columns.get(0);
                for (Map<String, Object> row : data) {
                    String subjectUri = String.valueOf(row.get(subjectColumn));
                    Resource subject = model.createResource(expandUri(subjectUri, baseUri));

                    for (String col : columns) {
                        if (col.equals(subjectColumn)) continue;
                        Object value = row.get(col);
                        if (value == null) continue;

                        Property predicate = model.createProperty(baseUri + col);

                        if (value instanceof String && ((String) value).startsWith("http")) {
                            model.add(subject, predicate, model.createResource((String) value));
                        } else {
                            model.add(subject, predicate, model.createLiteral(String.valueOf(value)));
                        }
                    }
                }
            }

            // Write model in specified format
            RDFFormat rdfFormat = getRdfFormat(format);
            RDFDataMgr.write(output, model, rdfFormat);

        } catch (Exception e) {
            throw new RuntimeException("Failed to write RDF: " + e.getMessage(), e);
        }
    }

    private Model loadModel(InputStream input, Map<String, Object> options) {
        String format = getOption(options, "format", "turtle");
        String baseUri = getOption(options, "baseUri", "http://example.org/");

        Model model = ModelFactory.createDefaultModel();
        Lang lang = getLang(format);

        RDFDataMgr.read(model, input, baseUri, lang);

        return model;
    }

    private Lang getLang(String format) {
        switch (format.toLowerCase()) {
            case "jsonld":
            case "json-ld":
                return Lang.JSONLD;
            case "ntriples":
            case "nt":
                return Lang.NTRIPLES;
            case "rdfxml":
            case "rdf":
            case "xml":
                return Lang.RDFXML;
            case "nquads":
            case "nq":
                return Lang.NQUADS;
            case "turtle":
            case "ttl":
            default:
                return Lang.TURTLE;
        }
    }

    private RDFFormat getRdfFormat(String format) {
        switch (format.toLowerCase()) {
            case "jsonld":
            case "json-ld":
                return RDFFormat.JSONLD_PRETTY;
            case "ntriples":
            case "nt":
                return RDFFormat.NTRIPLES;
            case "rdfxml":
            case "rdf":
            case "xml":
                return RDFFormat.RDFXML_PRETTY;
            case "nquads":
            case "nq":
                return RDFFormat.NQUADS;
            case "turtle":
            case "ttl":
            default:
                return RDFFormat.TURTLE_PRETTY;
        }
    }

    private String formatResource(Resource resource, String handling, Model model) {
        if (resource.isAnon()) {
            return "_:" + resource.getId().getLabelString();
        }

        String uri = resource.getURI();
        if (uri == null) return resource.toString();

        switch (handling) {
            case "localName":
                String local = resource.getLocalName();
                return local != null && !local.isEmpty() ? local : uri;

            case "prefixed":
                String prefix = model.getNsURIPrefix(resource.getNameSpace());
                if (prefix != null) {
                    return prefix + ":" + resource.getLocalName();
                }
                return uri;

            case "full":
            default:
                return uri;
        }
    }

    private String expandUri(String uri, String baseUri) {
        if (uri.startsWith("http://") || uri.startsWith("https://") || uri.startsWith("urn:")) {
            return uri;
        }
        if (uri.contains(":")) {
            // Might be prefixed, just return as is for now
            return uri;
        }
        return baseUri + uri;
    }

    @SuppressWarnings("unchecked")
    private <T> T getOption(Map<String, Object> options, String key, T defaultValue) {
        if (options == null || !options.containsKey(key)) {
            return defaultValue;
        }
        Object value = options.get(key);
        if (value == null) return defaultValue;
        return (T) value;
    }
}
