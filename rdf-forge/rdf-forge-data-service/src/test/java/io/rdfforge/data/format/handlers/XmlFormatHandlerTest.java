package io.rdfforge.data.format.handlers;

import io.rdfforge.data.format.DataFormatHandler.PreviewResult;
import io.rdfforge.data.format.DataFormatInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("XmlFormatHandler")
class XmlFormatHandlerTest {

    private final XmlFormatHandler handler = new XmlFormatHandler();

    @Test
    @DisplayName("advertises .xml extension and application/xml MIME type")
    void formatInfo_advertisesExtensionAndMime() {
        DataFormatInfo info = handler.getFormatInfo();
        assertThat(info.format()).isEqualTo("xml");
        assertThat(info.fileExtensions()).contains("xml");
        assertThat(handler.supportsExtension("xml")).isTrue();
        assertThat(handler.supportsExtension("XML")).isTrue();
        assertThat(handler.supportsExtension("csv")).isFalse();
        assertThat(handler.supportsMimeType("application/xml")).isTrue();
        assertThat(handler.supportsMimeType("text/xml")).isTrue();
        assertThat(info.available()).isTrue();
    }

    @Test
    @DisplayName("preview returns rows with flattened children and attributes")
    void preview_flatXml_returnsRows() {
        String xml = "<people>"
                + "<person id=\"1\"><name>Alice</name><age>30</age></person>"
                + "<person id=\"2\"><name>Bob</name><age>25</age></person>"
                + "</people>";
        PreviewResult result = handler.preview(
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)),
                Map.of(),
                10);

        assertThat(result.rows()).hasSize(2);
        assertThat(result.columns()).contains("@id", "name", "age");
        assertThat(result.rows().get(0))
                .containsEntry("@id", "1")
                .containsEntry("name", "Alice")
                .containsEntry("age", "30");
        assertThat(result.totalRows()).isEqualTo(2L);
    }

    @Test
    @DisplayName("readIterator streams records lazily")
    void readIterator_streamsRows() {
        String xml = "<rows>"
                + "<row><a>1</a></row>"
                + "<row><a>2</a></row>"
                + "<row><a>3</a></row>"
                + "</rows>";
        Iterator<Map<String, Object>> it = handler.readIterator(
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)),
                Map.of());

        List<String> values = new java.util.ArrayList<>();
        while (it.hasNext()) values.add((String) it.next().get("a"));
        assertThat(values).containsExactly("1", "2", "3");
    }

    // ---------------------------------------------------------------------
    // XXE hardening tests — these are the NON-NEGOTIABLE security contract.
    // ---------------------------------------------------------------------

    @Test
    @DisplayName("rejects external entity references (XXE via DOCTYPE is blocked)")
    void preview_xxeDoctype_doesNotExpandEntity(@org.junit.jupiter.api.io.TempDir java.nio.file.Path temp) throws Exception {
        // Create a "secret" file the attacker would like to read.
        File secret = temp.resolve("secret.txt").toFile();
        Files.writeString(secret.toPath(), "TOPSECRET-PAYLOAD");

        // Classic XXE payload pointing to the local file.
        String xxe = "<?xml version=\"1.0\"?>"
                + "<!DOCTYPE foo [ <!ENTITY xxe SYSTEM \"" + secret.toURI() + "\"> ]>"
                + "<people><person><name>&xxe;</name></person></people>";

        // The handler MUST either throw (DTDs disabled) or return the literal
        // entity reference without expanding it. It must NEVER return the
        // contents of the secret file.
        try {
            PreviewResult result = handler.preview(
                    new ByteArrayInputStream(xxe.getBytes(StandardCharsets.UTF_8)),
                    Map.of(),
                    10);
            // If preview succeeded, verify no row value leaked the secret.
            for (Map<String, Object> row : result.rows()) {
                for (Object v : row.values()) {
                    assertThat(String.valueOf(v)).doesNotContain("TOPSECRET-PAYLOAD");
                }
            }
        } catch (RuntimeException expected) {
            // Also acceptable — Stax throws because DTDs are disabled.
            assertThat(expected.getMessage()).doesNotContain("TOPSECRET-PAYLOAD");
        }
    }

    @Test
    @DisplayName("rejects billion-laughs style entity expansion")
    void preview_billionLaughs_doesNotExpand() {
        String bomb = "<?xml version=\"1.0\"?>"
                + "<!DOCTYPE lolz ["
                + "<!ENTITY lol \"lol\">"
                + "<!ENTITY lol2 \"&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;&lol;\">"
                + "<!ENTITY lol3 \"&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;&lol2;\">"
                + "]>"
                + "<rows><row><value>&lol3;</value></row></rows>";

        // Either throws (preferred) or returns a row whose value does not
        // contain 1000 copies of "lol".
        assertThatCode(() -> {
            try {
                PreviewResult result = handler.preview(
                        new ByteArrayInputStream(bomb.getBytes(StandardCharsets.UTF_8)),
                        Map.of(),
                        10);
                for (Map<String, Object> row : result.rows()) {
                    Object v = row.get("value");
                    // With DTDs disabled the entity should be empty / unresolved.
                    assertThat(String.valueOf(v).length()).isLessThan(100);
                }
            } catch (RuntimeException ignored) {
                // Acceptable — Stax bailed out on the DTD.
            }
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("unavailable Parquet handler refuses to process inputs")
    void parquetStub_throwsOnAnyOperation() {
        ParquetFormatHandler parquet = new ParquetFormatHandler();
        assertThat(parquet.getFormatInfo().available()).isFalse();
        assertThat(parquet.getFormatInfo().unavailableReason()).isNotBlank();
        assertThat(parquet.supportsExtension("parquet")).isFalse();
        assertThatThrownBy(() ->
                parquet.preview(new ByteArrayInputStream(new byte[0]), Map.of(), 1))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
