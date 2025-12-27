package io.rdfforge.engine.csvw;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for URI Template operations.
 * Provides endpoints for validating, parsing, and previewing URI templates.
 */
@RestController
@RequestMapping("/api/v1/uri-templates")
@RequiredArgsConstructor
@Tag(name = "URI Templates", description = "CSVW URI Template operations")
@CrossOrigin(origins = "*")
public class UriTemplateController {
    
    private final UriTemplateService uriTemplateService;
    
    @PostMapping("/validate")
    @Operation(summary = "Validate URI template syntax")
    public ResponseEntity<UriTemplateService.TemplateValidation> validate(
            @RequestBody TemplateRequest request) {
        UriTemplateService.TemplateValidation result = 
            uriTemplateService.validateTemplate(request.getTemplate());
        return ResponseEntity.ok(result);
    }
    
    @PostMapping("/parse")
    @Operation(summary = "Parse URI template to extract variables")
    public ResponseEntity<Map<String, UriTemplateService.TemplateVariable>> parse(
            @RequestBody TemplateRequest request) {
        Map<String, UriTemplateService.TemplateVariable> variables = 
            uriTemplateService.parseTemplate(request.getTemplate());
        return ResponseEntity.ok(variables);
    }
    
    @PostMapping("/expand")
    @Operation(summary = "Expand URI template with sample values")
    public ResponseEntity<ExpandResult> expand(@RequestBody ExpandRequest request) {
        String expanded = uriTemplateService.expandTemplate(
            request.getTemplate(), 
            request.getValues()
        );
        return ResponseEntity.ok(new ExpandResult(expanded, request.getTemplate()));
    }
    
    @PostMapping("/build-default")
    @Operation(summary = "Build a default URI template from base URI and key columns")
    public ResponseEntity<TemplateResult> buildDefault(@RequestBody BuildDefaultRequest request) {
        String template = uriTemplateService.buildDefaultTemplate(
            request.getBaseUri(),
            request.getKeyColumns() != null ? request.getKeyColumns() : new String[0]
        );
        return ResponseEntity.ok(new TemplateResult(template));
    }
    
    @Data
    public static class TemplateRequest {
        private String template;
    }
    
    @Data
    public static class ExpandRequest {
        private String template;
        private Map<String, Object> values;
    }
    
    @Data
    public static class BuildDefaultRequest {
        private String baseUri;
        private String[] keyColumns;
    }
    
    @Data
    public static class ExpandResult {
        private final String expanded;
        private final String template;
    }
    
    @Data
    public static class TemplateResult {
        private final String template;
    }
}
