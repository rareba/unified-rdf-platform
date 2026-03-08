package io.rdfforge.engine.operation.source;

import io.rdfforge.engine.operation.Operation;
import io.rdfforge.engine.operation.Operation.OperationContext;
import io.rdfforge.engine.operation.Operation.OperationResult;
import io.rdfforge.engine.operation.OperationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for LoadCsvOperation.
 *
 * Strategy: LoadCsvOperation resolves files relative to a list of "allowed"
 * directories checked against the JVM working directory. We use @TempDir to
 * write controlled CSV files, then configure the working directory so that
 * the file-resolution logic finds them.
 *
 * Tests that exercise the path-traversal guard rely on the fact that the
 * implementation always normalizes and checks Path#startsWith before reading.
 */
@DisplayName("LoadCsvOperation Tests")
class LoadCsvOperationTest {

    private LoadCsvOperation operation;

    // Reusable no-op callback — avoids NPE when the operation calls callback.onLog(...)
    private static final Operation.OperationCallback NOOP_CALLBACK = new Operation.OperationCallback() {
        @Override public void onProgress(long processed, long total) {}
        @Override public void onLog(String level, String message) {}
        @Override public void onMetric(String name, Object value) {}
    };

    @BeforeEach
    void setUp() {
        operation = new LoadCsvOperation();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Operation metadata
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Operation metadata")
    class MetadataTests {

        @Test
        @DisplayName("getId() should return 'load-csv'")
        void getId_ReturnsLoadCsv() {
            assertEquals("load-csv", operation.getId());
        }

        @Test
        @DisplayName("getName() should be non-blank")
        void getName_IsNonBlank() {
            assertFalse(operation.getName().isBlank());
        }

        @Test
        @DisplayName("getType() should be SOURCE")
        void getType_IsSource() {
            assertEquals(Operation.OperationType.SOURCE, operation.getType());
        }

        @Test
        @DisplayName("getParameters() should declare required 'file' parameter")
        void getParameters_DeclaresFileAsRequired() {
            Map<String, Operation.ParameterSpec> params = operation.getParameters();
            assertTrue(params.containsKey("file"));
            assertTrue(params.get("file").required());
        }

        @Test
        @DisplayName("getParameters() should declare optional 'delimiter' with default ','")
        void getParameters_DelimiterDefaultIsComma() {
            Operation.ParameterSpec spec = operation.getParameters().get("delimiter");
            assertNotNull(spec);
            assertFalse(spec.required());
            assertEquals(',', spec.defaultValue());
        }

        @Test
        @DisplayName("getParameters() should declare optional 'encoding' defaulting to 'UTF-8'")
        void getParameters_EncodingDefaultIsUtf8() {
            Operation.ParameterSpec spec = operation.getParameters().get("encoding");
            assertNotNull(spec);
            assertEquals("UTF-8", spec.defaultValue());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Missing / blank file path
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Missing file parameter")
    class MissingFileParameterTests {

        @Test
        @DisplayName("Should throw OperationException when 'file' parameter is absent")
        void execute_MissingFileParam_ThrowsOperationException() {
            OperationContext ctx = new OperationContext(
                Map.of(), null, null, Map.of(), NOOP_CALLBACK);

            assertThrows(OperationException.class, () -> operation.execute(ctx));
        }

        @Test
        @DisplayName("Should throw OperationException when 'file' parameter is an empty string")
        void execute_EmptyFileParam_ThrowsOperationException() {
            OperationContext ctx = buildContext(Map.of("file", ""));

            assertThrows(OperationException.class, () -> operation.execute(ctx));
        }

        @Test
        @DisplayName("Should throw OperationException when 'file' parameter is blank whitespace")
        void execute_BlankFileParam_ThrowsOperationException() {
            OperationContext ctx = buildContext(Map.of("file", "   "));

            assertThrows(OperationException.class, () -> operation.execute(ctx));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // File-not-found
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("File not found")
    class FileNotFoundTests {

        @Test
        @DisplayName("Should throw OperationException when the referenced file does not exist")
        void execute_NonExistentFile_ThrowsOperationException() {
            OperationContext ctx = buildContext(Map.of("file", "does-not-exist.csv"));

            OperationException ex = assertThrows(OperationException.class,
                () -> operation.execute(ctx));
            assertTrue(ex.getMessage().toLowerCase().contains("not found") ||
                       ex.getMessage().toLowerCase().contains("file"),
                "Exception message should mention file-not-found, got: " + ex.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Path-traversal prevention
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Path traversal prevention")
    class PathTraversalTests {

        @ParameterizedTest(name = "Traversal path [{0}] must not escape working directory")
        @ValueSource(strings = {
            "../../../etc/passwd",
            "../../../../../../windows/system32/config",
            "%2e%2e%2fetc%2fpasswd",
            "subfolder/../../etc/passwd"
        })
        @DisplayName("Should throw OperationException (not read an out-of-bounds file) for traversal paths")
        void execute_PathTraversal_ThrowsAndDoesNotEscape(String maliciousPath) {
            // The operation resolves the path and checks containment before reading.
            // It should either throw because the file doesn't exist within the allowed
            // directories, or throw because the normalised path escapes the base.
            // Either way, it must NOT succeed in reading arbitrary file-system paths.
            OperationContext ctx = buildContext(Map.of("file", maliciousPath));

            // Acceptable outcomes: OperationException OR the stream is empty/null
            // (i.e. the operation refuses to open the file).
            // The implementation currently throws on file-not-found after
            // containment-check failure, so OperationException is the expected path.
            assertThrows(OperationException.class, () -> {
                OperationResult result = operation.execute(ctx);
                // If execute() does not throw, ensure no data was returned
                if (result.outputStream() != null) {
                    List<?> rows = result.outputStream().collect(Collectors.toList());
                    assertTrue(rows.isEmpty(),
                        "Should not have loaded any data from a traversal path");
                }
            });
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Successful CSV loading (writing files into the working directory)
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Successful CSV loading")
    class SuccessfulLoadTests {

        @Test
        @DisplayName("Should load rows from a CSV under demo-data/ with comma delimiter")
        void execute_ValidCsvInDemoData_ReturnsRows(@TempDir Path tempDir) throws Exception {
            // Write a CSV into a directory named "demo-data" inside tempDir,
            // then override user.dir so the resolver finds it.
            Path demoData = tempDir.resolve("demo-data");
            Files.createDirectories(demoData);
            Path csv = demoData.resolve("sample.csv");
            Files.writeString(csv,
                "name,age,city\nAlice,30,NYC\nBob,25,LA\n",
                StandardCharsets.UTF_8);

            String originalUserDir = System.getProperty("user.dir");
            System.setProperty("user.dir", tempDir.toAbsolutePath().toString());
            try {
                OperationContext ctx = buildContext(Map.of("file", "sample.csv"));
                OperationResult result = operation.execute(ctx);

                assertTrue(result.success());
                assertNotNull(result.outputStream());

                List<Map<String, Object>> rows = result.outputStream()
                    .map(r -> (Map<String, Object>) r)
                    .collect(Collectors.toList());

                assertEquals(2, rows.size(), "Should have loaded 2 data rows");
                assertEquals("Alice", rows.get(0).get("name"));
                assertEquals("NYC", rows.get(0).get("city"));
            } finally {
                System.setProperty("user.dir", originalUserDir);
            }
        }

        @Test
        @DisplayName("Should load rows and include _rowNumber in each row map")
        void execute_ValidCsv_IncludesRowNumberField(@TempDir Path tempDir) throws Exception {
            Path demoData = tempDir.resolve("demo-data");
            Files.createDirectories(demoData);
            Files.writeString(demoData.resolve("rows.csv"),
                "col\nA\nB\nC\n", StandardCharsets.UTF_8);

            String originalUserDir = System.getProperty("user.dir");
            System.setProperty("user.dir", tempDir.toAbsolutePath().toString());
            try {
                OperationContext ctx = buildContext(Map.of("file", "rows.csv"));
                OperationResult result = operation.execute(ctx);

                List<Map<String, Object>> rows = result.outputStream()
                    .map(r -> (Map<String, Object>) r)
                    .collect(Collectors.toList());

                for (int i = 0; i < rows.size(); i++) {
                    assertEquals((long) (i + 1), rows.get(i).get("_rowNumber"),
                        "Row " + i + " should have _rowNumber " + (i + 1));
                }
            } finally {
                System.setProperty("user.dir", originalUserDir);
            }
        }

        @Test
        @DisplayName("Should use tab delimiter when specified via parameter")
        void execute_TsvDelimiter_ParsesTabSeparatedRows(@TempDir Path tempDir) throws Exception {
            Path demoData = tempDir.resolve("demo-data");
            Files.createDirectories(demoData);
            Files.writeString(demoData.resolve("data.tsv"),
                "name\tvalue\nFoo\t42\nBar\t7\n", StandardCharsets.UTF_8);

            String originalUserDir = System.getProperty("user.dir");
            System.setProperty("user.dir", tempDir.toAbsolutePath().toString());
            try {
                Map<String, Object> params = new HashMap<>();
                params.put("file", "data.tsv");
                params.put("delimiter", '\t');

                OperationResult result = operation.execute(buildContext(params));
                List<Map<String, Object>> rows = result.outputStream()
                    .map(r -> (Map<String, Object>) r)
                    .collect(Collectors.toList());

                assertEquals(2, rows.size());
                assertEquals("42", rows.get(0).get("value"));
            } finally {
                System.setProperty("user.dir", originalUserDir);
            }
        }

        @Test
        @DisplayName("Should accept delimiter as a String ('|') and parse correctly")
        void execute_PipeDelimiterAsString_ParsesCorrectly(@TempDir Path tempDir) throws Exception {
            Path demoData = tempDir.resolve("demo-data");
            Files.createDirectories(demoData);
            Files.writeString(demoData.resolve("piped.csv"),
                "a|b\n1|2\n3|4\n", StandardCharsets.UTF_8);

            String originalUserDir = System.getProperty("user.dir");
            System.setProperty("user.dir", tempDir.toAbsolutePath().toString());
            try {
                Map<String, Object> params = new HashMap<>();
                params.put("file", "piped.csv");
                params.put("delimiter", "|");   // String, not char

                OperationResult result = operation.execute(buildContext(params));
                List<Map<String, Object>> rows = result.outputStream()
                    .map(r -> (Map<String, Object>) r)
                    .collect(Collectors.toList());

                assertEquals(2, rows.size());
                assertEquals("2", rows.get(0).get("b"));
            } finally {
                System.setProperty("user.dir", originalUserDir);
            }
        }

        @Test
        @DisplayName("Should treat first row as data when hasHeader=false")
        void execute_NoHeader_FirstRowIsData(@TempDir Path tempDir) throws Exception {
            Path demoData = tempDir.resolve("demo-data");
            Files.createDirectories(demoData);
            Files.writeString(demoData.resolve("noheader.csv"),
                "A,B\nX,Y\n", StandardCharsets.UTF_8);

            String originalUserDir = System.getProperty("user.dir");
            System.setProperty("user.dir", tempDir.toAbsolutePath().toString());
            try {
                Map<String, Object> params = new HashMap<>();
                params.put("file", "noheader.csv");
                params.put("hasHeader", false);

                OperationResult result = operation.execute(buildContext(params));
                List<Map<String, Object>> rows = result.outputStream()
                    .map(r -> (Map<String, Object>) r)
                    .collect(Collectors.toList());

                // Both data rows should be returned; columns named "column0", "column1"
                assertEquals(2, rows.size());
                assertEquals("A", rows.get(0).get("column0"),
                    "When no header, first row becomes data with positional column names");
            } finally {
                System.setProperty("user.dir", originalUserDir);
            }
        }

        @Test
        @DisplayName("Should return empty stream for a CSV with only a header row")
        void execute_HeaderOnlyFile_ReturnsEmptyStream(@TempDir Path tempDir) throws Exception {
            Path demoData = tempDir.resolve("demo-data");
            Files.createDirectories(demoData);
            Files.writeString(demoData.resolve("headeronly.csv"),
                "col1,col2,col3\n", StandardCharsets.UTF_8);

            String originalUserDir = System.getProperty("user.dir");
            System.setProperty("user.dir", tempDir.toAbsolutePath().toString());
            try {
                OperationContext ctx = buildContext(Map.of("file", "headeronly.csv"));
                OperationResult result = operation.execute(ctx);

                assertTrue(result.success());
                List<?> rows = result.outputStream().collect(Collectors.toList());
                assertTrue(rows.isEmpty(), "Header-only CSV must produce zero data rows");
            } finally {
                System.setProperty("user.dir", originalUserDir);
            }
        }

        @Test
        @DisplayName("Should propagate headers in result metadata")
        void execute_ValidCsv_MetadataContainsHeaders(@TempDir Path tempDir) throws Exception {
            Path demoData = tempDir.resolve("demo-data");
            Files.createDirectories(demoData);
            Files.writeString(demoData.resolve("meta.csv"),
                "x,y,z\n1,2,3\n", StandardCharsets.UTF_8);

            String originalUserDir = System.getProperty("user.dir");
            System.setProperty("user.dir", tempDir.toAbsolutePath().toString());
            try {
                OperationResult result = operation.execute(
                    buildContext(Map.of("file", "meta.csv")));

                assertNotNull(result.metadata());
                assertTrue(result.metadata().containsKey("headers"),
                    "Metadata must include 'headers' key");

                @SuppressWarnings("unchecked")
                List<String> headers = (List<String>) result.metadata().get("headers");
                assertEquals(List.of("x", "y", "z"), headers);
            } finally {
                System.setProperty("user.dir", originalUserDir);
            }
        }

        @Test
        @DisplayName("Should handle UTF-8 encoded content with accented characters")
        void execute_Utf8Encoding_ParsesAccentedChars(@TempDir Path tempDir) throws Exception {
            Path demoData = tempDir.resolve("demo-data");
            Files.createDirectories(demoData);
            Files.writeString(demoData.resolve("utf8.csv"),
                "prénom\nJosé\nÉlodie\n", StandardCharsets.UTF_8);

            String originalUserDir = System.getProperty("user.dir");
            System.setProperty("user.dir", tempDir.toAbsolutePath().toString());
            try {
                Map<String, Object> params = new HashMap<>();
                params.put("file", "utf8.csv");
                params.put("encoding", "UTF-8");

                OperationResult result = operation.execute(buildContext(params));
                List<Map<String, Object>> rows = result.outputStream()
                    .map(r -> (Map<String, Object>) r)
                    .collect(Collectors.toList());

                assertEquals(2, rows.size());
                assertEquals("José", rows.get(0).get("prénom"));
            } finally {
                System.setProperty("user.dir", originalUserDir);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Delimiter parsing edge cases (unit-level, no file I/O needed)
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Delimiter parameter parsing")
    class DelimiterParsingTests {

        @Test
        @DisplayName("Null delimiter should default to comma")
        void execute_NullDelimiter_DefaultsToComma(@TempDir Path tempDir) throws Exception {
            Path demoData = tempDir.resolve("demo-data");
            Files.createDirectories(demoData);
            Files.writeString(demoData.resolve("comma.csv"),
                "a,b\n1,2\n", StandardCharsets.UTF_8);

            String originalUserDir = System.getProperty("user.dir");
            System.setProperty("user.dir", tempDir.toAbsolutePath().toString());
            try {
                Map<String, Object> params = new HashMap<>();
                params.put("file", "comma.csv");
                params.put("delimiter", null);   // explicit null

                OperationResult result = operation.execute(buildContext(params));
                List<Map<String, Object>> rows = result.outputStream()
                    .map(r -> (Map<String, Object>) r)
                    .collect(Collectors.toList());

                assertEquals(1, rows.size());
                assertEquals("2", rows.get(0).get("b"));
            } finally {
                System.setProperty("user.dir", originalUserDir);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // skipRows parameter
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("skipRows parameter")
    class SkipRowsTests {

        @Test
        @DisplayName("Should skip the specified number of rows before reading header")
        void execute_SkipOneRow_SkipsFirstRow(@TempDir Path tempDir) throws Exception {
            Path demoData = tempDir.resolve("demo-data");
            Files.createDirectories(demoData);
            // Row 0: metadata comment, Row 1: real header, Row 2: data
            Files.writeString(demoData.resolve("skip.csv"),
                "# metadata\nname,value\nFoo,1\n", StandardCharsets.UTF_8);

            String originalUserDir = System.getProperty("user.dir");
            System.setProperty("user.dir", tempDir.toAbsolutePath().toString());
            try {
                Map<String, Object> params = new HashMap<>();
                params.put("file", "skip.csv");
                params.put("skipRows", 1);

                OperationResult result = operation.execute(buildContext(params));
                List<Map<String, Object>> rows = result.outputStream()
                    .map(r -> (Map<String, Object>) r)
                    .collect(Collectors.toList());

                assertEquals(1, rows.size());
                assertEquals("Foo", rows.get(0).get("name"),
                    "After skipping 1 row the real header should be picked up");
            } finally {
                System.setProperty("user.dir", originalUserDir);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Callback invocation
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Callback invocation")
    class CallbackTests {

        @Test
        @DisplayName("Should invoke callback.onLog when file is successfully opened")
        void execute_WithCallback_LogsStartMessage(@TempDir Path tempDir) throws Exception {
            Path demoData = tempDir.resolve("demo-data");
            Files.createDirectories(demoData);
            Files.writeString(demoData.resolve("cb.csv"), "col\nval\n", StandardCharsets.UTF_8);

            String originalUserDir = System.getProperty("user.dir");
            System.setProperty("user.dir", tempDir.toAbsolutePath().toString());

            // Capture log calls
            java.util.List<String> logMessages = new java.util.ArrayList<>();
            Operation.OperationCallback capturingCallback = new Operation.OperationCallback() {
                @Override public void onProgress(long p, long t) {}
                @Override public void onLog(String level, String message) {
                    logMessages.add(message);
                }
                @Override public void onMetric(String name, Object value) {}
            };

            try {
                OperationContext ctx = new OperationContext(
                    Map.of("file", "cb.csv"), null, null, Map.of(), capturingCallback);
                OperationResult result = operation.execute(ctx);
                // Consume the stream to trigger execution
                result.outputStream().collect(Collectors.toList());

                assertFalse(logMessages.isEmpty(),
                    "Callback should have received at least one log message");
            } finally {
                System.setProperty("user.dir", originalUserDir);
            }
        }

        @Test
        @DisplayName("Should not throw when callback is null")
        void execute_NullCallback_DoesNotThrow(@TempDir Path tempDir) throws Exception {
            Path demoData = tempDir.resolve("demo-data");
            Files.createDirectories(demoData);
            Files.writeString(demoData.resolve("nocb.csv"), "col\nval\n", StandardCharsets.UTF_8);

            String originalUserDir = System.getProperty("user.dir");
            System.setProperty("user.dir", tempDir.toAbsolutePath().toString());
            try {
                OperationContext ctx = new OperationContext(
                    Map.of("file", "nocb.csv"), null, null, Map.of(), null);

                assertDoesNotThrow(() -> {
                    OperationResult result = operation.execute(ctx);
                    result.outputStream().collect(Collectors.toList());
                });
            } finally {
                System.setProperty("user.dir", originalUserDir);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static OperationContext buildContext(Map<String, Object> params) {
        return new OperationContext(params, null, null, Map.of(), NOOP_CALLBACK);
    }
}
