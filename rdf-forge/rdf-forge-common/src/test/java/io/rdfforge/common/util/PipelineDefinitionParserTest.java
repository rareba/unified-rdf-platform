package io.rdfforge.common.util;

import io.rdfforge.common.model.Pipeline;
import io.rdfforge.common.model.PipelineStep;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for PipelineDefinitionParser.
 *
 * The parser is a pure utility class with no external dependencies, so every
 * test is entirely self-contained. Tests cover JSON format, YAML format, both
 * parameter-key variants ("params" / "parameters"), error paths, and
 * boundary conditions.
 */
@DisplayName("PipelineDefinitionParser Tests")
class PipelineDefinitionParserTest {

    // ─────────────────────────────────────────────────────────────────────────
    // Null / empty / blank input
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Null / empty / blank definition")
    class BlankInputTests {

        @ParameterizedTest(name = "parse returns empty list for blank input: [{0}]")
        @NullAndEmptySource
        @ValueSource(strings = {"  ", "\t", "\n"})
        @DisplayName("Should return an empty list for null, empty, or whitespace definitions")
        void parse_NullOrBlankDefinition_ReturnsEmptyList(String definition) {
            List<PipelineStep> steps = PipelineDefinitionParser.parse(
                definition, Pipeline.DefinitionFormat.JSON);
            assertNotNull(steps);
            assertTrue(steps.isEmpty());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // JSON format — happy paths
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("JSON format parsing")
    class JsonParsingTests {

        @Test
        @DisplayName("Should parse a single-step JSON pipeline definition")
        void parse_SingleStepJson_ReturnsSingleStep() {
            String json = """
                    {
                      "steps": [
                        {
                          "id": "step1",
                          "operation": "load-csv",
                          "name": "Load CSV"
                        }
                      ]
                    }
                    """;

            List<PipelineStep> steps = PipelineDefinitionParser.parse(
                json, Pipeline.DefinitionFormat.JSON);

            assertEquals(1, steps.size());
            PipelineStep step = steps.get(0);
            assertEquals("step1", step.getId());
            assertEquals("load-csv", step.getOperationType());
            assertEquals("Load CSV", step.getName());
        }

        @Test
        @DisplayName("Should parse a multi-step JSON pipeline in correct order")
        void parse_MultiStepJson_ReturnsAllStepsInOrder() {
            String json = """
                    {
                      "steps": [
                        { "id": "s1", "operation": "load-csv"  },
                        { "id": "s2", "operation": "map-to-rdf" },
                        { "id": "s3", "operation": "sparql-insert" }
                      ]
                    }
                    """;

            List<PipelineStep> steps = PipelineDefinitionParser.parse(
                json, Pipeline.DefinitionFormat.JSON);

            assertEquals(3, steps.size());
            assertEquals("s1", steps.get(0).getId());
            assertEquals("s2", steps.get(1).getId());
            assertEquals("s3", steps.get(2).getId());
        }

        @Test
        @DisplayName("Should return empty list when JSON has an empty 'steps' array")
        void parse_EmptyStepsArrayJson_ReturnsEmptyList() {
            String json = "{\"steps\": []}";

            List<PipelineStep> steps = PipelineDefinitionParser.parse(
                json, Pipeline.DefinitionFormat.JSON);

            assertNotNull(steps);
            assertTrue(steps.isEmpty());
        }

        @Test
        @DisplayName("Should return empty list when JSON has no 'steps' key at all")
        void parse_NoStepsKeyJson_ReturnsEmptyList() {
            String json = "{\"name\": \"pipeline\"}";

            List<PipelineStep> steps = PipelineDefinitionParser.parse(
                json, Pipeline.DefinitionFormat.JSON);

            assertNotNull(steps);
            assertTrue(steps.isEmpty());
        }

        @Test
        @DisplayName("Should parse input and output connections from JSON")
        void parse_JsonWithConnections_ParsesInputsAndOutputs() {
            String json = """
                    {
                      "steps": [
                        {
                          "id": "s1",
                          "operation": "load-csv",
                          "inputs":  ["src"],
                          "outputs": ["dst"]
                        }
                      ]
                    }
                    """;

            PipelineStep step = PipelineDefinitionParser.parse(
                json, Pipeline.DefinitionFormat.JSON).get(0);

            assertEquals(List.of("src"), step.getInputConnections());
            assertEquals(List.of("dst"), step.getOutputConnections());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // YAML format — happy paths
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("YAML format parsing")
    class YamlParsingTests {

        @Test
        @DisplayName("Should parse a single-step YAML pipeline definition")
        void parse_SingleStepYaml_ReturnsSingleStep() {
            String yaml = """
                    steps:
                      - id: step1
                        operation: load-csv
                        name: Load CSV
                    """;

            List<PipelineStep> steps = PipelineDefinitionParser.parse(
                yaml, Pipeline.DefinitionFormat.YAML);

            assertEquals(1, steps.size());
            assertEquals("step1", steps.get(0).getId());
            assertEquals("load-csv", steps.get(0).getOperationType());
            assertEquals("Load CSV", steps.get(0).getName());
        }

        @Test
        @DisplayName("Should parse a multi-step YAML pipeline in correct order")
        void parse_MultiStepYaml_ReturnsAllStepsInOrder() {
            String yaml = """
                    steps:
                      - id: s1
                        operation: load-csv
                      - id: s2
                        operation: map-to-rdf
                    """;

            List<PipelineStep> steps = PipelineDefinitionParser.parse(
                yaml, Pipeline.DefinitionFormat.YAML);

            assertEquals(2, steps.size());
            assertEquals("s1", steps.get(0).getId());
            assertEquals("s2", steps.get(1).getId());
        }

        @Test
        @DisplayName("Should parse YAML connections (inputs / outputs)")
        void parse_YamlWithConnections_ParsesConnections() {
            String yaml = """
                    steps:
                      - id: step1
                        operation: load-csv
                        inputs:
                          - upstream
                        outputs:
                          - downstream
                    """;

            PipelineStep step = PipelineDefinitionParser.parse(
                yaml, Pipeline.DefinitionFormat.YAML).get(0);

            assertEquals(List.of("upstream"), step.getInputConnections());
            assertEquals(List.of("downstream"), step.getOutputConnections());
        }

        @Test
        @DisplayName("Should return empty list when YAML has an empty 'steps' list")
        void parse_EmptyStepsYaml_ReturnsEmptyList() {
            String yaml = "steps: []";

            List<PipelineStep> steps = PipelineDefinitionParser.parse(
                yaml, Pipeline.DefinitionFormat.YAML);

            assertNotNull(steps);
            assertTrue(steps.isEmpty());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // "params" vs "parameters" key support
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Parameter key variants: 'params' vs 'parameters'")
    class ParameterKeyVariantTests {

        @Test
        @DisplayName("Should read step parameters from 'params' key (UI format)")
        void parse_ParamsKey_ParsesParameters() {
            String json = """
                    {
                      "steps": [
                        {
                          "id": "s1",
                          "operation": "load-csv",
                          "params": { "file": "data.csv", "delimiter": "," }
                        }
                      ]
                    }
                    """;

            PipelineStep step = PipelineDefinitionParser.parse(
                json, Pipeline.DefinitionFormat.JSON).get(0);

            assertNotNull(step.getParameters(), "Parameters map must not be null");
            assertEquals("data.csv", step.getParameters().get("file"));
            assertEquals(",", step.getParameters().get("delimiter"));
        }

        @Test
        @DisplayName("Should read step parameters from 'parameters' key (API format)")
        void parse_ParametersKey_ParsesParameters() {
            String json = """
                    {
                      "steps": [
                        {
                          "id": "s1",
                          "operation": "load-csv",
                          "parameters": { "file": "api.csv" }
                        }
                      ]
                    }
                    """;

            PipelineStep step = PipelineDefinitionParser.parse(
                json, Pipeline.DefinitionFormat.JSON).get(0);

            assertNotNull(step.getParameters());
            assertEquals("api.csv", step.getParameters().get("file"));
        }

        @Test
        @DisplayName("'params' should take precedence over 'parameters' when both are present")
        void parse_BothParamsAndParameters_ParamsTakesPrecedence() {
            String json = """
                    {
                      "steps": [
                        {
                          "id": "s1",
                          "operation": "load-csv",
                          "params":      { "file": "from-params.csv" },
                          "parameters":  { "file": "from-parameters.csv" }
                        }
                      ]
                    }
                    """;

            PipelineStep step = PipelineDefinitionParser.parse(
                json, Pipeline.DefinitionFormat.JSON).get(0);

            assertEquals("from-params.csv", step.getParameters().get("file"),
                "'params' key should take precedence over 'parameters'");
        }

        @Test
        @DisplayName("Step without any parameter key should have null parameters")
        void parse_NoParamKey_ParametersAreNull() {
            String json = """
                    {
                      "steps": [
                        { "id": "s1", "operation": "noop" }
                      ]
                    }
                    """;

            PipelineStep step = PipelineDefinitionParser.parse(
                json, Pipeline.DefinitionFormat.JSON).get(0);

            assertNull(step.getParameters(),
                "Steps without a params/parameters key should have null parameters map");
        }

        @Test
        @DisplayName("Should support nested parameter maps inside 'params'")
        void parse_NestedParams_ParsesNestedMap() {
            String json = """
                    {
                      "steps": [
                        {
                          "id": "s1",
                          "operation": "map-to-rdf",
                          "params": {
                            "mapping": { "subject": "${id}", "predicate": "ex:name" }
                          }
                        }
                      ]
                    }
                    """;

            PipelineStep step = PipelineDefinitionParser.parse(
                json, Pipeline.DefinitionFormat.JSON).get(0);

            assertNotNull(step.getParameters().get("mapping"));
            @SuppressWarnings("unchecked")
            Map<String, Object> mapping = (Map<String, Object>) step.getParameters().get("mapping");
            assertEquals("${id}", mapping.get("subject"));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Error paths
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Error paths — invalid / unparseable definitions")
    class ErrorPathTests {

        @Test
        @DisplayName("Should throw PipelineParseException for invalid JSON")
        void parse_InvalidJson_ThrowsPipelineParseException() {
            String badJson = "{ steps: [ broken";

            assertThrows(PipelineDefinitionParser.PipelineParseException.class,
                () -> PipelineDefinitionParser.parse(badJson, Pipeline.DefinitionFormat.JSON));
        }

        @Test
        @DisplayName("Should throw PipelineParseException for invalid YAML")
        void parse_InvalidYaml_ThrowsPipelineParseException() {
            // YAML that deliberately breaks indentation rules
            String badYaml = "steps:\n  - id: ok\n    bad indent:\nkey: [broken";

            assertThrows(PipelineDefinitionParser.PipelineParseException.class,
                () -> PipelineDefinitionParser.parse(badYaml, Pipeline.DefinitionFormat.YAML));
        }

        @Test
        @DisplayName("PipelineParseException should wrap the original cause")
        void parse_InvalidJson_ExceptionHasCause() {
            String badJson = "this is not json at all";

            PipelineDefinitionParser.PipelineParseException ex = assertThrows(
                PipelineDefinitionParser.PipelineParseException.class,
                () -> PipelineDefinitionParser.parse(badJson, Pipeline.DefinitionFormat.JSON));

            assertNotNull(ex.getCause(), "Exception must wrap the original parsing error");
            assertNotNull(ex.getMessage(), "Exception must have a descriptive message");
        }

        @Test
        @DisplayName("Should throw PipelineParseException when JSON root is an array instead of object")
        void parse_JsonArrayRoot_ThrowsPipelineParseException() {
            String jsonArray = "[{\"id\": \"s1\"}]";

            // The parser expects an object with a "steps" key at root level;
            // an array root will fail the cast to Map.
            assertThrows(PipelineDefinitionParser.PipelineParseException.class,
                () -> PipelineDefinitionParser.parse(jsonArray, Pipeline.DefinitionFormat.JSON));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Format routing — same content, different format enum
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Format routing")
    class FormatRoutingTests {

        @Test
        @DisplayName("Should fail when YAML content is fed with JSON format (it parses differently)")
        void parse_YamlContentWithJsonFormat_EitherFailsOrMisparses() {
            // YAML and JSON overlap for simple cases; a YAML-only construct
            // (block mapping without quotes) may not be valid JSON.
            String yamlOnly = "steps:\n  - id: s1\n    operation: noop\n";

            // Depending on the Jackson version this either throws or returns empty.
            // The important thing is it does NOT silently succeed with correct data.
            try {
                List<PipelineStep> steps = PipelineDefinitionParser.parse(
                    yamlOnly, Pipeline.DefinitionFormat.JSON);
                // If it doesn't throw, it must have failed to find valid steps
                assertTrue(steps.isEmpty(),
                    "Parsing YAML with JSON mapper should produce no valid steps");
            } catch (PipelineDefinitionParser.PipelineParseException e) {
                // This is also acceptable — the parser correctly detected the mismatch
            }
        }

        @Test
        @DisplayName("Valid JSON parsed with YAML format should still succeed (JSON is valid YAML)")
        void parse_JsonContentWithYamlFormat_Succeeds() {
            // JSON is a valid subset of YAML, so this should parse correctly.
            String json = "{\"steps\": [{\"id\": \"s1\", \"operation\": \"noop\"}]}";

            List<PipelineStep> steps = PipelineDefinitionParser.parse(
                json, Pipeline.DefinitionFormat.YAML);

            assertEquals(1, steps.size());
            assertEquals("s1", steps.get(0).getId());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Real-world multi-step pipeline with all fields populated
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Complete pipeline definition")
    class CompletePipelineTests {

        @Test
        @DisplayName("Should parse a realistic ETL pipeline with five steps and all field types")
        void parse_RealisticEtlPipeline_ParsesAllFields() {
            String json = """
                    {
                      "steps": [
                        {
                          "id": "load",
                          "name": "Load CSV",
                          "operation": "load-csv",
                          "params": { "file": "${SOURCE_FILE}", "delimiter": "," }
                        },
                        {
                          "id": "validate",
                          "name": "Validate Schema",
                          "operation": "shacl-validate",
                          "inputs": ["load"],
                          "params": { "shapesFile": "shapes.ttl" }
                        },
                        {
                          "id": "map",
                          "name": "Map to RDF",
                          "operation": "map-to-rdf",
                          "inputs": ["validate"],
                          "params": { "baseUri": "http://example.org/" }
                        },
                        {
                          "id": "enrich",
                          "name": "Enrich with SPARQL",
                          "operation": "sparql-construct",
                          "inputs": ["map"],
                          "params": { "query": "CONSTRUCT {...} WHERE {...}" }
                        },
                        {
                          "id": "publish",
                          "name": "Publish to Triplestore",
                          "operation": "sparql-insert",
                          "inputs": ["enrich"],
                          "params": { "endpoint": "${TRIPLESTORE_URL}", "graph": "${TARGET_GRAPH}" }
                        }
                      ]
                    }
                    """;

            List<PipelineStep> steps = PipelineDefinitionParser.parse(
                json, Pipeline.DefinitionFormat.JSON);

            assertEquals(5, steps.size(), "All five steps should be parsed");

            // Validate first step
            PipelineStep load = steps.get(0);
            assertEquals("load", load.getId());
            assertEquals("load-csv", load.getOperationType());
            assertEquals("${SOURCE_FILE}", load.getParameters().get("file"));
            assertNull(load.getInputConnections(), "Source step has no inputs");

            // Validate that connections form a chain
            assertEquals(List.of("load"), steps.get(1).getInputConnections());
            assertEquals(List.of("validate"), steps.get(2).getInputConnections());
            assertEquals(List.of("map"), steps.get(3).getInputConnections());
            assertEquals(List.of("enrich"), steps.get(4).getInputConnections());

            // Validate last step parameters
            PipelineStep publish = steps.get(4);
            assertEquals("${TRIPLESTORE_URL}", publish.getParameters().get("endpoint"));
            assertEquals("${TARGET_GRAPH}", publish.getParameters().get("graph"));
        }
    }
}
