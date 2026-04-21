package io.rdfforge.pipeline.dto;

import java.util.List;

/**
 * Validation outcome. {@code valid=true} when the rule set is internally
 * consistent: unique rule ids, syntactically-valid URI templates, no dangling
 * required fields. When available columns are passed in the request, the
 * service additionally checks that column-based rules reference real columns.
 */
public record MappingValidationResponse(
    boolean valid,
    List<ValidationIssue> issues
) {

    public record ValidationIssue(
        String ruleId,
        String field,
        String code,
        String message
    ) {}
}
