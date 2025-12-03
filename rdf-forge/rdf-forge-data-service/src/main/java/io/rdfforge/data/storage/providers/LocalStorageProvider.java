package io.rdfforge.data.storage.providers;

import io.rdfforge.data.storage.StorageProvider;
import io.rdfforge.data.storage.StorageProviderInfo;
import io.rdfforge.data.storage.StorageProviderInfo.ConfigField;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.util.*;

import static io.rdfforge.data.storage.StorageProviderInfo.*;

/**
 * Local filesystem storage provider.
 *
 * This provider stores files on the local filesystem.
 * Useful for development, single-server deployments, or when
 * cloud storage is not needed.
 */
@Component
@Slf4j
public class LocalStorageProvider implements StorageProvider {

    private static final StorageProviderInfo INFO = new StorageProviderInfo(
        "local",
        "Local Filesystem",
        "Store files on the local filesystem. Ideal for development and single-server deployments.",
        "Local",
        "",
        Map.of(
            "basePath", new ConfigField("basePath", "Base Path", "string",
                "Root directory for file storage", true, "./data/storage"),
            "createDirs", new ConfigField("createDirs", "Create Directories", "boolean",
                "Automatically create parent directories", false, true)
        ),
        List.of(
            CAPABILITY_UPLOAD,
            CAPABILITY_DOWNLOAD,
            CAPABILITY_DELETE,
            CAPABILITY_LIST
        )
    );

    @Value("${storage.local.base-path:./data/storage}")
    private String basePath;

    @Value("${storage.local.create-dirs:true}")
    private boolean createDirs;

    private Path rootPath;
    private boolean initialized = false;

    @Override
    public StorageProviderInfo getProviderInfo() {
        return INFO;
    }

    @Override
    public boolean isEnabled() {
        return basePath != null && !basePath.isEmpty();
    }

    @Override
    public Map<String, Object> getConfiguration() {
        Map<String, Object> config = new HashMap<>();
        config.put("basePath", basePath);
        config.put("createDirs", createDirs);
        return config;
    }

    @Override
    public void initialize(Map<String, Object> config) throws IOException {
        this.basePath = (String) config.getOrDefault("basePath", basePath);
        this.createDirs = Boolean.parseBoolean(String.valueOf(config.getOrDefault("createDirs", createDirs)));

        initStorage();
    }

    private void initStorage() throws IOException {
        rootPath = Paths.get(basePath).toAbsolutePath().normalize();

        if (createDirs) {
            Files.createDirectories(rootPath);
        }

        if (!Files.exists(rootPath)) {
            throw new IOException("Storage directory does not exist: " + rootPath);
        }

        if (!Files.isDirectory(rootPath)) {
            throw new IOException("Storage path is not a directory: " + rootPath);
        }

        initialized = true;
        log.info("Local storage initialized at: {}", rootPath);
    }

    private void ensureInitialized() throws IOException {
        if (!initialized && isEnabled()) {
            initStorage();
        }
        if (rootPath == null) {
            throw new IOException("Local storage not initialized");
        }
    }

    private Path resolvePath(String objectPath) {
        // Normalize and validate path to prevent directory traversal
        Path resolved = rootPath.resolve(objectPath).normalize();
        if (!resolved.startsWith(rootPath)) {
            throw new SecurityException("Path traversal attempt detected: " + objectPath);
        }
        return resolved;
    }

    @Override
    public StorageObject upload(InputStream inputStream, String objectPath, String contentType, long size) throws IOException {
        ensureInitialized();

        Path filePath = resolvePath(objectPath);

        // Create parent directories if needed
        if (createDirs) {
            Files.createDirectories(filePath.getParent());
        }

        // Write the file
        Files.copy(inputStream, filePath, StandardCopyOption.REPLACE_EXISTING);

        BasicFileAttributes attrs = Files.readAttributes(filePath, BasicFileAttributes.class);

        return new StorageObject(
            objectPath,
            "local",
            attrs.size(),
            contentType,
            String.valueOf(attrs.lastModifiedTime().toMillis()),
            attrs.lastModifiedTime().toInstant(),
            Map.of()
        );
    }

    @Override
    public InputStream download(String objectPath) throws IOException {
        ensureInitialized();

        Path filePath = resolvePath(objectPath);

        if (!Files.exists(filePath)) {
            throw new FileNotFoundException("File not found: " + objectPath);
        }

        return Files.newInputStream(filePath);
    }

    @Override
    public void delete(String objectPath) throws IOException {
        ensureInitialized();

        Path filePath = resolvePath(objectPath);

        if (Files.exists(filePath)) {
            Files.delete(filePath);
        }
    }

    @Override
    public boolean exists(String objectPath) throws IOException {
        ensureInitialized();

        Path filePath = resolvePath(objectPath);
        return Files.exists(filePath) && Files.isRegularFile(filePath);
    }

    @Override
    public Optional<StorageObject> getObjectInfo(String objectPath) throws IOException {
        ensureInitialized();

        Path filePath = resolvePath(objectPath);

        if (!Files.exists(filePath)) {
            return Optional.empty();
        }

        BasicFileAttributes attrs = Files.readAttributes(filePath, BasicFileAttributes.class);

        String contentType = Files.probeContentType(filePath);

        return Optional.of(new StorageObject(
            objectPath,
            "local",
            attrs.size(),
            contentType,
            String.valueOf(attrs.lastModifiedTime().toMillis()),
            attrs.lastModifiedTime().toInstant(),
            Map.of()
        ));
    }

    @Override
    public List<StorageObject> list(String prefix, int maxKeys) throws IOException {
        ensureInitialized();

        Path prefixPath = prefix != null && !prefix.isEmpty()
            ? resolvePath(prefix)
            : rootPath;

        if (!Files.exists(prefixPath)) {
            return Collections.emptyList();
        }

        List<StorageObject> objects = new ArrayList<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(prefixPath)) {
            int count = 0;
            for (Path path : stream) {
                if (count >= maxKeys) break;

                if (Files.isRegularFile(path)) {
                    BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
                    String relativePath = rootPath.relativize(path).toString().replace('\\', '/');

                    objects.add(new StorageObject(
                        relativePath,
                        "local",
                        attrs.size(),
                        Files.probeContentType(path),
                        String.valueOf(attrs.lastModifiedTime().toMillis()),
                        attrs.lastModifiedTime().toInstant(),
                        Map.of()
                    ));
                    count++;
                }
            }
        }

        return objects;
    }

    @Override
    public String getPresignedDownloadUrl(String objectPath, int expirySeconds) throws IOException {
        // Local storage doesn't support presigned URLs
        // Return a file:// URL or throw an exception
        ensureInitialized();

        Path filePath = resolvePath(objectPath);
        if (!Files.exists(filePath)) {
            throw new FileNotFoundException("File not found: " + objectPath);
        }

        return filePath.toUri().toString();
    }

    @Override
    public String getPresignedUploadUrl(String objectPath, int expirySeconds) throws IOException {
        // Local storage doesn't support presigned URLs
        throw new UnsupportedOperationException("Local storage does not support presigned upload URLs");
    }

    @Override
    public StorageObject copy(String sourcePath, String destPath) throws IOException {
        ensureInitialized();

        Path source = resolvePath(sourcePath);
        Path dest = resolvePath(destPath);

        if (!Files.exists(source)) {
            throw new FileNotFoundException("Source file not found: " + sourcePath);
        }

        // Create parent directories if needed
        if (createDirs) {
            Files.createDirectories(dest.getParent());
        }

        Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);

        return getObjectInfo(destPath).orElseThrow(() ->
            new IOException("Failed to get info for copied file: " + destPath));
    }
}
