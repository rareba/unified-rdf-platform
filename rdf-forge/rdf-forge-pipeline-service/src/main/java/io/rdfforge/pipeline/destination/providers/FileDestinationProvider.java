package io.rdfforge.pipeline.destination.providers;

import io.rdfforge.pipeline.destination.DestinationInfo;
import io.rdfforge.pipeline.destination.DestinationInfo.ConfigField;
import io.rdfforge.pipeline.destination.DestinationProvider;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Destination provider for exporting RDF data to local files.
 *
 * Supports various RDF serialization formats including Turtle, JSON-LD,
 * N-Triples, RDF/XML, and more.
 */
@Component
@Slf4j
public class FileDestinationProvider implements DestinationProvider {

    private static final String TYPE = "file";

    private static final Map<String, RDFFormat> FORMAT_MAP = Map.of(
        "turtle", RDFFormat.TURTLE_PRETTY,
        "ttl", RDFFormat.TURTLE_PRETTY,
        "json-ld", RDFFormat.JSONLD_PRETTY,
        "jsonld", RDFFormat.JSONLD_PRETTY,
        "n-triples", RDFFormat.NTRIPLES,
        "nt", RDFFormat.NTRIPLES,
        "rdf/xml", RDFFormat.RDFXML_PRETTY,
        "rdfxml", RDFFormat.RDFXML_PRETTY,
        "xml", RDFFormat.RDFXML_PRETTY,
        "trig", RDFFormat.TRIG_PRETTY
    );

    private static final Map<String, String> FORMAT_EXTENSIONS = Map.of(
        "turtle", ".ttl",
        "json-ld", ".jsonld",
        "n-triples", ".nt",
        "rdf/xml", ".rdf",
        "trig", ".trig",
        "n-quads", ".nq"
    );

    @Override
    public DestinationInfo getDestinationInfo() {
        Map<String, ConfigField> configFields = new LinkedHashMap<>();

        configFields.put("directory", new ConfigField(
            "directory",
            "Output Directory",
            "string",
            "The directory path where files will be saved",
            true
        ));

        configFields.put("filename", new ConfigField(
            "filename",
            "Filename",
            "string",
            "The output filename (without extension). Supports templates: {timestamp}, {date}",
            false,
            "output-{timestamp}"
        ));

        configFields.put("format", new ConfigField(
            "format",
            "Output Format",
            "select",
            "The RDF serialization format",
            true,
            "turtle",
            List.of("turtle", "json-ld", "n-triples", "rdf/xml", "trig"),
            false
        ));

        configFields.put("overwrite", new ConfigField(
            "overwrite",
            "Overwrite Existing",
            "boolean",
            "Whether to overwrite existing files",
            false,
            false
        ));

        configFields.put("createDirectory", new ConfigField(
            "createDirectory",
            "Create Directory",
            "boolean",
            "Create the output directory if it doesn't exist",
            false,
            true
        ));

        configFields.put("compress", new ConfigField(
            "compress",
            "Compress Output",
            "select",
            "Compression format for the output file",
            false,
            "none",
            List.of("none", "gzip"),
            false
        ));

        return new DestinationInfo(
            TYPE,
            "Local File",
            "Export RDF data to local filesystem in various serialization formats",
            DestinationInfo.CATEGORY_FILE,
            configFields,
            List.of(
                DestinationInfo.CAPABILITY_REPLACE,
                DestinationInfo.CAPABILITY_APPEND
            ),
            List.of("turtle", "json-ld", "n-triples", "rdf/xml", "trig")
        );
    }

    @Override
    public PublishResult publish(Model model, Map<String, Object> config) throws IOException {
        String directory = (String) config.get("directory");
        String filename = (String) config.getOrDefault("filename", "output-{timestamp}");
        String format = (String) config.getOrDefault("format", "turtle");
        boolean overwrite = Boolean.TRUE.equals(config.get("overwrite"));
        boolean createDirectory = config.get("createDirectory") == null || Boolean.TRUE.equals(config.get("createDirectory"));
        String compress = (String) config.getOrDefault("compress", "none");

        // Validate directory
        if (directory == null || directory.isBlank()) {
            return PublishResult.failure("Output directory is required");
        }

        try {
            Path dirPath = Paths.get(directory);

            // Create directory if needed
            if (!Files.exists(dirPath)) {
                if (createDirectory) {
                    Files.createDirectories(dirPath);
                    log.info("Created output directory: {}", dirPath);
                } else {
                    return PublishResult.failure("Output directory does not exist: " + directory);
                }
            }

            // Process filename template
            String processedFilename = processFilenameTemplate(filename);

            // Add extension
            String extension = FORMAT_EXTENSIONS.getOrDefault(format.toLowerCase(), ".ttl");
            if ("gzip".equals(compress)) {
                extension += ".gz";
            }
            String fullFilename = processedFilename + extension;

            Path outputPath = dirPath.resolve(fullFilename);

            // Check overwrite
            if (Files.exists(outputPath) && !overwrite) {
                return PublishResult.failure("File already exists and overwrite is disabled: " + outputPath);
            }

            // Get RDF format
            RDFFormat rdfFormat = FORMAT_MAP.getOrDefault(format.toLowerCase(), RDFFormat.TURTLE_PRETTY);

            // Write the file
            long tripleCount = model.size();

            try (OutputStream os = createOutputStream(outputPath, compress)) {
                RDFDataMgr.write(os, model, rdfFormat);
            }

            long fileSize = Files.size(outputPath);

            log.info("Exported {} triples to {} ({} bytes)", tripleCount, outputPath, fileSize);

            return PublishResult.success(tripleCount, null, Map.of(
                "path", outputPath.toString(),
                "format", format,
                "fileSize", fileSize,
                "compressed", !"none".equals(compress)
            ));

        } catch (Exception e) {
            log.error("Failed to export to file: {}", e.getMessage(), e);
            return PublishResult.failure("Failed to export: " + e.getMessage());
        }
    }

    private String processFilenameTemplate(String template) {
        LocalDateTime now = LocalDateTime.now();
        return template
            .replace("{timestamp}", now.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")))
            .replace("{date}", now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
            .replace("{time}", now.format(DateTimeFormatter.ofPattern("HH-mm-ss")));
    }

    private OutputStream createOutputStream(Path path, String compress) throws IOException {
        OutputStream os = Files.newOutputStream(path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        if ("gzip".equals(compress)) {
            os = new java.util.zip.GZIPOutputStream(os);
        }
        return os;
    }

    @Override
    public void clearGraph(String graphUri, Map<String, Object> config) throws IOException {
        // For file destination, clearing means deleting the file
        String directory = (String) config.get("directory");
        String filename = (String) config.get("filename");

        if (directory != null && filename != null) {
            Path filePath = Paths.get(directory, filename);
            if (Files.exists(filePath)) {
                Files.delete(filePath);
                log.info("Deleted file: {}", filePath);
            }
        }
    }

    @Override
    public boolean isAvailable(Map<String, Object> config) {
        String directory = (String) config.get("directory");
        if (directory == null || directory.isBlank()) {
            return false;
        }

        Path dirPath = Paths.get(directory);

        // Check if directory exists or can be created
        if (Files.exists(dirPath)) {
            return Files.isWritable(dirPath);
        }

        // Check if parent exists and is writable
        Path parent = dirPath.getParent();
        return parent != null && Files.exists(parent) && Files.isWritable(parent);
    }

    @Override
    public ValidationResult validateConfig(Map<String, Object> config) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        String directory = (String) config.get("directory");
        if (directory == null || directory.isBlank()) {
            errors.add("Output directory is required");
        } else {
            Path dirPath = Paths.get(directory);
            if (!Files.exists(dirPath)) {
                Boolean createDir = (Boolean) config.get("createDirectory");
                if (Boolean.FALSE.equals(createDir)) {
                    errors.add("Output directory does not exist: " + directory);
                } else {
                    warnings.add("Output directory will be created: " + directory);
                }
            } else if (!Files.isWritable(dirPath)) {
                errors.add("Output directory is not writable: " + directory);
            }
        }

        String format = (String) config.get("format");
        if (format != null && !FORMAT_MAP.containsKey(format.toLowerCase())) {
            errors.add("Unsupported format: " + format + ". Supported: " + FORMAT_MAP.keySet());
        }

        String compress = (String) config.get("compress");
        if (compress != null && !List.of("none", "gzip").contains(compress.toLowerCase())) {
            errors.add("Unsupported compression: " + compress + ". Supported: none, gzip");
        }

        if (errors.isEmpty()) {
            return ValidationResult.valid();
        }
        return new ValidationResult(false, errors, warnings);
    }
}
