package io.rdfforge.data.format.handlers;

import io.rdfforge.data.format.DataFormatHandler;
import io.rdfforge.data.format.DataFormatInfo;
import io.rdfforge.data.format.DataFormatInfo.FormatOption;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;

import static io.rdfforge.data.format.DataFormatInfo.*;

/**
 * Handler for Excel formats (XLSX and XLS).
 *
 * Supports:
 * - Excel 2007+ (.xlsx) - default
 * - Legacy Excel (.xls)
 * - Multiple sheets
 * - Header row detection
 * - Date/number formatting
 */
@Component
public class ExcelFormatHandler implements DataFormatHandler {

    private static final DataFormatInfo INFO = new DataFormatInfo(
        "excel",
        "Excel (Microsoft Excel)",
        "Spreadsheet format with multiple sheets, formulas, and rich formatting.",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        List.of("xlsx", "xls"),
        true,
        true,
        false, // Excel doesn't support true streaming
        Map.of(
            "sheetName", new FormatOption("sheetName", "Sheet Name", "string",
                "Name of the sheet to read (default: first sheet)", ""),
            "sheetIndex", new FormatOption("sheetIndex", "Sheet Index", "number",
                "Index of the sheet to read (0-based, default: 0)", 0),
            "hasHeader", new FormatOption("hasHeader", "Has Header Row", "boolean",
                "First row contains column names", true),
            "skipRows", new FormatOption("skipRows", "Skip Rows", "number",
                "Number of rows to skip at the beginning", 0),
            "dateFormat", new FormatOption("dateFormat", "Date Format", "string",
                "Format pattern for date values", "yyyy-MM-dd"),
            "trimWhitespace", new FormatOption("trimWhitespace", "Trim Whitespace", "boolean",
                "Remove leading/trailing whitespace from values", true)
        ),
        List.of(
            CAPABILITY_READ,
            CAPABILITY_WRITE,
            CAPABILITY_ANALYZE,
            CAPABILITY_PREVIEW,
            CAPABILITY_SCHEMA_INFERENCE,
            CAPABILITY_TYPE_DETECTION
        )
    );

    @Override
    public DataFormatInfo getFormatInfo() {
        return INFO;
    }

    @Override
    public boolean supportsExtension(String extension) {
        return extension != null && List.of("xlsx", "xls").contains(extension.toLowerCase());
    }

    @Override
    public boolean supportsMimeType(String mimeType) {
        return mimeType != null && (
            mimeType.equalsIgnoreCase("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet") ||
            mimeType.equalsIgnoreCase("application/vnd.ms-excel")
        );
    }

    @Override
    public PreviewResult preview(InputStream input, Map<String, Object> options, int maxRows) {
        try (Workbook workbook = WorkbookFactory.create(input)) {
            Sheet sheet = getSheet(workbook, options);
            boolean hasHeader = getOption(options, "hasHeader", true);
            int skipRows = getOption(options, "skipRows", 0);

            int startRow = skipRows;
            int rowCount = sheet.getLastRowNum() - startRow + 1;

            // Get header row
            Row headerRow = sheet.getRow(startRow);
            if (headerRow == null) {
                return new PreviewResult(List.of(), List.of(), 0, false);
            }

            List<String> columns;
            int dataStartRow;

            if (hasHeader) {
                columns = extractColumnNames(headerRow, options);
                dataStartRow = startRow + 1;
            } else {
                int numCols = headerRow.getLastCellNum();
                columns = generateColumnNames(numCols);
                dataStartRow = startRow;
            }

            // Read preview rows
            List<Map<String, Object>> rows = new ArrayList<>();
            int count = 0;

            for (int i = dataStartRow; i <= sheet.getLastRowNum() && count < maxRows; i++) {
                Row row = sheet.getRow(i);
                if (row != null) {
                    rows.add(rowToMap(row, columns, options));
                    count++;
                }
            }

            long totalRows = sheet.getLastRowNum() - dataStartRow + 1;
            boolean hasMore = totalRows > maxRows;

            return new PreviewResult(columns, rows, totalRows, hasMore);

        } catch (Exception e) {
            throw new RuntimeException("Failed to preview Excel: " + e.getMessage(), e);
        }
    }

    @Override
    public AnalysisResult analyze(InputStream input, Map<String, Object> options) {
        try (Workbook workbook = WorkbookFactory.create(input)) {
            Sheet sheet = getSheet(workbook, options);
            boolean hasHeader = getOption(options, "hasHeader", true);
            int skipRows = getOption(options, "skipRows", 0);

            int startRow = skipRows;
            Row headerRow = sheet.getRow(startRow);

            if (headerRow == null) {
                return new AnalysisResult(List.of(), 0, Map.of());
            }

            List<String> columns;
            int dataStartRow;

            if (hasHeader) {
                columns = extractColumnNames(headerRow, options);
                dataStartRow = startRow + 1;
            } else {
                int numCols = headerRow.getLastCellNum();
                columns = generateColumnNames(numCols);
                dataStartRow = startRow;
            }

            // Initialize analysis structures
            int numColumns = columns.size();
            List<List<Object>> sampleValues = new ArrayList<>();
            long[] nullCounts = new long[numColumns];
            Set<Object>[] uniqueValues = new HashSet[numColumns];
            String[] detectedTypes = new String[numColumns];

            for (int i = 0; i < numColumns; i++) {
                sampleValues.add(new ArrayList<>());
                uniqueValues[i] = new HashSet<>();
                detectedTypes[i] = null;
            }

            // Process all rows
            long totalRows = 0;
            for (int i = dataStartRow; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row != null) {
                    processRowForAnalysis(row, columns, sampleValues, nullCounts, uniqueValues, detectedTypes, options);
                    totalRows++;
                }
            }

            // Build column info
            List<ColumnInfo> columnInfos = new ArrayList<>();
            for (int i = 0; i < numColumns; i++) {
                columnInfos.add(new ColumnInfo(
                    columns.get(i),
                    detectedTypes[i] != null ? detectedTypes[i] : "string",
                    nullCounts[i],
                    uniqueValues[i].size(),
                    new ArrayList<>(sampleValues.get(i)),
                    Map.of()
                ));
            }

            // Get sheet names for metadata
            List<String> sheetNames = new ArrayList<>();
            for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
                sheetNames.add(workbook.getSheetName(i));
            }

            return new AnalysisResult(columnInfos, totalRows, Map.of(
                "sheetCount", workbook.getNumberOfSheets(),
                "sheetNames", sheetNames,
                "activeSheet", sheet.getSheetName()
            ));

        } catch (Exception e) {
            throw new RuntimeException("Failed to analyze Excel: " + e.getMessage(), e);
        }
    }

    @Override
    public Iterator<Map<String, Object>> readIterator(InputStream input, Map<String, Object> options) {
        try {
            Workbook workbook = WorkbookFactory.create(input);
            Sheet sheet = getSheet(workbook, options);
            boolean hasHeader = getOption(options, "hasHeader", true);
            int skipRows = getOption(options, "skipRows", 0);

            int startRow = skipRows;
            Row headerRow = sheet.getRow(startRow);

            List<String> columns;
            int dataStartRow;

            if (hasHeader && headerRow != null) {
                columns = extractColumnNames(headerRow, options);
                dataStartRow = startRow + 1;
            } else {
                int numCols = headerRow != null ? headerRow.getLastCellNum() : 0;
                columns = generateColumnNames(numCols);
                dataStartRow = startRow;
            }

            final List<String> finalColumns = columns;
            final int finalDataStartRow = dataStartRow;

            return new Iterator<>() {
                private int currentRow = finalDataStartRow;

                @Override
                public boolean hasNext() {
                    return currentRow <= sheet.getLastRowNum();
                }

                @Override
                public Map<String, Object> next() {
                    if (!hasNext()) throw new NoSuchElementException();
                    Row row = sheet.getRow(currentRow++);
                    if (row == null) {
                        return createEmptyRow(finalColumns);
                    }
                    return rowToMap(row, finalColumns, options);
                }
            };

        } catch (Exception e) {
            throw new RuntimeException("Failed to read Excel: " + e.getMessage(), e);
        }
    }

    @Override
    public void write(List<Map<String, Object>> data, List<String> columns, OutputStream output, Map<String, Object> options) {
        try (Workbook workbook = new XSSFWorkbook()) {
            String sheetName = getOption(options, "sheetName", "Sheet1");
            if (sheetName == null || sheetName.isEmpty()) {
                sheetName = "Sheet1";
            }

            Sheet sheet = workbook.createSheet(sheetName);

            // Create header row
            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < columns.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(columns.get(i));
            }

            // Create data rows
            int rowNum = 1;
            for (Map<String, Object> rowData : data) {
                Row row = sheet.createRow(rowNum++);
                for (int i = 0; i < columns.size(); i++) {
                    Cell cell = row.createCell(i);
                    Object value = rowData.get(columns.get(i));
                    setCellValue(cell, value);
                }
            }

            // Auto-size columns
            for (int i = 0; i < columns.size(); i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(output);

        } catch (Exception e) {
            throw new RuntimeException("Failed to write Excel: " + e.getMessage(), e);
        }
    }

    private Sheet getSheet(Workbook workbook, Map<String, Object> options) {
        String sheetName = getOption(options, "sheetName", "");
        int sheetIndex = getOption(options, "sheetIndex", 0);

        if (sheetName != null && !sheetName.isEmpty()) {
            Sheet sheet = workbook.getSheet(sheetName);
            if (sheet != null) return sheet;
        }

        if (sheetIndex >= 0 && sheetIndex < workbook.getNumberOfSheets()) {
            return workbook.getSheetAt(sheetIndex);
        }

        return workbook.getSheetAt(0);
    }

    private List<String> extractColumnNames(Row headerRow, Map<String, Object> options) {
        boolean trimWhitespace = getOption(options, "trimWhitespace", true);
        List<String> columns = new ArrayList<>();

        for (int i = 0; i < headerRow.getLastCellNum(); i++) {
            Cell cell = headerRow.getCell(i);
            String name = getCellStringValue(cell);
            if (name == null || name.isEmpty()) {
                name = "column_" + (i + 1);
            } else if (trimWhitespace) {
                name = name.trim();
            }
            columns.add(name);
        }

        return columns;
    }

    private List<String> generateColumnNames(int count) {
        List<String> columns = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            columns.add("column_" + (i + 1));
        }
        return columns;
    }

    private Map<String, Object> rowToMap(Row row, List<String> columns, Map<String, Object> options) {
        boolean trimWhitespace = getOption(options, "trimWhitespace", true);
        Map<String, Object> map = new LinkedHashMap<>();

        for (int i = 0; i < columns.size(); i++) {
            Cell cell = row.getCell(i);
            Object value = getCellValue(cell);

            if (value instanceof String && trimWhitespace) {
                value = ((String) value).trim();
            }

            map.put(columns.get(i), value);
        }

        return map;
    }

    private Map<String, Object> createEmptyRow(List<String> columns) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (String col : columns) {
            map.put(col, null);
        }
        return map;
    }

    private Object getCellValue(Cell cell) {
        if (cell == null) return null;

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getLocalDateTimeCellValue();
                }
                double numValue = cell.getNumericCellValue();
                if (numValue == Math.floor(numValue) && !Double.isInfinite(numValue)) {
                    return (long) numValue;
                }
                return numValue;
            case BOOLEAN:
                return cell.getBooleanCellValue();
            case FORMULA:
                try {
                    return cell.getNumericCellValue();
                } catch (Exception e) {
                    return cell.getStringCellValue();
                }
            case BLANK:
                return null;
            default:
                return cell.toString();
        }
    }

    private String getCellStringValue(Cell cell) {
        if (cell == null) return null;

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                if (DateUtil.isCellDateFormatted(cell)) {
                    return cell.getLocalDateTimeCellValue().toString();
                }
                double numValue = cell.getNumericCellValue();
                if (numValue == Math.floor(numValue) && !Double.isInfinite(numValue)) {
                    return String.valueOf((long) numValue);
                }
                return String.valueOf(numValue);
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return String.valueOf(cell.getNumericCellValue());
                } catch (Exception e) {
                    return cell.getStringCellValue();
                }
            default:
                return cell.toString();
        }
    }

    private void setCellValue(Cell cell, Object value) {
        if (value == null) {
            cell.setBlank();
        } else if (value instanceof Number) {
            cell.setCellValue(((Number) value).doubleValue());
        } else if (value instanceof Boolean) {
            cell.setCellValue((Boolean) value);
        } else if (value instanceof java.time.LocalDateTime) {
            cell.setCellValue((java.time.LocalDateTime) value);
        } else if (value instanceof java.time.LocalDate) {
            cell.setCellValue((java.time.LocalDate) value);
        } else if (value instanceof java.util.Date) {
            cell.setCellValue((java.util.Date) value);
        } else {
            cell.setCellValue(value.toString());
        }
    }

    private void processRowForAnalysis(Row row, List<String> columns,
                                       List<List<Object>> sampleValues, long[] nullCounts,
                                       Set<Object>[] uniqueValues, String[] detectedTypes,
                                       Map<String, Object> options) {
        for (int i = 0; i < columns.size(); i++) {
            Cell cell = row.getCell(i);
            Object value = getCellValue(cell);

            if (value == null) {
                nullCounts[i]++;
            } else {
                uniqueValues[i].add(value);

                if (sampleValues.get(i).size() < 5) {
                    sampleValues.get(i).add(value);
                }

                String type = detectType(value);
                if (detectedTypes[i] == null) {
                    detectedTypes[i] = type;
                } else if (!detectedTypes[i].equals(type)) {
                    detectedTypes[i] = "string";
                }
            }
        }
    }

    private String detectType(Object value) {
        if (value instanceof Long || value instanceof Integer) return "integer";
        if (value instanceof Number) return "decimal";
        if (value instanceof Boolean) return "boolean";
        if (value instanceof java.time.LocalDateTime || value instanceof java.time.LocalDate) return "datetime";
        return "string";
    }

    @SuppressWarnings("unchecked")
    private <T> T getOption(Map<String, Object> options, String key, T defaultValue) {
        if (options == null || !options.containsKey(key)) {
            return defaultValue;
        }
        Object value = options.get(key);
        if (value == null) return defaultValue;

        if (defaultValue instanceof Boolean && value instanceof String) {
            return (T) Boolean.valueOf((String) value);
        }
        if (defaultValue instanceof Integer && value instanceof String) {
            return (T) Integer.valueOf((String) value);
        }

        return (T) value;
    }
}
