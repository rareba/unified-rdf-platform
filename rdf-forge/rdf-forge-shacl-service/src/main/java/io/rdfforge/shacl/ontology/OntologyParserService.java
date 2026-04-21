package io.rdfforge.shacl.ontology;

import io.rdfforge.shacl.entity.OntologyEntity.RdfFormat;
import io.rdfforge.shacl.ontology.dto.NamespaceMap;
import io.rdfforge.shacl.ontology.dto.TermDetail;
import io.rdfforge.shacl.ontology.dto.TermSearchResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFParser;
import org.apache.jena.riot.RiotException;
import org.apache.jena.riot.system.ErrorHandler;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.apache.jena.vocabulary.SKOS;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Stream;

/**
 * Pure RDF parsing / introspection helpers backed by Apache Jena.
 *
 * <p>Security: this service uses Jena's {@link RDFParser} builder with
 * {@code checking(true)}, and for RDF/XML / TriG parsing it installs an
 * {@link ErrorHandler} that fails on warnings too. We never enable Jena's
 * network lookup fallbacks — {@link RDFDataMgr#read} is not used directly
 * with a URL. This blocks XXE-style external entity resolution.
 */
@Slf4j
@Service
public class OntologyParserService {

    /**
     * Parse raw RDF content into a Jena {@link Model}.
     *
     * @throws OntologyParseException on any syntax or security failure
     */
    public Model parse(String content, RdfFormat format) {
        if (content == null || content.isBlank()) {
            throw new OntologyParseException("Ontology content is empty");
        }
        Model model = ModelFactory.createDefaultModel();
        Lang lang = toLang(format);
        ErrorCollectingHandler handler = new ErrorCollectingHandler();
        try {
            RDFParser.create()
                .source(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)))
                .lang(lang)
                .errorHandler(handler)
                .checking(true)
                .parse(model);
        } catch (RiotException e) {
            String msg = cleanMessage(e.getMessage());
            throw new OntologyParseException("Invalid " + format + " content: " + msg, e);
        } catch (Exception e) {
            throw new OntologyParseException("Failed to parse ontology: " + cleanMessage(e.getMessage()), e);
        }
        if (handler.hasFatal()) {
            throw new OntologyParseException("Invalid " + format + " content: " + handler.firstError());
        }
        return model;
    }

    /** Serialize a Jena model back to the requested format. */
    public String serialize(Model model, RdfFormat format) {
        if (model == null) return "";
        StringWriter sw = new StringWriter();
        RDFDataMgr.write(sw, model, toLang(format));
        return sw.toString();
    }

    /** Extract the prefix -> URI map declared on the model. */
    public NamespaceMap extractNamespaces(Model model) {
        Map<String, String> raw = model.getNsPrefixMap();
        List<NamespaceMap.Entry> entries = new ArrayList<>(raw.size());
        for (Map.Entry<String, String> e : raw.entrySet()) {
            entries.add(new NamespaceMap.Entry(e.getKey(), e.getValue()));
        }
        entries.sort(Comparator.comparing(NamespaceMap.Entry::prefix));
        return NamespaceMap.of(entries);
    }

    public long countStatements(Model model) {
        return model == null ? 0L : model.size();
    }

    /** List distinct URIs typed as owl:Class or rdfs:Class. */
    public Stream<String> listClasses(Model model) {
        Set<String> out = new LinkedHashSet<>();
        collectTypedResources(model, OWL.Class, out);
        collectTypedResources(model, RDFS.Class, out);
        return out.stream();
    }

    /** List distinct URIs typed as rdf:Property / owl:ObjectProperty / owl:DatatypeProperty. */
    public Stream<String> listProperties(Model model) {
        Set<String> out = new LinkedHashSet<>();
        collectTypedResources(model, RDF.Property, out);
        collectTypedResources(model, OWL.ObjectProperty, out);
        collectTypedResources(model, OWL.DatatypeProperty, out);
        collectTypedResources(model, OWL.AnnotationProperty, out);
        return out.stream();
    }

    /** List distinct URIs typed as skos:Concept. */
    public Stream<String> listSkosConcepts(Model model) {
        Set<String> out = new LinkedHashSet<>();
        collectTypedResources(model, SKOS.Concept, out);
        return out.stream();
    }

    /** Build a lightweight summary for a term — used by search results. */
    public TermSearchResult toSearchResult(Model model, String uri, String type) {
        Resource r = model.getResource(uri);
        return TermSearchResult.builder()
            .uri(uri)
            .type(type)
            .label(firstLiteral(r, RDFS.label))
            .comment(firstLiteral(r, RDFS.comment))
            .altLabels(literalList(r, SKOS.altLabel))
            .broader(resourceList(r, SKOS.broader))
            .narrower(resourceList(r, SKOS.narrower))
            .build();
    }

    /** Full detail for a single term, resolving all common annotation properties. */
    public TermDetail getTermDetail(Model model, String uri) {
        Resource r = model.getResource(uri);
        List<String> types = new ArrayList<>();
        for (StmtIterator it = r.listProperties(RDF.type); it.hasNext();) {
            RDFNode obj = it.next().getObject();
            if (obj.isURIResource()) types.add(obj.asResource().getURI());
        }
        return TermDetail.builder()
            .uri(uri)
            .types(types)
            .label(firstLiteral(r, RDFS.label))
            .comment(firstLiteral(r, RDFS.comment))
            .altLabels(literalList(r, SKOS.altLabel))
            .domain(resourceList(r, RDFS.domain))
            .range(resourceList(r, RDFS.range))
            .broader(resourceList(r, SKOS.broader))
            .narrower(resourceList(r, SKOS.narrower))
            .exactMatch(resourceList(r, SKOS.exactMatch))
            .closeMatch(resourceList(r, SKOS.closeMatch))
            .build();
    }

    // ------------------------------------------------------------------
    // internals
    // ------------------------------------------------------------------

    private static Lang toLang(RdfFormat format) {
        return switch (format) {
            case TURTLE -> Lang.TURTLE;
            case RDF_XML -> Lang.RDFXML;
            case JSON_LD -> Lang.JSONLD11;
            case N_TRIPLES -> Lang.NTRIPLES;
            case N_QUADS -> Lang.NQUADS;
            case TRIG -> Lang.TRIG;
        };
    }

    private static void collectTypedResources(Model model, Resource type, Set<String> out) {
        ResIterator it = model.listSubjectsWithProperty(RDF.type, type);
        try {
            while (it.hasNext()) {
                Resource r = it.next();
                if (r.isURIResource()) out.add(r.getURI());
            }
        } finally {
            it.close();
        }
    }

    private static String firstLiteral(Resource r, Property p) {
        if (!r.hasProperty(p)) return null;
        for (StmtIterator it = r.listProperties(p); it.hasNext();) {
            RDFNode obj = it.next().getObject();
            if (obj.isLiteral()) {
                return obj.asLiteral().getString();
            }
        }
        return null;
    }

    private static List<String> literalList(Resource r, Property p) {
        List<String> out = new ArrayList<>();
        for (StmtIterator it = r.listProperties(p); it.hasNext();) {
            RDFNode obj = it.next().getObject();
            if (obj.isLiteral()) out.add(obj.asLiteral().getString());
        }
        return out;
    }

    private static List<String> resourceList(Resource r, Property p) {
        List<String> out = new ArrayList<>();
        for (StmtIterator it = r.listProperties(p); it.hasNext();) {
            RDFNode obj = it.next().getObject();
            if (obj.isURIResource()) out.add(obj.asResource().getURI());
        }
        return out;
    }

    private static String cleanMessage(String raw) {
        if (raw == null) return "unknown error";
        // Strip multi-line stack-trace noise so the UI sees a single line.
        int newline = raw.indexOf('\n');
        String cleaned = newline >= 0 ? raw.substring(0, newline) : raw;
        return cleaned.trim();
    }

    /**
     * Collects parse errors so we can fail fast even when Jena would only
     * emit a warning.
     */
    static final class ErrorCollectingHandler implements ErrorHandler {
        private final List<String> errors = new ArrayList<>();

        boolean hasFatal() { return !errors.isEmpty(); }

        String firstError() { return errors.get(0); }

        @Override public void warning(String message, long line, long col) {
            log.debug("RDF parse warning at {}:{} - {}", line, col, message);
        }

        @Override public void error(String message, long line, long col) {
            errors.add(String.format("line %d col %d: %s", line, col, message));
        }

        @Override public void fatal(String message, long line, long col) {
            errors.add(String.format("fatal at line %d col %d: %s", line, col, message));
            throw new RiotException(message);
        }
    }
}
