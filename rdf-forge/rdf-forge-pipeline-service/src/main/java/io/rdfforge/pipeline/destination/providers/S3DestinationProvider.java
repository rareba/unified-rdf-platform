package io.rdfforge.pipeline.destination.providers;

import io.rdfforge.pipeline.destination.DestinationInfo;
import io.rdfforge.pipeline.destination.DestinationInfo.ConfigField;
import io.rdfforge.pipeline.destination.DestinationProvider;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.springframework.stereotype.Component;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Destination provider for publishing RDF data to S3-compatible storage.
 *
 * Supports AWS S3, MinIO, and other S3-compatible services.
 * This implementation uses direct HTTP requests for S3 compatibility
 * without requiring the full AWS SDK.
 */
@Component
@Slf4j
public class S3DestinationProvider implements DestinationProvider {

    private static final String TYPE = "s3";

    private static final Map<String, RDFFormat> FORMAT_MAP = Map.of(
        "turtle", RDFFormat.TURTLE_PRETTY,
        "json-ld", RDFFormat.JSONLD_PRETTY,
        "n-triples", RDFFormat.NTRIPLES,
        "rdf/xml", RDFFormat.RDFXML_PRETTY
    );

    private static final Map<String, String> CONTENT_TYPES = Map.of(
        "turtle", "text/turtle",
        "json-ld", "application/ld+json",
        "n-triples", "application/n-triples",
        "rdf/xml", "application/rdf+xml"
    );

    private static final Map<String, String> FORMAT_EXTENSIONS = Map.of(
        "turtle", ".ttl",
        "json-ld", ".jsonld",
        "n-triples", ".nt",
        "rdf/xml", ".rdf"
    );

    @Override
    public DestinationInfo getDestinationInfo() {
        Map<String, ConfigField> configFields = new LinkedHashMap<>();

        configFields.put("endpoint", new ConfigField(
            "endpoint",
            "S3 Endpoint",
            "string",
            "The S3 endpoint URL (e.g., https://s3.amazonaws.com or http://localhost:9000 for MinIO)",
            true
        ));

        configFields.put("bucket", new ConfigField(
            "bucket",
            "Bucket Name",
            "string",
            "The S3 bucket name",
            true
        ));

        configFields.put("key", new ConfigField(
            "key",
            "Object Key",
            "string",
            "The object key (path) within the bucket. Supports templates: {timestamp}, {date}",
            true,
            "rdf-output/{timestamp}"
        ));

        configFields.put("accessKey", new ConfigField(
            "accessKey",
            "Access Key",
            "string",
            "AWS Access Key ID",
            true,
            true
        ));

        configFields.put("secretKey", new ConfigField(
            "secretKey",
            "Secret Key",
            "password",
            "AWS Secret Access Key",
            true,
            true
        ));

        configFields.put("region", new ConfigField(
            "region",
            "Region",
            "string",
            "AWS Region (e.g., us-east-1)",
            false,
            "us-east-1"
        ));

        configFields.put("format", new ConfigField(
            "format",
            "Output Format",
            "select",
            "The RDF serialization format",
            true,
            "turtle",
            List.of("turtle", "json-ld", "n-triples", "rdf/xml"),
            false
        ));

        configFields.put("storageClass", new ConfigField(
            "storageClass",
            "Storage Class",
            "select",
            "S3 storage class",
            false,
            "STANDARD",
            List.of("STANDARD", "STANDARD_IA", "ONEZONE_IA", "INTELLIGENT_TIERING", "GLACIER"),
            false
        ));

        configFields.put("publicRead", new ConfigField(
            "publicRead",
            "Public Read",
            "boolean",
            "Make the uploaded object publicly readable",
            false,
            false
        ));

        return new DestinationInfo(
            TYPE,
            "S3 / MinIO Storage",
            "Publish RDF data to Amazon S3 or S3-compatible storage (MinIO, DigitalOcean Spaces, etc.)",
            DestinationInfo.CATEGORY_CLOUD_STORAGE,
            configFields,
            List.of(
                DestinationInfo.CAPABILITY_REPLACE,
                DestinationInfo.CAPABILITY_STREAMING
            ),
            List.of("turtle", "json-ld", "n-triples", "rdf/xml")
        );
    }

    @Override
    public PublishResult publish(Model model, Map<String, Object> config) throws IOException {
        String endpoint = (String) config.get("endpoint");
        String bucket = (String) config.get("bucket");
        String key = (String) config.get("key");
        String accessKey = (String) config.get("accessKey");
        String secretKey = (String) config.get("secretKey");
        String region = (String) config.getOrDefault("region", "us-east-1");
        String format = (String) config.getOrDefault("format", "turtle");
        String storageClass = (String) config.getOrDefault("storageClass", "STANDARD");
        boolean publicRead = Boolean.TRUE.equals(config.get("publicRead"));

        // Validate required fields
        if (endpoint == null || bucket == null || key == null) {
            return PublishResult.failure("Missing required configuration: endpoint, bucket, and key are required");
        }

        try {
            // Process key template
            String processedKey = processKeyTemplate(key);

            // Add extension
            String extension = FORMAT_EXTENSIONS.getOrDefault(format.toLowerCase(), ".ttl");
            if (!processedKey.endsWith(extension)) {
                processedKey += extension;
            }

            // Serialize model
            RDFFormat rdfFormat = FORMAT_MAP.getOrDefault(format.toLowerCase(), RDFFormat.TURTLE_PRETTY);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            RDFDataMgr.write(baos, model, rdfFormat);
            byte[] data = baos.toByteArray();

            long tripleCount = model.size();

            // Upload to S3
            String contentType = CONTENT_TYPES.getOrDefault(format.toLowerCase(), "application/octet-stream");

            uploadToS3(endpoint, bucket, processedKey, data, contentType, accessKey, secretKey,
                       region, storageClass, publicRead);

            String objectUrl = endpoint + "/" + bucket + "/" + processedKey;

            log.info("Uploaded {} triples to S3: {}", tripleCount, objectUrl);

            return PublishResult.success(tripleCount, null, Map.of(
                "bucket", bucket,
                "key", processedKey,
                "url", objectUrl,
                "format", format,
                "sizeBytes", data.length
            ));

        } catch (Exception e) {
            log.error("Failed to upload to S3: {}", e.getMessage(), e);
            return PublishResult.failure("Failed to upload: " + e.getMessage());
        }
    }

    private String processKeyTemplate(String template) {
        LocalDateTime now = LocalDateTime.now();
        return template
            .replace("{timestamp}", now.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")))
            .replace("{date}", now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")))
            .replace("{year}", String.valueOf(now.getYear()))
            .replace("{month}", String.format("%02d", now.getMonthValue()))
            .replace("{day}", String.format("%02d", now.getDayOfMonth()));
    }

    private void uploadToS3(String endpoint, String bucket, String key, byte[] data,
                            String contentType, String accessKey, String secretKey,
                            String region, String storageClass, boolean publicRead)
            throws IOException {
        // Construct the URL
        String urlString = endpoint;
        if (!urlString.endsWith("/")) {
            urlString += "/";
        }
        urlString += bucket + "/" + key;

        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        try {
            conn.setRequestMethod("PUT");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", contentType);
            conn.setRequestProperty("Content-Length", String.valueOf(data.length));
            conn.setRequestProperty("x-amz-storage-class", storageClass);

            if (publicRead) {
                conn.setRequestProperty("x-amz-acl", "public-read");
            }

            // Sign the request (simplified - in production use AWS SDK or proper signing)
            String dateStr = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
                .format(LocalDateTime.now(ZoneOffset.UTC));
            conn.setRequestProperty("x-amz-date", dateStr);

            // For MinIO with access/secret key
            if (accessKey != null && secretKey != null) {
                String auth = accessKey + ":" + secretKey;
                String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));
                // MinIO accepts basic auth as fallback
                conn.setRequestProperty("Authorization", "AWS " + accessKey + ":" +
                    calculateSimpleSignature(secretKey, "PUT", bucket, key, dateStr));
            }

            // Write data
            try (OutputStream os = conn.getOutputStream()) {
                os.write(data);
            }

            int responseCode = conn.getResponseCode();
            if (responseCode < 200 || responseCode >= 300) {
                String errorMsg = readErrorResponse(conn);
                throw new IOException("S3 upload failed with code " + responseCode + ": " + errorMsg);
            }

        } finally {
            conn.disconnect();
        }
    }

    private String calculateSimpleSignature(String secretKey, String method, String bucket,
                                             String key, String dateStr) {
        try {
            String stringToSign = method + "\n\n\n" + dateStr + "\n/" + bucket + "/" + key;
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA1");
            mac.init(new javax.crypto.spec.SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
            byte[] rawHmac = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(rawHmac);
        } catch (Exception e) {
            log.warn("Failed to calculate signature: {}", e.getMessage());
            return "";
        }
    }

    private String readErrorResponse(HttpURLConnection conn) {
        try {
            InputStream errorStream = conn.getErrorStream();
            if (errorStream != null) {
                return new String(errorStream.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            // Ignore
        }
        return "Unknown error";
    }

    @Override
    public void clearGraph(String graphUri, Map<String, Object> config) throws IOException {
        // S3 doesn't have graphs - this would delete the object
        String endpoint = (String) config.get("endpoint");
        String bucket = (String) config.get("bucket");
        String key = (String) config.get("key");
        String accessKey = (String) config.get("accessKey");
        String secretKey = (String) config.get("secretKey");

        if (endpoint != null && bucket != null && key != null) {
            String urlString = endpoint + "/" + bucket + "/" + key;
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();

            try {
                conn.setRequestMethod("DELETE");

                if (accessKey != null && secretKey != null) {
                    String dateStr = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
                        .format(LocalDateTime.now(ZoneOffset.UTC));
                    conn.setRequestProperty("x-amz-date", dateStr);
                    conn.setRequestProperty("Authorization", "AWS " + accessKey + ":" +
                        calculateSimpleSignature(secretKey, "DELETE", bucket, key, dateStr));
                }

                int responseCode = conn.getResponseCode();
                if (responseCode >= 200 && responseCode < 300) {
                    log.info("Deleted S3 object: {}/{}", bucket, key);
                }
            } finally {
                conn.disconnect();
            }
        }
    }

    @Override
    public boolean isAvailable(Map<String, Object> config) {
        String endpoint = (String) config.get("endpoint");
        String bucket = (String) config.get("bucket");

        if (endpoint == null || bucket == null) {
            return false;
        }

        try {
            String urlString = endpoint + "/" + bucket;
            URL url = new URL(urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("HEAD");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            int responseCode = conn.getResponseCode();
            return responseCode < 400;
        } catch (Exception e) {
            log.debug("S3 availability check failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public ValidationResult validateConfig(Map<String, Object> config) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        String endpoint = (String) config.get("endpoint");
        if (endpoint == null || endpoint.isBlank()) {
            errors.add("S3 endpoint is required");
        } else {
            try {
                new URL(endpoint);
            } catch (Exception e) {
                errors.add("Invalid endpoint URL: " + endpoint);
            }
        }

        String bucket = (String) config.get("bucket");
        if (bucket == null || bucket.isBlank()) {
            errors.add("Bucket name is required");
        }

        String key = (String) config.get("key");
        if (key == null || key.isBlank()) {
            errors.add("Object key is required");
        }

        String accessKey = (String) config.get("accessKey");
        String secretKey = (String) config.get("secretKey");
        if ((accessKey == null || accessKey.isBlank()) && (secretKey == null || secretKey.isBlank())) {
            warnings.add("No credentials provided - upload may fail unless bucket is publicly writable");
        } else if (accessKey == null || secretKey == null) {
            errors.add("Both access key and secret key must be provided");
        }

        String format = (String) config.get("format");
        if (format != null && !FORMAT_MAP.containsKey(format.toLowerCase())) {
            errors.add("Unsupported format: " + format + ". Supported: " + FORMAT_MAP.keySet());
        }

        if (errors.isEmpty()) {
            return ValidationResult.success();
        }
        return new ValidationResult(false, errors, warnings);
    }
}
