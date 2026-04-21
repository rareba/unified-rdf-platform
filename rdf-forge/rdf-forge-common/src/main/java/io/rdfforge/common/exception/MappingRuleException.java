package io.rdfforge.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Thrown when a {@code MappingRule} fails at apply-time — for example when a
 * URI template references an undefined variable, or a required source column
 * is null. The {@code ruleId} lets the UI highlight the offending rule inline
 * in the Mapping Studio preview panel.
 */
@Getter
public class MappingRuleException extends RdfForgeException {

    private final String ruleId;

    public MappingRuleException(String ruleId, String message) {
        super("MAPPING_RULE_ERROR", message, HttpStatus.UNPROCESSABLE_ENTITY);
        this.ruleId = ruleId;
    }

    public MappingRuleException(String ruleId, String message, Throwable cause) {
        super("MAPPING_RULE_ERROR", message, HttpStatus.UNPROCESSABLE_ENTITY, cause);
        this.ruleId = ruleId;
    }
}
