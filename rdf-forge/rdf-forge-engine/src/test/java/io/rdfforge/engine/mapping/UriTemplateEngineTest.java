package io.rdfforge.engine.mapping;

import io.rdfforge.common.exception.MappingRuleException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("UriTemplateEngine")
class UriTemplateEngineTest {

    @Test
    @DisplayName("render substitutes single placeholder")
    void singlePlaceholder() {
        String out = UriTemplateEngine.render(
            "${baseUri}person/${id}",
            Map.of("baseUri", "https://ex.org/", "id", "42"),
            "r1"
        );
        assertEquals("https://ex.org/person/42", out);
    }

    @Test
    @DisplayName("render substitutes multiple placeholders")
    void multiplePlaceholders() {
        String out = UriTemplateEngine.render(
            "${baseUri}${type}/${id}",
            Map.of("baseUri", "https://ex.org/", "type", "obs", "id", "7"),
            "r1"
        );
        assertEquals("https://ex.org/obs/7", out);
    }

    @Test
    @DisplayName("render honors dotted row.column path")
    void dottedPath() {
        String out = UriTemplateEngine.render(
            "${baseUri}${row.col1}/${row.col2}",
            Map.of("baseUri", "https://ex.org/", "row.col1", "a", "row.col2", "b"),
            "r1"
        );
        assertEquals("https://ex.org/a/b", out);
    }

    @Test
    @DisplayName("render throws MappingRuleException when placeholder is null")
    void nullPlaceholder() {
        MappingRuleException ex = assertThrows(MappingRuleException.class, () ->
            UriTemplateEngine.render("${baseUri}person/${id}",
                Map.of("baseUri", "https://ex.org/"), "rule-123")
        );
        assertEquals("rule-123", ex.getRuleId());
        assertTrue(ex.getMessage().contains("${id}"));
    }

    @Test
    @DisplayName("renderOrNull returns null when a placeholder is missing")
    void renderOrNullMissing() {
        assertNull(UriTemplateEngine.renderOrNull("${a}/${b}", Map.of("a", "x")));
    }

    @Test
    @DisplayName("buildContext exposes both bare and row-prefixed keys")
    void buildContextKeys() {
        Map<String, Object> ctx = UriTemplateEngine.buildContext(
            Map.of("id", "42", "name", "Alice"),
            Map.of("baseUri", "https://ex.org/")
        );
        assertEquals("42", ctx.get("id"));
        assertEquals("42", ctx.get("row.id"));
        assertEquals("Alice", ctx.get("row.name"));
        assertEquals("https://ex.org/", ctx.get("baseUri"));
    }

    @Test
    @DisplayName("null template raises MappingRuleException")
    void nullTemplate() {
        assertThrows(MappingRuleException.class, () ->
            UriTemplateEngine.render(null, Map.of(), "r"));
    }
}
