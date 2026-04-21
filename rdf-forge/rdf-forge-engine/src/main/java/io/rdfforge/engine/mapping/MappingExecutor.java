package io.rdfforge.engine.mapping;

import io.rdfforge.common.exception.MappingRuleException;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.graph.Triple;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Core engine that materializes {@link Triple}s (and optional explain
 * traces) from a {@link MappingSpec} and a list of source rows.
 *
 * <p>Design:
 * <ul>
 *   <li><b>Subject URI minting</b> — a rule with {@code type=FIXED_URI} or
 *       {@code type=COLUMN_TO_URI} establishes the subject for its row via
 *       the rule's {@code uriTemplate} (preferred) or its {@code source}
 *       column value. In the absence of both, the executor mints
 *       {@code baseUri + "row/" + rowIndex}.</li>
 *   <li><b>Triple attribution</b> — every emitted triple carries a trace
 *       identifying the rule, the source column, the URI template used,
 *       the transforms applied, and the final value. The UI uses this to
 *       light up the relevant cell in the source panel and the rule in the
 *       mapping panel.</li>
 *   <li><b>Row isolation</b> — a failure on one row does not abort the
 *       batch; in {@link #executeLenient} mode the error is captured in
 *       place of triples for that row and the rest continue. The strict
 *       {@link #execute} mode propagates the first failure.</li>
 * </ul>
 *
 * <p>The executor is a Spring {@code @Component} so pipeline-service can
 * inject it directly.
 */
@Slf4j
@Component
public class MappingExecutor {

    /** Emission mode used by preview/explain; strict mode throws on errors. */
    public enum Mode { STRICT, LENIENT }

    /** Result of one row's execution — includes both triples and traces. */
    public record RowResult(
        int rowIndex,
        Map<String, Object> row,
        List<Triple> triples,
        List<TripleTrace> traces,
        String error
    ) {}

    /**
     * Structured trace of one generated triple — what rule produced it, what
     * source column fed it, and the transform pipeline that was applied.
     */
    public record TripleTrace(
        Triple triple,
        String ruleId,
        String ruleType,
        String source,
        String target,
        String uriTemplateUsed,
        Object sourceValue,
        List<TransformEngine.Step> transforms,
        String finalValue
    ) {}

    /** Strict execution — first {@link MappingRuleException} propagates. */
    public List<RowResult> execute(MappingSpec mapping, List<Map<String, Object>> rows) {
        return run(mapping, rows, Mode.STRICT);
    }

    /** Lenient execution — per-row errors captured, batch keeps going. */
    public List<RowResult> executeLenient(MappingSpec mapping, List<Map<String, Object>> rows) {
        return run(mapping, rows, Mode.LENIENT);
    }

    // ────────────────────────── internals ──────────────────────────

    private List<RowResult> run(MappingSpec mapping, List<Map<String, Object>> rows, Mode mode) {
        if (mapping == null) throw new IllegalArgumentException("mapping must not be null");
        if (rows == null) return List.of();

        List<RowResult> results = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> row = rows.get(i);
            try {
                RowResult rr = executeRow(mapping, row, i);
                results.add(rr);
            } catch (MappingRuleException ex) {
                if (mode == Mode.STRICT) throw ex;
                log.debug("Row {} failed under LENIENT mode: {}", i, ex.getMessage());
                results.add(new RowResult(i, row, List.of(), List.of(), ex.getMessage()));
            }
        }
        return results;
    }

    private RowResult executeRow(MappingSpec mapping, Map<String, Object> row, int rowIndex) {
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("baseUri", mapping.baseUri() == null ? "" : mapping.baseUri());
        extra.put("rowIndex", rowIndex);
        Map<String, Object> ctx = UriTemplateEngine.buildContext(row, extra);

        String defaultSubject = defaultSubjectUri(mapping, row, rowIndex);
        String currentSubject = defaultSubject;

        List<Triple> triples = new ArrayList<>();
        List<TripleTrace> traces = new ArrayList<>();

        for (MappingRuleSpec rule : safe(mapping.rules())) {
            if (rule.type() == null) {
                throw new MappingRuleException(rule.id(), "Rule type is required");
            }

            switch (rule.type()) {
                case FIXED_URI -> {
                    currentSubject = renderSubject(rule, ctx);
                    // FIXED_URI establishes the subject but emits no triples on its own.
                }
                case COLUMN_TO_URI -> {
                    Object sourceValue = row == null ? null : row.get(rule.source());
                    // Subject mint; object is the subject iff there's no target, else target is predicate and object=uri.
                    TransformEngine.Result tr = TransformEngine.apply(rule.id(), sourceValue, rule.transform());
                    Map<String, Object> subCtx = new LinkedHashMap<>(ctx);
                    if (tr.value() != null) subCtx.put(rule.source(), tr.value());
                    String uri = renderSubject(rule, subCtx);

                    if (rule.target() == null || rule.target().isBlank()) {
                        currentSubject = uri;
                    } else {
                        // Emit subject --target--> <uri> and update current subject if rule also shifts it.
                        Triple t = Triple.create(
                            NodeFactory.createURI(currentSubject),
                            NodeFactory.createURI(rule.target()),
                            NodeFactory.createURI(uri)
                        );
                        triples.add(t);
                        traces.add(new TripleTrace(
                            t, rule.id(), rule.type().name(),
                            rule.source(), rule.target(), rule.uriTemplate(),
                            sourceValue, tr.steps(), uri
                        ));
                    }
                }
                case COLUMN_TO_LITERAL -> {
                    Object sourceValue = row == null ? null : row.get(rule.source());
                    if (sourceValue == null) {
                        throw new MappingRuleException(
                            rule.id(),
                            "Source column '" + rule.source() + "' resolves to null in rule " + rule.id()
                        );
                    }
                    TransformEngine.Result tr = TransformEngine.apply(rule.id(), sourceValue, rule.transform());
                    Triple t = buildLiteralTriple(currentSubject, rule, tr.value());
                    triples.add(t);
                    traces.add(new TripleTrace(
                        t, rule.id(), rule.type().name(),
                        rule.source(), rule.target(), rule.uriTemplate(),
                        sourceValue, tr.steps(), tr.value()
                    ));
                }
                case CONSTANT -> {
                    // Constant literal under rule.target predicate. Value taken from rule.source.
                    TransformEngine.Result tr = TransformEngine.apply(rule.id(), rule.source(), rule.transform());
                    if (tr.value() == null) {
                        throw new MappingRuleException(rule.id(), "CONSTANT rule " + rule.id() + " has null value");
                    }
                    Triple t = buildLiteralTriple(currentSubject, rule, tr.value());
                    triples.add(t);
                    traces.add(new TripleTrace(
                        t, rule.id(), rule.type().name(),
                        rule.source(), rule.target(), rule.uriTemplate(),
                        rule.source(), tr.steps(), tr.value()
                    ));
                }
                case NESTED -> {
                    // NESTED rules link the current subject to a new URI via the target predicate
                    // and then re-scope currentSubject to the new URI for subsequent rules.
                    String nestedUri = renderSubject(rule, ctx);
                    if (rule.target() != null && !rule.target().isBlank()) {
                        Triple t = Triple.create(
                            NodeFactory.createURI(currentSubject),
                            NodeFactory.createURI(rule.target()),
                            NodeFactory.createURI(nestedUri)
                        );
                        triples.add(t);
                        traces.add(new TripleTrace(
                            t, rule.id(), rule.type().name(),
                            rule.source(), rule.target(), rule.uriTemplate(),
                            null, List.of(), nestedUri
                        ));
                    }
                    currentSubject = nestedUri;
                }
            }
        }

        return new RowResult(rowIndex, row, triples, traces, null);
    }

    private Triple buildLiteralTriple(String subject, MappingRuleSpec rule, String value) {
        if (rule.target() == null || rule.target().isBlank()) {
            throw new MappingRuleException(rule.id(), "Rule " + rule.id() + " requires a target predicate");
        }
        if (value == null) {
            throw new MappingRuleException(rule.id(), "Rule " + rule.id() + " resolved to null literal");
        }
        return Triple.create(
            NodeFactory.createURI(subject),
            NodeFactory.createURI(rule.target()),
            buildLiteralObject(rule, value)
        );
    }

    private org.apache.jena.graph.Node buildLiteralObject(MappingRuleSpec rule, String value) {
        if (rule.datatype() != null && !rule.datatype().isBlank()) {
            String dt = expandPrefix(rule.datatype());
            return NodeFactory.createLiteralDT(value, org.apache.jena.datatypes.TypeMapper.getInstance().getSafeTypeByName(dt));
        }
        if (rule.language() != null && !rule.language().isBlank()) {
            return NodeFactory.createLiteralLang(value, rule.language());
        }
        return NodeFactory.createLiteralString(value);
    }

    /**
     * Expand common xsd:/rdf: prefixes to full URIs so rule authors can write
     * {@code "xsd:dateTime"} without knowing the namespace. Anything already
     * a full URI passes through unchanged.
     */
    private static String expandPrefix(String dt) {
        if (dt.startsWith("http://") || dt.startsWith("https://")) return dt;
        if (dt.startsWith("xsd:")) return "http://www.w3.org/2001/XMLSchema#" + dt.substring(4);
        if (dt.startsWith("rdf:")) return "http://www.w3.org/1999/02/22-rdf-syntax-ns#" + dt.substring(4);
        if (dt.startsWith("rdfs:")) return "http://www.w3.org/2000/01/rdf-schema#" + dt.substring(5);
        return dt;
    }

    private String defaultSubjectUri(MappingSpec mapping, Map<String, Object> row, int rowIndex) {
        String base = mapping.baseUri() == null ? "" : mapping.baseUri();
        if (!base.isEmpty() && !base.endsWith("/")) base = base + "/";
        return base + "row/" + rowIndex;
    }

    /**
     * Resolve the subject URI for a rule: prefer {@code uriTemplate}, fall
     * back to {@code baseUri + sanitize(source column value)}, else raise.
     */
    private String renderSubject(MappingRuleSpec rule, Map<String, Object> ctx) {
        if (rule.uriTemplate() != null && !rule.uriTemplate().isBlank()) {
            return UriTemplateEngine.render(rule.uriTemplate(), ctx, rule.id());
        }
        if (rule.source() != null && !rule.source().isBlank()) {
            Object v = ctx.get(rule.source());
            if (v == null || v.toString().isBlank()) {
                throw new MappingRuleException(
                    rule.id(),
                    "Rule " + rule.id() + " has no uriTemplate and source column '"
                        + rule.source() + "' resolves to null/empty"
                );
            }
            Object baseUri = ctx.get("baseUri");
            String base = baseUri == null ? "" : baseUri.toString();
            if (!base.isEmpty() && !base.endsWith("/")) base = base + "/";
            return base + sanitize(v.toString());
        }
        throw new MappingRuleException(rule.id(), "Rule " + rule.id() + " has neither uriTemplate nor source");
    }

    private static String sanitize(String v) {
        return v.replaceAll("[^a-zA-Z0-9._\\-]", "_");
    }

    private static <T> List<T> safe(List<T> in) {
        return in == null ? List.of() : in;
    }
}
