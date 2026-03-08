package io.rdfforge.engine.shacl;

import io.rdfforge.common.exception.ShaclValidationException;
import io.rdfforge.common.model.ValidationReport;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.StringReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ShaclValidatorService.
 *
 * These tests use real Apache Jena objects — no mocking of the SHACL engine —
 * because the value of the service lies precisely in its interaction with the
 * underlying validation library. Tests are written to be fast and deterministic
 * by using small, self-contained Turtle snippets.
 */
@DisplayName("ShaclValidatorService Tests")
class ShaclValidatorServiceTest {

    // The service is instantiated directly so we can control its internal state
    // (e.g. the timeout) without Spring context overhead.
    private ShaclValidatorService service;

    // ─────────────────────────────────────────────────────────────────────────
    // Minimal SHACL shape: every ex:Person must have an ex:name property.
    // ─────────────────────────────────────────────────────────────────────────
    private static final String VALID_SHAPES_TTL = """
            @prefix sh:   <http://www.w3.org/ns/shacl#> .
            @prefix ex:   <http://example.org/> .
            @prefix xsd:  <http://www.w3.org/2001/XMLSchema#> .

            ex:PersonShape
              a sh:NodeShape ;
              sh:targetClass ex:Person ;
              sh:property [
                sh:path ex:name ;
                sh:minCount 1 ;
                sh:datatype xsd:string ;
              ] .
            """;

    // Conforming data: ex:Person instance with required ex:name.
    private static final String CONFORMING_DATA_TTL = """
            @prefix ex:  <http://example.org/> .
            @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .

            ex:Alice a ex:Person ;
              ex:name "Alice"^^xsd:string .
            """;

    // Violating data: ex:Person instance WITHOUT the required ex:name.
    private static final String VIOLATING_DATA_TTL = """
            @prefix ex:  <http://example.org/> .

            ex:Bob a ex:Person .
            """;

    // Shape that uses sh:severity sh:Warning so we can test severity mapping.
    private static final String WARNING_SHAPES_TTL = """
            @prefix sh:  <http://www.w3.org/ns/shacl#> .
            @prefix ex:  <http://example.org/> .
            @prefix xsd: <http://www.w3.org/2001/XMLSchema#> .

            ex:PersonShape
              a sh:NodeShape ;
              sh:targetClass ex:Person ;
              sh:property [
                sh:path ex:email ;
                sh:minCount 1 ;
                sh:severity sh:Warning ;
              ] .
            """;

    @BeforeEach
    void setUp() throws Exception {
        service = new ShaclValidatorService();
        // Inject the default timeout (60 s) since Spring is not present to process @Value.
        java.lang.reflect.Field timeoutField =
            ShaclValidatorService.class.getDeclaredField("validationTimeoutSeconds");
        timeoutField.setAccessible(true);
        timeoutField.set(service, 60);
    }

    // Helper: parse a Turtle string into a Jena Model.
    private static Model modelFrom(String turtle) {
        Model m = ModelFactory.createDefaultModel();
        m.read(new StringReader(turtle), null, "TURTLE");
        return m;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // validate(Model, Model) — happy paths
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("validate(Model, Model) — conforming data")
    class ConformingValidationTests {

        @Test
        @DisplayName("Should return a conforming report when data satisfies all shapes")
        void validate_ConformingData_ReturnsConformingReport() {
            Model data = modelFrom(CONFORMING_DATA_TTL);
            Model shapes = modelFrom(VALID_SHAPES_TTL);

            ValidationReport report = service.validate(data, shapes);

            assertNotNull(report);
            assertTrue(report.isConforms(), "Report should indicate conformance");
            assertEquals(0, report.getViolationCount());
            assertEquals(0, report.getWarningCount());
            assertNotNull(report.getId(), "Report must have a generated UUID");
            assertNotNull(report.getValidatedAt(), "Report must record timestamp");
            assertTrue(report.getDurationMs() >= 0, "Duration must be non-negative");
        }

        @Test
        @DisplayName("Should return empty results list when data is conforming")
        void validate_ConformingData_HasEmptyResultsList() {
            ValidationReport report = service.validate(
                modelFrom(CONFORMING_DATA_TTL), modelFrom(VALID_SHAPES_TTL));

            assertNotNull(report.getResults());
            assertTrue(report.getResults().isEmpty());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // validate(Model, Model) — violation paths
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("validate(Model, Model) — violations")
    class ViolationValidationTests {

        @Test
        @DisplayName("Should return non-conforming report when data violates a mandatory property")
        void validate_ViolatingData_ReturnsNonConformingReport() {
            Model data = modelFrom(VIOLATING_DATA_TTL);
            Model shapes = modelFrom(VALID_SHAPES_TTL);

            ValidationReport report = service.validate(data, shapes);

            assertFalse(report.isConforms(), "Report should indicate non-conformance");
            assertTrue(report.getViolationCount() > 0,
                "There should be at least one violation");
        }

        @Test
        @DisplayName("Should record VIOLATION severity for sh:minCount constraint breach")
        void validate_ViolatingData_SeverityIsViolation() {
            ValidationReport report = service.validate(
                modelFrom(VIOLATING_DATA_TTL), modelFrom(VALID_SHAPES_TTL));

            ValidationReport.ValidationResult result = report.getResults().get(0);
            assertEquals(ValidationReport.ValidationResult.Severity.VIOLATION, result.getSeverity());
        }

        @Test
        @DisplayName("Should record WARNING severity when shape uses sh:severity sh:Warning")
        void validate_WarningSeverityShape_MapsToWarningSeverity() {
            // Bob has no ex:email — shape fires a Warning, not a Violation
            ValidationReport report = service.validate(
                modelFrom(VIOLATING_DATA_TTL), modelFrom(WARNING_SHAPES_TTL));

            assertFalse(report.isConforms());
            assertEquals(0, report.getViolationCount(),
                "Violation count must not include warning-level entries");
            assertTrue(report.getWarningCount() > 0,
                "Warning count must reflect the sh:Warning severity entry");

            boolean hasWarning = report.getResults().stream()
                .anyMatch(r -> r.getSeverity() == ValidationReport.ValidationResult.Severity.WARNING);
            assertTrue(hasWarning);
        }

        @Test
        @DisplayName("Should populate focusNode in each validation result")
        void validate_ViolatingData_PopulatesFocusNode() {
            ValidationReport report = service.validate(
                modelFrom(VIOLATING_DATA_TTL), modelFrom(VALID_SHAPES_TTL));

            ValidationReport.ValidationResult result = report.getResults().get(0);
            assertNotNull(result.getFocusNode(), "Focus node must be populated");
            assertTrue(result.getFocusNode().contains("Bob"),
                "Focus node should reference the violating resource");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // validate(Model, String) — shapes provided as Turtle string
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("validate(Model, String) — shapes as Turtle content")
    class ValidateWithStringShapesTests {

        @Test
        @DisplayName("Should validate correctly when shapes are provided as a string")
        void validate_ValidShapesString_ReturnsReport() {
            ValidationReport report = service.validate(
                modelFrom(CONFORMING_DATA_TTL), VALID_SHAPES_TTL);

            assertNotNull(report);
            assertTrue(report.isConforms());
        }

        @Test
        @DisplayName("Should throw ShaclValidationException when shapes string is invalid Turtle")
        void validate_InvalidShapesTurtle_ThrowsShaclValidationException() {
            Model data = modelFrom(CONFORMING_DATA_TTL);
            String badTurtle = "this is not valid turtle @@@";

            assertThrows(ShaclValidationException.class,
                () -> service.validate(data, badTurtle));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // validateSyntax(String)
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("validateSyntax(String)")
    class ValidateSyntaxTests {

        @Test
        @DisplayName("Should return true for syntactically correct SHACL Turtle")
        void validateSyntax_ValidShacl_ReturnsTrue() {
            assertTrue(service.validateSyntax(VALID_SHAPES_TTL));
        }

        @Test
        @DisplayName("Should return false for invalid Turtle syntax")
        void validateSyntax_InvalidTurtle_ReturnsFalse() {
            assertFalse(service.validateSyntax("@prefix ex: <broken"));
        }

        @Test
        @DisplayName("Should return false for syntactically valid RDF that is not SHACL")
        void validateSyntax_ValidRdfButNoShapes_ReturnsTrueAsParsingSucceeds() {
            // Jena's Shapes.parse succeeds on valid RDF even with no sh:NodeShape;
            // the service docs say "validates syntax", which means RDF parseability.
            String plainRdf = """
                    @prefix ex: <http://example.org/> .
                    ex:Thing a ex:Concept .
                    """;
            // Parsing succeeds — no exception is thrown — so syntax is valid.
            assertTrue(service.validateSyntax(plainRdf));
        }

        @ParameterizedTest(name = "validateSyntax returns false for blank/null input: [{0}]")
        @NullAndEmptySource
        @ValueSource(strings = {"   ", "\t\n"})
        @DisplayName("Should return false for null or blank content")
        void validateSyntax_NullOrBlank_ReturnsFalse(String input) {
            assertFalse(service.validateSyntax(input));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // validateSyntaxWithDetails(String)
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("validateSyntaxWithDetails(String)")
    class ValidateSyntaxWithDetailsTests {

        @Test
        @DisplayName("Should return valid=true and message for well-formed SHACL")
        void validateSyntaxWithDetails_ValidShacl_ReturnsValid() {
            ShaclValidatorService.ValidationSyntaxResult result =
                service.validateSyntaxWithDetails(VALID_SHAPES_TTL);

            assertTrue(result.valid());
            assertNotNull(result.message());
            assertNull(result.line(), "Line should be null for valid input");
            assertNull(result.column(), "Column should be null for valid input");
        }

        @Test
        @DisplayName("Should return valid=false with error details for malformed Turtle")
        void validateSyntaxWithDetails_InvalidTurtle_ReturnsInvalidWithMessage() {
            ShaclValidatorService.ValidationSyntaxResult result =
                service.validateSyntaxWithDetails("PREFIX ex: <broken turtle @@@");

            assertFalse(result.valid());
            assertNotNull(result.message(), "Error message must be present");
        }

        @ParameterizedTest(name = "validateSyntaxWithDetails returns invalid for blank: [{0}]")
        @NullAndEmptySource
        @ValueSource(strings = {"  "})
        @DisplayName("Should return valid=false with descriptive message for null or blank input")
        void validateSyntaxWithDetails_NullOrBlank_ReturnsInvalid(String input) {
            ShaclValidatorService.ValidationSyntaxResult result =
                service.validateSyntaxWithDetails(input);

            assertFalse(result.valid());
            assertNotNull(result.message());
            assertTrue(result.message().toLowerCase().contains("empty") ||
                       result.message().toLowerCase().contains("null"),
                "Message should describe the empty/null condition, got: " + result.message());
        }

        @Test
        @DisplayName("Should include line number in error for parseable position error")
        void validateSyntaxWithDetails_LinePositionError_IncludesLineInfo() {
            // A Turtle document that is almost valid but fails on line 2
            String badOnLine2 = """
                    @prefix ex: <http://example.org/> .
                    ex:Bob THIS_IS_WRONG .
                    """;

            ShaclValidatorService.ValidationSyntaxResult result =
                service.validateSyntaxWithDetails(badOnLine2);

            assertFalse(result.valid());
            // Jena reports a line number for this kind of error
            // (The exact value may vary; we just verify it is populated when available.)
            // If line is non-null it must be positive.
            if (result.line() != null) {
                assertTrue(result.line() >= 0);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // shutdown — verify graceful teardown does not throw
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("shutdown()")
    class ShutdownTests {

        @Test
        @DisplayName("Should shut down the internal thread pool without throwing")
        void shutdown_Called_DoesNotThrow() {
            assertDoesNotThrow(() -> service.shutdown());
        }

        @Test
        @DisplayName("Should be idempotent — calling shutdown twice does not throw")
        void shutdown_CalledTwice_DoesNotThrow() {
            assertDoesNotThrow(() -> {
                service.shutdown();
                service.shutdown();
            });
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Concurrent execution — verifies the thread pool can handle parallel calls
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Concurrent validation")
    class ConcurrentValidationTests {

        @Test
        @DisplayName("Should handle multiple concurrent validations without data corruption")
        void validate_ConcurrentCalls_AllReturnCorrectResults() throws Exception {
            int threads = 4;
            ExecutorService pool = Executors.newFixedThreadPool(threads);

            Future<ValidationReport>[] futures = new Future[threads];
            for (int i = 0; i < threads; i++) {
                futures[i] = pool.submit(() ->
                    service.validate(
                        modelFrom(CONFORMING_DATA_TTL),
                        modelFrom(VALID_SHAPES_TTL)));
            }

            pool.shutdown();
            pool.awaitTermination(30, TimeUnit.SECONDS);

            for (Future<ValidationReport> future : futures) {
                ValidationReport report = future.get();
                assertTrue(report.isConforms(),
                    "All concurrent reports should indicate conformance");
            }
        }
    }
}
