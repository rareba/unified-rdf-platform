package io.rdfforge.data.format.handlers;

import io.rdfforge.data.format.DataFormatHandler;
import io.rdfforge.data.format.DataFormatInfo;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Placeholder handler for Apache Parquet.
 *
 * <p>Parquet support is NOT implemented. This handler exists only so that the
 * {@link io.rdfforge.data.format.DataFormatRegistry#getAvailableFormats() format
 * registry} advertises the format as "known but unavailable" — preventing the UI
 * from claiming Parquet support that does not exist. All read/write operations
 * throw {@link UnsupportedOperationException} to fail loudly if the gating is
 * bypassed.</p>
 *
 * <p>To implement Parquet support, see the TODO at
 * {@code docs/TODO_PARQUET.md}.</p>
 */
@Component
public class ParquetFormatHandler implements DataFormatHandler {

    private static final String UNAVAILABLE_MSG =
        "Parquet support is not yet implemented — see docs/TODO_PARQUET.md";

    private static final DataFormatInfo INFO = new DataFormatInfo(
        "parquet",
        "Apache Parquet",
        "Columnar storage format used by big-data tooling. Not yet implemented.",
        "application/vnd.apache.parquet",
        List.of("parquet"),
        false,
        false,
        false,
        Map.of(),
        List.of(),
        false,
        UNAVAILABLE_MSG
    );

    @Override
    public DataFormatInfo getFormatInfo() {
        return INFO;
    }

    @Override
    public boolean supportsExtension(String extension) {
        // We advertise the extension in INFO so the registry can still report "known",
        // but we refuse to actually claim it for upload routing.
        return false;
    }

    @Override
    public boolean supportsMimeType(String mimeType) {
        return false;
    }

    @Override
    public PreviewResult preview(InputStream input, Map<String, Object> options, int maxRows) {
        throw new UnsupportedOperationException(UNAVAILABLE_MSG);
    }

    @Override
    public AnalysisResult analyze(InputStream input, Map<String, Object> options) {
        throw new UnsupportedOperationException(UNAVAILABLE_MSG);
    }

    @Override
    public Iterator<Map<String, Object>> readIterator(InputStream input, Map<String, Object> options) {
        throw new UnsupportedOperationException(UNAVAILABLE_MSG);
    }

    @Override
    public void write(List<Map<String, Object>> data, List<String> columns, OutputStream output, Map<String, Object> options) {
        throw new UnsupportedOperationException(UNAVAILABLE_MSG);
    }
}
