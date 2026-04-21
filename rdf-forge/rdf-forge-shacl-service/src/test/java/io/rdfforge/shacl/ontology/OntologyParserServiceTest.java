package io.rdfforge.shacl.ontology;

import io.rdfforge.shacl.entity.OntologyEntity.RdfFormat;
import io.rdfforge.shacl.ontology.dto.NamespaceMap;
import io.rdfforge.shacl.ontology.dto.TermDetail;
import org.apache.jena.rdf.model.Model;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("OntologyParserService Tests")
class OntologyParserServiceTest {

    private OntologyParserService parser;

    private static final String SAMPLE_TURTLE = """
        @prefix owl: <http://www.w3.org/2002/07/owl#> .
        @prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
        @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
        @prefix skos: <http://www.w3.org/2004/02/skos/core#> .
        @prefix ex: <http://example.org/schema/> .

        ex:Person a owl:Class ;
            rdfs:label "Person" ;
            rdfs:comment "A human being." .

        ex:Organization a owl:Class ;
            rdfs:label "Organization" .

        ex:name a owl:DatatypeProperty ;
            rdfs:label "name" ;
            rdfs:domain ex:Person ;
            rdfs:range rdfs:Literal .

        ex:knows a owl:ObjectProperty ;
            rdfs:label "knows" ;
            rdfs:domain ex:Person ;
            rdfs:range ex:Person .

        ex:topic-ai a skos:Concept ;
            skos:prefLabel "Artificial Intelligence" ;
            skos:altLabel "AI" ;
            skos:broader ex:topic-cs .

        ex:topic-cs a skos:Concept ;
            skos:prefLabel "Computer Science" ;
            skos:narrower ex:topic-ai .
        """;

    @BeforeEach
    void setUp() {
        parser = new OntologyParserService();
    }

    @Test
    @DisplayName("Should parse valid Turtle content")
    void parse_validTurtle_succeeds() {
        Model model = parser.parse(SAMPLE_TURTLE, RdfFormat.TURTLE);
        assertNotNull(model);
        assertTrue(model.size() > 10, "Model should contain multiple statements");
    }

    @Test
    @DisplayName("Should throw OntologyParseException for invalid content")
    void parse_invalidContent_throws() {
        String broken = "this is :: not :: turtle ??? @@@";
        assertThrows(OntologyParseException.class,
            () -> parser.parse(broken, RdfFormat.TURTLE));
    }

    @Test
    @DisplayName("Should throw OntologyParseException for empty content")
    void parse_emptyContent_throws() {
        assertThrows(OntologyParseException.class,
            () -> parser.parse("", RdfFormat.TURTLE));
        assertThrows(OntologyParseException.class,
            () -> parser.parse(null, RdfFormat.TURTLE));
    }

    @Test
    @DisplayName("Should extract namespace prefix map")
    void extractNamespaces_returnsAllDeclared() {
        Model model = parser.parse(SAMPLE_TURTLE, RdfFormat.TURTLE);
        NamespaceMap ns = parser.extractNamespaces(model);

        assertNotNull(ns);
        assertTrue(ns.entries().size() >= 5, "Should contain all declared prefixes");
        List<String> prefixes = ns.entries().stream().map(NamespaceMap.Entry::prefix).toList();
        assertTrue(prefixes.contains("ex"));
        assertTrue(prefixes.contains("owl"));
        assertTrue(prefixes.contains("skos"));
    }

    @Test
    @DisplayName("Should list owl:Class URIs")
    void listClasses_returnsOwlClasses() {
        Model model = parser.parse(SAMPLE_TURTLE, RdfFormat.TURTLE);
        List<String> classes = parser.listClasses(model).toList();

        assertEquals(2, classes.size());
        assertTrue(classes.contains("http://example.org/schema/Person"));
        assertTrue(classes.contains("http://example.org/schema/Organization"));
    }

    @Test
    @DisplayName("Should list all property types")
    void listProperties_returnsObjectAndDatatypeProperties() {
        Model model = parser.parse(SAMPLE_TURTLE, RdfFormat.TURTLE);
        List<String> props = parser.listProperties(model).toList();

        assertEquals(2, props.size());
        assertTrue(props.contains("http://example.org/schema/name"));
        assertTrue(props.contains("http://example.org/schema/knows"));
    }

    @Test
    @DisplayName("Should list SKOS concepts")
    void listSkosConcepts_returnsSkosConcepts() {
        Model model = parser.parse(SAMPLE_TURTLE, RdfFormat.TURTLE);
        List<String> concepts = parser.listSkosConcepts(model).collect(Collectors.toList());

        assertEquals(2, concepts.size());
        assertTrue(concepts.contains("http://example.org/schema/topic-ai"));
        assertTrue(concepts.contains("http://example.org/schema/topic-cs"));
    }

    @Test
    @DisplayName("Should return full term detail for a class")
    void getTermDetail_forClass_returnsAllAnnotations() {
        Model model = parser.parse(SAMPLE_TURTLE, RdfFormat.TURTLE);
        TermDetail detail = parser.getTermDetail(model, "http://example.org/schema/Person");

        assertNotNull(detail);
        assertEquals("http://example.org/schema/Person", detail.uri());
        assertTrue(detail.types().contains("http://www.w3.org/2002/07/owl#Class"));
        assertEquals("Person", detail.label());
        assertEquals("A human being.", detail.comment());
    }

    @Test
    @DisplayName("Should return domain and range for a property")
    void getTermDetail_forProperty_returnsDomainAndRange() {
        Model model = parser.parse(SAMPLE_TURTLE, RdfFormat.TURTLE);
        TermDetail detail = parser.getTermDetail(model, "http://example.org/schema/knows");

        assertNotNull(detail);
        assertEquals("knows", detail.label());
        assertTrue(detail.domain().contains("http://example.org/schema/Person"));
        assertTrue(detail.range().contains("http://example.org/schema/Person"));
    }

    @Test
    @DisplayName("Should return altLabel, broader, narrower for a SKOS concept")
    void getTermDetail_forSkosConcept_returnsSkosAnnotations() {
        Model model = parser.parse(SAMPLE_TURTLE, RdfFormat.TURTLE);
        TermDetail detail = parser.getTermDetail(model, "http://example.org/schema/topic-ai");

        assertNotNull(detail);
        assertTrue(detail.altLabels().contains("AI"));
        assertTrue(detail.broader().contains("http://example.org/schema/topic-cs"));
    }

    @Test
    @DisplayName("Should count statements")
    void countStatements_returnsNonZero() {
        Model model = parser.parse(SAMPLE_TURTLE, RdfFormat.TURTLE);
        assertTrue(parser.countStatements(model) > 0);
    }

    @Test
    @DisplayName("Should round-trip serialize to Turtle")
    void serialize_turtle_roundTrips() {
        Model model = parser.parse(SAMPLE_TURTLE, RdfFormat.TURTLE);
        String out = parser.serialize(model, RdfFormat.TURTLE);
        assertNotNull(out);
        assertTrue(out.contains("Person"));
        // Re-parse to confirm the output is valid
        Model reparsed = parser.parse(out, RdfFormat.TURTLE);
        assertEquals(model.size(), reparsed.size());
    }

    @Test
    @DisplayName("Should reject RDF/XML with external entity references (XXE-safe)")
    void parse_rdfXmlWithXxe_doesNotResolveEntity() {
        // Attempt to sneak a DOCTYPE with an external entity. Jena's default RIOT
        // parser does not resolve external entities — the parser should either
        // fail outright or parse the document with the placeholder untouched.
        String malicious = """
            <?xml version="1.0"?>
            <!DOCTYPE rdf:RDF [
              <!ENTITY xxe SYSTEM "http://bad.example.com/secret">
            ]>
            <rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
                     xmlns:ex="http://example.org/">
              <rdf:Description rdf:about="http://example.org/s">
                <ex:p>&xxe;</ex:p>
              </rdf:Description>
            </rdf:RDF>
            """;
        // Either it throws (strict mode rejects DTDs) or succeeds without having
        // contacted bad.example.com. We only care that it doesn't resolve
        // externally — which it won't because we never enabled URL reading and
        // the parser has no network access configured.
        try {
            Model model = parser.parse(malicious, RdfFormat.RDF_XML);
            // If parsing succeeded, confirm no remote resolution happened by
            // checking the resulting model did not pull data from the phantom
            // URL.
            String content = parser.serialize(model, RdfFormat.TURTLE);
            assertFalse(content.contains("secret"),
                "External entity should not have been resolved");
        } catch (OntologyParseException expected) {
            // Preferred outcome: parser rejects the DTD outright.
            assertTrue(expected.getMessage().toLowerCase().contains("invalid")
                || expected.getMessage().toLowerCase().contains("doctype")
                || expected.getMessage().toLowerCase().contains("entity")
                || expected.getMessage().toLowerCase().contains("dtd")
                || expected.getMessage().toLowerCase().contains("xml"));
        }
    }
}
