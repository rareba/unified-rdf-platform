package io.rdfforge.engine.csvw;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for building and expanding CSVW-style URI Templates.
 * 
 * Supports RFC 6570 Level 1 URI Templates with CSVW extensions:
 * - {column_name} - Simple variable substitution
 * - {+column_name} - Reserved expansion (no URL encoding)
 * - {#fragment} - Fragment expansion
 * - {/path} - Path segment expansion
 * 
 * Example templates:
 * - "http://example.org/dimension/{year}/{canton}"
 * - "http://example.org/observation/{+id}"
 * - "http://example.org/resource/{region}/{year}"
 */
@Service
@Slf4j
public class UriTemplateService {
    
    // Pattern to match template variables like {name}, {+name}, {#name}
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{([+#/]?)([^}]+)\\}");
    
    /**
     * Expand a URI template using values from a row.
     * 
     * @param template URI template with {column} placeholders
     * @param values Map of column names to values
     * @return Expanded URI
     */
    public String expandTemplate(String template, Map<String, Object> values) {
        if (template == null || template.isEmpty()) {
            return template;
        }
        
        StringBuffer result = new StringBuffer();
        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        
        while (matcher.find()) {
            String modifier = matcher.group(1);
            String variableName = matcher.group(2);
            Object value = values.get(variableName);
            
            String replacement;
            if (value == null) {
                // Keep the placeholder if value is missing
                replacement = "";
            } else {
                String stringValue = value.toString();
                
                if ("+".equals(modifier)) {
                    // Reserved expansion - no encoding
                    replacement = stringValue;
                } else if ("#".equals(modifier)) {
                    // Fragment expansion
                    replacement = "#" + encodeUri(stringValue);
                } else if ("/".equals(modifier)) {
                    // Path segment expansion
                    replacement = "/" + encodeUri(stringValue);
                } else {
                    // Simple expansion - URL encoded
                    replacement = encodeUri(stringValue);
                }
            }
            
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        
        return result.toString();
    }
    
    /**
     * Parse a URI template to extract variable names.
     * 
     * @param template URI template
     * @return Map of variable names to their modifiers
     */
    public Map<String, TemplateVariable> parseTemplate(String template) {
        Map<String, TemplateVariable> variables = new HashMap<>();
        
        if (template == null || template.isEmpty()) {
            return variables;
        }
        
        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        while (matcher.find()) {
            String modifier = matcher.group(1);
            String name = matcher.group(2);
            
            variables.put(name, TemplateVariable.builder()
                .name(name)
                .modifier(modifier.isEmpty() ? null : modifier)
                .required(true)
                .build());
        }
        
        return variables;
    }
    
    /**
     * Validate a URI template syntax.
     * 
     * @param template URI template to validate
     * @return Validation result
     */
    public TemplateValidation validateTemplate(String template) {
        try {
            Map<String, TemplateVariable> variables = parseTemplate(template);
            
            // Check for basic URI structure
            if (!template.startsWith("http://") && !template.startsWith("https://") 
                && !template.contains("://") && !template.startsWith("{")) {
                return TemplateValidation.builder()
                    .valid(false)
                    .message("Template should start with a URI scheme (http://, https://)")
                    .build();
            }
            
            return TemplateValidation.builder()
                .valid(true)
                .variables(variables)
                .message("Valid template with " + variables.size() + " variables")
                .build();
                
        } catch (Exception e) {
            return TemplateValidation.builder()
                .valid(false)
                .message("Invalid template: " + e.getMessage())
                .build();
        }
    }
    
    /**
     * Build a default URI template from a base URI and column names.
     * 
     * @param baseUri Base URI
     * @param keyColumns Column names to include in the template
     * @return Generated template
     */
    public String buildDefaultTemplate(String baseUri, String... keyColumns) {
        StringBuilder template = new StringBuilder(baseUri);
        if (!baseUri.endsWith("/")) {
            template.append("/");
        }
        
        for (int i = 0; i < keyColumns.length; i++) {
            if (i > 0) {
                template.append("/");
            }
            template.append("{").append(keyColumns[i]).append("}");
        }
        
        return template.toString();
    }
    
    private String encodeUri(String value) {
        try {
            // Replace spaces with underscores, then URL encode
            String sanitized = value.trim().replaceAll("\\s+", "_");
            return URLEncoder.encode(sanitized, StandardCharsets.UTF_8)
                .replace("+", "%20")  // URLEncoder uses + for space, we want %20
                .replace("%2F", "/"); // Allow forward slashes
        } catch (Exception e) {
            log.warn("Failed to encode URI value: {}", value);
            return value.replaceAll("[^a-zA-Z0-9_-]", "_");
        }
    }
    
    @Data
    @Builder
    public static class TemplateVariable {
        private String name;
        private String modifier;
        private boolean required;
        private String defaultValue;
    }
    
    @Data
    @Builder
    public static class TemplateValidation {
        private boolean valid;
        private String message;
        private Map<String, TemplateVariable> variables;
    }
}
