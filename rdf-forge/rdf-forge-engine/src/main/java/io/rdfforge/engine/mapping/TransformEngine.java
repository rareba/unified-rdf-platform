package io.rdfforge.engine.mapping;

import io.rdfforge.common.exception.MappingRuleException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Applies a single {@code transform} map to a value, returning both the
 * transformed string and a structured list of steps for the Explain trace.
 *
 * <p>Supported transform {@code type} values:
 * <ul>
 *   <li>{@code UPPER} — uppercase</li>
 *   <li>{@code LOWER} — lowercase</li>
 *   <li>{@code TRIM} — strip leading/trailing whitespace</li>
 *   <li>{@code SUBSTRING} — params {@code start}, optional {@code end}</li>
 *   <li>{@code REGEX_REPLACE} — params {@code pattern}, {@code replacement}</li>
 * </ul>
 *
 * <p>Unknown {@code type} values raise a {@link MappingRuleException} tagged
 * with the caller-supplied {@code ruleId} so the UI can flag the offending
 * rule.
 */
public final class TransformEngine {

    private TransformEngine() {}

    /** Output of a transform application — final value + ordered step trace. */
    public record Result(String value, List<Step> steps) {}

    public record Step(String type, String input, String output, Map<String, Object> params) {}

    public static Result apply(String ruleId, Object input, Map<String, Object> transform) {
        String current = input == null ? null : input.toString();
        List<Step> trace = new ArrayList<>();
        if (transform == null || transform.isEmpty()) {
            return new Result(current, trace);
        }

        Object typeObj = transform.get("type");
        if (typeObj == null) {
            return new Result(current, trace);
        }
        String type = typeObj.toString().toUpperCase();

        @SuppressWarnings("unchecked")
        Map<String, Object> params = transform.get("params") instanceof Map
            ? (Map<String, Object>) transform.get("params")
            : Map.of();

        String before = current;
        String after = switch (type) {
            case "UPPER" -> current == null ? null : current.toUpperCase();
            case "LOWER" -> current == null ? null : current.toLowerCase();
            case "TRIM" -> current == null ? null : current.trim();
            case "SUBSTRING" -> applySubstring(ruleId, current, params);
            case "REGEX_REPLACE" -> applyRegexReplace(ruleId, current, params);
            default -> throw new MappingRuleException(ruleId, "Unknown transform type: " + type);
        };
        trace.add(new Step(type, before, after, params));
        return new Result(after, trace);
    }

    private static String applySubstring(String ruleId, String input, Map<String, Object> params) {
        if (input == null) return null;
        int start = toInt(params.get("start"), 0);
        Object endObj = params.get("end");
        try {
            if (endObj == null) {
                return start >= input.length() ? "" : input.substring(start);
            }
            int end = toInt(endObj, input.length());
            end = Math.min(end, input.length());
            if (start > end) return "";
            return input.substring(Math.max(0, start), end);
        } catch (IndexOutOfBoundsException e) {
            throw new MappingRuleException(ruleId, "SUBSTRING out of bounds: " + e.getMessage());
        }
    }

    private static String applyRegexReplace(String ruleId, String input, Map<String, Object> params) {
        if (input == null) return null;
        Object pattern = params.get("pattern");
        Object replacement = params.getOrDefault("replacement", "");
        if (pattern == null) {
            throw new MappingRuleException(ruleId, "REGEX_REPLACE requires 'pattern'");
        }
        try {
            return input.replaceAll(pattern.toString(), replacement.toString());
        } catch (Exception e) {
            throw new MappingRuleException(ruleId, "REGEX_REPLACE failed: " + e.getMessage(), e);
        }
    }

    private static int toInt(Object value, int fallback) {
        if (value instanceof Number n) return n.intValue();
        if (value == null) return fallback;
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
