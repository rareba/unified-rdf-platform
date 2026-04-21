package io.rdfforge.engine.mapping;

import io.rdfforge.common.exception.MappingRuleException;
import org.apache.jena.graph.Triple;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("MappingExecutor")
class MappingExecutorTest {

    private MappingExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new MappingExecutor();
    }

    private static Map<String, Object> row(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put(kv[i].toString(), kv[i + 1]);
        return m;
    }

    @Test
    @DisplayName("COLUMN_TO_URI with target predicate emits a URI-valued triple")
    void columnToUri() {
        MappingRuleSpec subj = new MappingRuleSpec(
            "subj", MappingRuleSpec.RuleType.FIXED_URI,
            null, null, "${baseUri}person/${id}", null, null, null
        );
        MappingRuleSpec rel = new MappingRuleSpec(
            "rel", MappingRuleSpec.RuleType.COLUMN_TO_URI,
            "company", "http://ex.org/worksAt", "${baseUri}company/${company}", null, null, null
        );
        MappingSpec mapping = new MappingSpec("m1", "https://ex.org/", List.of(subj, rel));

        List<MappingExecutor.RowResult> out = executor.execute(
            mapping, List.of(row("id", "1", "company", "ACME")));

        assertEquals(1, out.size());
        assertEquals(1, out.get(0).triples().size());
        Triple t = out.get(0).triples().get(0);
        assertEquals("https://ex.org/person/1", t.getSubject().getURI());
        assertEquals("http://ex.org/worksAt", t.getPredicate().getURI());
        assertEquals("https://ex.org/company/ACME", t.getObject().getURI());
    }

    @Test
    @DisplayName("COLUMN_TO_LITERAL with xsd:dateTime datatype")
    void columnToLiteralDateTime() {
        MappingRuleSpec subj = new MappingRuleSpec(
            "s", MappingRuleSpec.RuleType.FIXED_URI, null, null,
            "${baseUri}r/${id}", null, null, null);
        MappingRuleSpec lit = new MappingRuleSpec(
            "lit", MappingRuleSpec.RuleType.COLUMN_TO_LITERAL,
            "ts", "http://ex.org/when", null,
            "xsd:dateTime", null, null);

        MappingSpec mapping = new MappingSpec("m1", "https://ex.org/", List.of(subj, lit));
        List<MappingExecutor.RowResult> out = executor.execute(
            mapping, List.of(row("id", "1", "ts", "2026-04-21T12:00:00Z")));

        Triple t = out.get(0).triples().get(0);
        assertTrue(t.getObject().isLiteral());
        assertEquals("2026-04-21T12:00:00Z", t.getObject().getLiteralLexicalForm());
        assertEquals("http://www.w3.org/2001/XMLSchema#dateTime",
            t.getObject().getLiteralDatatypeURI());
    }

    @Test
    @DisplayName("URI template with multiple substitutions")
    void multipleSubstitutions() {
        MappingRuleSpec subj = new MappingRuleSpec(
            "s", MappingRuleSpec.RuleType.FIXED_URI, null, null,
            "${baseUri}${type}/${id}", null, null, null);
        MappingRuleSpec lit = new MappingRuleSpec(
            "l", MappingRuleSpec.RuleType.COLUMN_TO_LITERAL,
            "name", "http://ex.org/name", null, null, null, null);

        MappingSpec mapping = new MappingSpec("m1", "https://ex.org/", List.of(subj, lit));
        List<MappingExecutor.RowResult> out = executor.execute(
            mapping, List.of(row("id", "7", "type", "obs", "name", "hi")));

        assertEquals("https://ex.org/obs/7", out.get(0).triples().get(0).getSubject().getURI());
    }

    @Test
    @DisplayName("UPPER transform uppercases literal value and records trace step")
    void transformUpper() {
        MappingRuleSpec subj = new MappingRuleSpec(
            "s", MappingRuleSpec.RuleType.FIXED_URI, null, null,
            "${baseUri}r/${id}", null, null, null);
        MappingRuleSpec lit = new MappingRuleSpec(
            "lit", MappingRuleSpec.RuleType.COLUMN_TO_LITERAL,
            "name", "http://ex.org/name", null, null, null,
            Map.of("type", "UPPER"));
        MappingSpec mapping = new MappingSpec("m1", "https://ex.org/", List.of(subj, lit));

        List<MappingExecutor.RowResult> out = executor.execute(
            mapping, List.of(row("id", "1", "name", "alice")));

        assertEquals("ALICE", out.get(0).triples().get(0).getObject().getLiteralLexicalForm());
        assertEquals("UPPER", out.get(0).traces().get(0).transforms().get(0).type());
    }

    @Test
    @DisplayName("TRIM transform strips surrounding whitespace")
    void transformTrim() {
        MappingRuleSpec subj = new MappingRuleSpec(
            "s", MappingRuleSpec.RuleType.FIXED_URI, null, null,
            "${baseUri}r/${id}", null, null, null);
        MappingRuleSpec lit = new MappingRuleSpec(
            "lit", MappingRuleSpec.RuleType.COLUMN_TO_LITERAL,
            "name", "http://ex.org/name", null, null, null,
            Map.of("type", "TRIM"));
        MappingSpec mapping = new MappingSpec("m1", "https://ex.org/", List.of(subj, lit));

        List<MappingExecutor.RowResult> out = executor.execute(
            mapping, List.of(row("id", "1", "name", "  alice  ")));

        assertEquals("alice", out.get(0).triples().get(0).getObject().getLiteralLexicalForm());
    }

    @Test
    @DisplayName("REGEX_REPLACE transform replaces matched groups")
    void transformRegexReplace() {
        MappingRuleSpec subj = new MappingRuleSpec(
            "s", MappingRuleSpec.RuleType.FIXED_URI, null, null,
            "${baseUri}r/${id}", null, null, null);
        MappingRuleSpec lit = new MappingRuleSpec(
            "lit", MappingRuleSpec.RuleType.COLUMN_TO_LITERAL,
            "phone", "http://ex.org/phone", null, null, null,
            Map.of("type", "REGEX_REPLACE", "params",
                Map.of("pattern", "[^0-9]", "replacement", ""))
        );
        MappingSpec mapping = new MappingSpec("m1", "https://ex.org/", List.of(subj, lit));

        List<MappingExecutor.RowResult> out = executor.execute(
            mapping, List.of(row("id", "1", "phone", "+1 (555) 123-4567")));

        assertEquals("15551234567", out.get(0).triples().get(0).getObject().getLiteralLexicalForm());
    }

    @Test
    @DisplayName("null source value for COLUMN_TO_LITERAL raises MappingRuleException")
    void nullSourceValue() {
        MappingRuleSpec subj = new MappingRuleSpec(
            "s", MappingRuleSpec.RuleType.FIXED_URI, null, null,
            "${baseUri}r/${id}", null, null, null);
        MappingRuleSpec lit = new MappingRuleSpec(
            "lit", MappingRuleSpec.RuleType.COLUMN_TO_LITERAL,
            "missing", "http://ex.org/x", null, null, null, null);
        MappingSpec mapping = new MappingSpec("m1", "https://ex.org/", List.of(subj, lit));

        MappingRuleException ex = assertThrows(MappingRuleException.class, () ->
            executor.execute(mapping, List.of(row("id", "1"))));
        assertEquals("lit", ex.getRuleId());
    }

    @Test
    @DisplayName("lenient mode captures errors per-row without aborting the batch")
    void lenientMode() {
        MappingRuleSpec subj = new MappingRuleSpec(
            "s", MappingRuleSpec.RuleType.FIXED_URI, null, null,
            "${baseUri}r/${id}", null, null, null);
        MappingRuleSpec lit = new MappingRuleSpec(
            "lit", MappingRuleSpec.RuleType.COLUMN_TO_LITERAL,
            "name", "http://ex.org/x", null, null, null, null);
        MappingSpec mapping = new MappingSpec("m1", "https://ex.org/", List.of(subj, lit));

        List<MappingExecutor.RowResult> out = executor.executeLenient(
            mapping, List.of(row("id", "1", "name", "alice"), row("id", "2")));

        assertNull(out.get(0).error());
        assertNotNull(out.get(1).error());
        assertEquals(1, out.get(0).triples().size());
        assertEquals(0, out.get(1).triples().size());
    }

    @Test
    @DisplayName("Explain trace reports rule id, source, uriTemplate, transforms")
    void explainTraceStructure() {
        MappingRuleSpec subj = new MappingRuleSpec(
            "s", MappingRuleSpec.RuleType.FIXED_URI, null, null,
            "${baseUri}r/${id}", null, null, null);
        MappingRuleSpec lit = new MappingRuleSpec(
            "lit", MappingRuleSpec.RuleType.COLUMN_TO_LITERAL,
            "name", "http://ex.org/name", null, null, null,
            Map.of("type", "UPPER"));
        MappingSpec mapping = new MappingSpec("m1", "https://ex.org/", List.of(subj, lit));

        List<MappingExecutor.RowResult> out = executor.execute(
            mapping, List.of(row("id", "1", "name", "alice")));

        MappingExecutor.TripleTrace trace = out.get(0).traces().get(0);
        assertEquals("lit", trace.ruleId());
        assertEquals("name", trace.source());
        assertEquals("http://ex.org/name", trace.target());
        assertEquals("ALICE", trace.finalValue());
        assertEquals(1, trace.transforms().size());
        assertEquals("UPPER", trace.transforms().get(0).type());
    }
}
