package io.rdfforge.pipeline.dto;

import java.util.List;
import java.util.Map;

/**
 * Explain response. Each entry pairs a generated triple with the trace
 * (rule id, source columns that were consumed, URI template that was applied,
 * ordered list of transforms, datatype). The UI uses this payload to highlight
 * the cells in the source panel and the rule row in the mapping panel.
 */
public record ExplainResponse(
    List<RowExplain> rows
) {

    public record RowExplain(
        int rowIndex,
        Map<String, Object> row,
        List<TripleExplain> triples
    ) {}

    public record TripleExplain(
        TripleDto triple,
        ExplainTrace trace
    ) {}

    public record ExplainTrace(
        String ruleId,
        String ruleType,
        String source,
        String target,
        String uriTemplateUsed,
        Object sourceValue,
        List<TransformStep> transforms,
        String finalValue
    ) {}

    public record TransformStep(
        String type,
        String inputValue,
        String outputValue,
        Map<String, Object> params
    ) {}
}
