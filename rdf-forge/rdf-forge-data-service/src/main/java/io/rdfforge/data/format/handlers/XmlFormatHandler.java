package io.rdfforge.data.format.handlers;

import io.rdfforge.data.format.DataFormatHandler;
import io.rdfforge.data.format.DataFormatInfo;
import io.rdfforge.data.format.DataFormatInfo.FormatOption;
import org.springframework.stereotype.Component;

import javax.xml.XMLConstants;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.*;
import java.util.*;

import static io.rdfforge.data.format.DataFormatInfo.*;

/**
 * Handler for generic XML data documents.
 *
 * <p>Supports reading record-oriented XML where a repeated element represents a row
 * (e.g. {@code <records><record>...</record></records>}). Attributes and child text
 * values of the row element are flattened into columns (attributes prefixed with
 * {@code @}).</p>
 *
 * <h3>Security</h3>
 * This handler is hardened against XXE (XML External Entity) attacks:
 * <ul>
 *     <li>DTD support is disabled ({@code XMLInputFactory.SUPPORT_DTD=false})</li>
 *     <li>External entities are disabled
 *         ({@code javax.xml.stream.isSupportingExternalEntities=false})</li>
 *     <li>Secure processing is enforced
 *         ({@code XMLConstants.FEATURE_SECURE_PROCESSING=true})</li>
 * </ul>
 * These settings are mandatory; the factory is created through
 * {@link #createSafeFactory()} which centralizes the security configuration.
 */
@Component
public class XmlFormatHandler implements DataFormatHandler {

    private static final DataFormatInfo INFO = new DataFormatInfo(
        "xml",
        "XML (Extensible Markup Language)",
        "Generic XML documents. Repeated child elements are treated as records; "
            + "attributes and text nodes become columns.",
        "application/xml",
        List.of("xml"),
        true,
        true,
        true,
        Map.of(
            "recordElement", new FormatOption("recordElement", "Record Element", "string",
                "Name of the repeated element that represents one row. "
                    + "Auto-detected if blank.", ""),
            "includeAttributes", new FormatOption("includeAttributes", "Include Attributes",
                "boolean", "Include XML attributes as columns (prefixed with @)", true),
            "trimWhitespace", new FormatOption("trimWhitespace", "Trim Whitespace", "boolean",
                "Remove leading/trailing whitespace from text values", true)
        ),
        List.of(
            CAPABILITY_READ,
            CAPABILITY_ANALYZE,
            CAPABILITY_PREVIEW,
            CAPABILITY_STREAMING,
            CAPABILITY_SCHEMA_INFERENCE
        ),
        true,
        null
    );

    @Override
    public DataFormatInfo getFormatInfo() {
        return INFO;
    }

    @Override
    public boolean supportsExtension(String extension) {
        return extension != null && "xml".equalsIgnoreCase(extension);
    }

    @Override
    public boolean supportsMimeType(String mimeType) {
        return mimeType != null && (
            mimeType.equalsIgnoreCase("application/xml") ||
            mimeType.equalsIgnoreCase("text/xml"));
    }

    /**
     * Build a hardened {@link XMLInputFactory}. All callers MUST go through this
     * method — do not create XMLInputFactory instances directly.
     */
    static XMLInputFactory createSafeFactory() {
        XMLInputFactory factory = XMLInputFactory.newInstance();
        // Disable DTDs entirely — prevents billion-laughs / DOCTYPE-based attacks.
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
        // Disable external entity resolution — prevents classic XXE file exfiltration.
        factory.setProperty("javax.xml.stream.isSupportingExternalEntities", Boolean.FALSE);
        // Belt-and-braces: enforce the JAXP secure processing feature.
        try {
            factory.setProperty(XMLConstants.FEATURE_SECURE_PROCESSING, Boolean.TRUE);
        } catch (IllegalArgumentException ignored) {
            // Not all Stax implementations expose this property; the DTD/entity
            // settings above are the primary defense.
        }
        return factory;
    }

    @Override
    public PreviewResult preview(InputStream input, Map<String, Object> options, int maxRows) {
        String desiredRecord = emptyToNull(getOption(options, "recordElement", ""));
        boolean includeAttributes = getOption(options, "includeAttributes", true);
        boolean trimWhitespace = getOption(options, "trimWhitespace", true);

        try {
            XMLInputFactory factory = createSafeFactory();
            XMLStreamReader reader = factory.createXMLStreamReader(input);
            try {
                List<Map<String, Object>> rows = new ArrayList<>();
                Set<String> columns = new LinkedHashSet<>();
                String recordElement = desiredRecord;
                long totalRows = 0;
                boolean hasMore = false;
                boolean skippedOuter = false;

                while (reader.hasNext()) {
                    int event = reader.next();
                    if (event != XMLStreamConstants.START_ELEMENT) {
                        continue;
                    }
                    if (!skippedOuter && recordElement == null) {
                        // Skip the outer document element; the next
                        // START_ELEMENT under it is the repeated record.
                        skippedOuter = true;
                        continue;
                    }
                    if (recordElement == null) {
                        recordElement = reader.getLocalName();
                    }
                    if (!recordElement.equals(reader.getLocalName())) {
                        continue;
                    }
                    Map<String, Object> row = readRecord(reader, includeAttributes, trimWhitespace);
                    columns.addAll(row.keySet());
                    if (rows.size() < maxRows) {
                        rows.add(row);
                    } else {
                        hasMore = true;
                    }
                    totalRows++;
                }
                return new PreviewResult(new ArrayList<>(columns), rows, totalRows, hasMore);
            } finally {
                reader.close();
            }
        } catch (XMLStreamException e) {
            throw new RuntimeException("Failed to preview XML: " + e.getMessage(), e);
        }
    }

    @Override
    public AnalysisResult analyze(InputStream input, Map<String, Object> options) {
        PreviewResult preview = preview(input, options, Integer.MAX_VALUE);
        List<ColumnInfo> columns = new ArrayList<>();
        for (String col : preview.columns()) {
            long nullCount = 0;
            Set<Object> unique = new HashSet<>();
            List<Object> samples = new ArrayList<>();
            for (Map<String, Object> row : preview.rows()) {
                Object v = row.get(col);
                if (v == null || (v instanceof String s && s.isEmpty())) {
                    nullCount++;
                } else {
                    unique.add(v);
                    if (samples.size() < 5) samples.add(v);
                }
            }
            columns.add(new ColumnInfo(col, "string", nullCount, unique.size(), samples, Map.of()));
        }
        return new AnalysisResult(columns, preview.totalRows(), Map.of("format", "xml"));
    }

    @Override
    public Iterator<Map<String, Object>> readIterator(InputStream input, Map<String, Object> options) {
        String desiredRecord = emptyToNull(getOption(options, "recordElement", ""));
        boolean includeAttributes = getOption(options, "includeAttributes", true);
        boolean trimWhitespace = getOption(options, "trimWhitespace", true);

        try {
            XMLInputFactory factory = createSafeFactory();
            XMLStreamReader reader = factory.createXMLStreamReader(input);

            return new Iterator<>() {
                String recordElement = desiredRecord;
                Map<String, Object> nextRow;
                boolean closed = false;
                boolean skippedOuter = false;

                @Override
                public boolean hasNext() {
                    if (closed) return false;
                    if (nextRow != null) return true;
                    try {
                        while (reader.hasNext()) {
                            int event = reader.next();
                            if (event != XMLStreamConstants.START_ELEMENT) continue;
                            if (!skippedOuter && recordElement == null) {
                                skippedOuter = true;
                                continue;
                            }
                            if (recordElement == null) {
                                recordElement = reader.getLocalName();
                            }
                            if (!recordElement.equals(reader.getLocalName())) continue;
                            nextRow = readRecord(reader, includeAttributes, trimWhitespace);
                            return true;
                        }
                        close();
                        return false;
                    } catch (XMLStreamException e) {
                        close();
                        throw new RuntimeException("Error reading XML", e);
                    }
                }

                @Override
                public Map<String, Object> next() {
                    if (!hasNext()) throw new NoSuchElementException();
                    Map<String, Object> out = nextRow;
                    nextRow = null;
                    return out;
                }

                private void close() {
                    if (!closed) {
                        closed = true;
                        try { reader.close(); } catch (XMLStreamException ignored) { }
                    }
                }
            };
        } catch (XMLStreamException e) {
            throw new RuntimeException("Failed to read XML: " + e.getMessage(), e);
        }
    }

    @Override
    public void write(List<Map<String, Object>> data, List<String> columns, OutputStream output, Map<String, Object> options) {
        throw new UnsupportedOperationException("Writing XML is not supported yet");
    }

    /**
     * Read the body of a single record element. The reader must be positioned on the
     * {@code START_ELEMENT} event for that record.
     */
    private Map<String, Object> readRecord(XMLStreamReader reader,
                                           boolean includeAttributes,
                                           boolean trimWhitespace) throws XMLStreamException {
        Map<String, Object> row = new LinkedHashMap<>();

        if (includeAttributes) {
            int attrs = reader.getAttributeCount();
            for (int i = 0; i < attrs; i++) {
                row.put("@" + reader.getAttributeLocalName(i), reader.getAttributeValue(i));
            }
        }

        int depth = 1;
        String currentChild = null;
        StringBuilder currentText = new StringBuilder();
        Map<String, Object> childAttrs = new LinkedHashMap<>();

        while (depth > 0 && reader.hasNext()) {
            int event = reader.next();
            switch (event) {
                case XMLStreamConstants.START_ELEMENT -> {
                    depth++;
                    if (depth == 2) {
                        currentChild = reader.getLocalName();
                        currentText.setLength(0);
                        childAttrs.clear();
                        if (includeAttributes) {
                            int attrs = reader.getAttributeCount();
                            for (int i = 0; i < attrs; i++) {
                                childAttrs.put(currentChild + ".@" + reader.getAttributeLocalName(i),
                                        reader.getAttributeValue(i));
                            }
                        }
                    }
                }
                case XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA -> {
                    if (depth == 2 && currentChild != null) {
                        currentText.append(reader.getText());
                    }
                }
                case XMLStreamConstants.END_ELEMENT -> {
                    if (depth == 2 && currentChild != null) {
                        String value = currentText.toString();
                        if (trimWhitespace) value = value.trim();
                        row.put(currentChild, value);
                        row.putAll(childAttrs);
                        currentChild = null;
                    }
                    depth--;
                }
                default -> {
                    // ignore comments, whitespace, entity refs (entities are disabled)
                }
            }
        }

        return row;
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isEmpty()) ? null : s;
    }

    @SuppressWarnings("unchecked")
    private <T> T getOption(Map<String, Object> options, String key, T defaultValue) {
        if (options == null || !options.containsKey(key)) {
            return defaultValue;
        }
        Object value = options.get(key);
        if (value == null) return defaultValue;
        if (defaultValue instanceof Boolean && value instanceof String s) {
            return (T) Boolean.valueOf(s);
        }
        return (T) value;
    }
}
