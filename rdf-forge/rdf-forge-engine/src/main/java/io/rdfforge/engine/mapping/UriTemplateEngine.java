package io.rdfforge.engine.mapping;

import io.rdfforge.common.exception.MappingRuleException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal {@code ${placeholder}} template engine used by the mapping executor
 * to mint subject URIs and literal templates. Intentionally narrow:
 *
 * <ul>
 *   <li>Supports {@code ${var}} and {@code ${dotted.path}} — the path is
 *       resolved against a flat context, so callers must pre-flatten nested
 *       structures when needed.</li>
 *   <li>Mints via simple substitution — no Velocity/JSP tax.</li>
 *   <li>Throws {@link MappingRuleException} with the rule id when a required
 *       placeholder resolves to null, so the UI can highlight the broken
 *       rule.</li>
 * </ul>
 *
 * <p>The regex {@code \$\{([^}]+)\}} is explicitly anchored on
 * {@code [^}]} to avoid consuming trailing braces; this matches the same
 * grammar that the existing cube operations use informally via
 * {@code replace("{value}", ...)}, but generalized.
 */
public final class UriTemplateEngine {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{([^}]+)\\}");

    private UriTemplateEngine() {}

    /**
     * Render {@code template} against {@code context}. Missing placeholders
     * raise {@link MappingRuleException} carrying the supplied {@code ruleId}.
     */
    public static String render(String template, Map<String, Object> context, String ruleId) {
        if (template == null) {
            throw new MappingRuleException(ruleId, "URI template is null");
        }
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String expr = matcher.group(1).trim();
            Object value = resolve(expr, context);
            if (value == null) {
                throw new MappingRuleException(
                    ruleId,
                    "Template placeholder '${" + expr + "}' resolves to null in rule " + ruleId
                );
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(value.toString()));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /**
     * Render a template, returning {@link java.util.Optional#empty()} when a
     * placeholder is missing instead of throwing. Useful for preview/explain
     * code paths that prefer to surface partial results.
     */
    public static String renderOrNull(String template, Map<String, Object> context) {
        if (template == null) return null;
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String expr = matcher.group(1).trim();
            Object value = resolve(expr, context);
            if (value == null) return null;
            matcher.appendReplacement(out, Matcher.quoteReplacement(value.toString()));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    /**
     * Flatten a row (plus implicit baseUri, etc.) into the lookup table the
     * placeholder grammar expects. Callers put the baseUri under "baseUri",
     * row columns under "row.<col>", and can also address columns as bare
     * names for ergonomic templates like "${id}".
     */
    public static Map<String, Object> buildContext(Map<String, Object> row, Map<String, Object> extra) {
        Map<String, Object> ctx = new LinkedHashMap<>();
        if (row != null) {
            for (Map.Entry<String, Object> e : row.entrySet()) {
                ctx.put(e.getKey(), e.getValue());
                ctx.put("row." + e.getKey(), e.getValue());
            }
        }
        if (extra != null) {
            ctx.putAll(extra);
        }
        return ctx;
    }

    private static Object resolve(String expr, Map<String, Object> context) {
        if (context == null) return null;
        if (context.containsKey(expr)) return context.get(expr);
        // Dotted path fallback: try to walk nested maps for "a.b.c".
        String[] parts = expr.split("\\.");
        Object current = context.get(parts[0]);
        for (int i = 1; i < parts.length && current instanceof Map<?, ?> m; i++) {
            current = m.get(parts[i]);
        }
        return current;
    }
}
