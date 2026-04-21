package io.rdfforge.pipeline.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.rdfforge.common.exception.PipelineValidationException;
import io.rdfforge.common.exception.ResourceNotFoundException;
import io.rdfforge.common.security.AuthUser;
import io.rdfforge.pipeline.client.AuthenticatedShaclClient;
import io.rdfforge.pipeline.client.OntologySummary;
import io.rdfforge.pipeline.client.ShaclClientException;
import io.rdfforge.pipeline.client.ShapeSummary;
import io.rdfforge.pipeline.client.ValidationIssueSummary;
import io.rdfforge.pipeline.client.ValidationRunSummary;
import io.rdfforge.pipeline.dto.ReleaseBuildResponse;
import io.rdfforge.pipeline.dto.ReleaseCreateRequest;
import io.rdfforge.pipeline.dto.ReleaseDto;
import io.rdfforge.pipeline.entity.MappingEntity;
import io.rdfforge.pipeline.entity.ProjectEntity;
import io.rdfforge.pipeline.entity.ReleaseEntity;
import io.rdfforge.pipeline.entity.ReleaseStatus;
import io.rdfforge.pipeline.repository.MappingRepository;
import io.rdfforge.pipeline.repository.ProjectRepository;
import io.rdfforge.pipeline.repository.ReleaseRepository;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Release Factory service.
 *
 * <p>Lifecycle: a release is created as {@link ReleaseStatus#DRAFT}. Calling
 * {@link #build(UUID, AuthUser)} transitions it to {@code BUILDING}, gathers
 * referenced assets (mappings in-JVM, ontologies/shapes/validation via
 * {@link AuthenticatedShaclClient}), assembles a zip bundle and transitions
 * to {@code PUBLISHED}.
 *
 * <p><b>PUBLISHED means the bundle is complete and honest.</b> If any
 * manifest-listed id cannot be fetched (401/403/404/5xx or transport error)
 * the release goes to {@code FAILED}, a short {@code failureReason} is
 * persisted, and the partially-written artifact is deleted. There are no
 * "NOT_YET_FETCHED" placeholders anywhere in the output.
 *
 * <p>If a manifest doesn't list ids for a given kind (e.g. no shapes), the
 * bundle simply omits that folder — it's allowed to contain only the kinds
 * the user requested plus the always-present {@code manifest.json} +
 * {@code README.md}.
 */
@Slf4j
@Service
public class ReleaseService {

    /** Relaxed SemVer: MAJOR.MINOR.PATCH with optional pre-release / build suffix. */
    private static final Pattern SEMVER = Pattern.compile(
        "^(0|[1-9]\\d*)\\.(0|[1-9]\\d*)\\.(0|[1-9]\\d*)"
        + "(?:-(?:0|[1-9]\\d*|[0-9A-Za-z-]+)(?:\\.(?:0|[1-9]\\d*|[0-9A-Za-z-]+))*)?"
        + "(?:\\+[0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*)?$"
    );

    private static final int MAX_FAILURE_REASON_LEN = 500;
    private static final int MAX_ISSUES_PER_RUN = 500;

    private final ReleaseRepository releaseRepository;
    private final ProjectRepository projectRepository;
    private final MappingRepository mappingRepository;
    private final AuthenticatedShaclClient shaclClient;
    private final ObjectMapper objectMapper;

    @Value("${rdf-forge.release.artifact.dir:/tmp/rdf-forge-releases}")
    private String artifactDir;

    public ReleaseService(ReleaseRepository releaseRepository,
                          ProjectRepository projectRepository,
                          MappingRepository mappingRepository,
                          AuthenticatedShaclClient shaclClient) {
        this.releaseRepository = releaseRepository;
        this.projectRepository = projectRepository;
        this.mappingRepository = mappingRepository;
        this.shaclClient = shaclClient;
        this.objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    }

    @PostConstruct
    void ensureArtifactDir() {
        try {
            Files.createDirectories(Paths.get(artifactDir));
        } catch (IOException e) {
            log.warn("Unable to create release artifact directory {} — builds will fail later",
                artifactDir, e);
        }
    }

    // ─────────────────────────── create ───────────────────────────

    @Transactional
    public ReleaseDto create(UUID projectId, ReleaseCreateRequest request, AuthUser user) {
        requireAuthenticated(user);
        if (projectId == null) {
            throw new PipelineValidationException("projectId is required");
        }
        if (request == null) {
            throw new PipelineValidationException("Release create request body is required");
        }
        validateVersion(request.version());
        validateName(request.name());
        ProjectEntity project = loadProjectForWrite(projectId, user);

        if (releaseRepository.existsByProjectIdAndVersion(project.getId(), request.version())) {
            throw new PipelineValidationException(
                "Release version '" + request.version() + "' already exists for this project");
        }

        Map<String, Object> manifest = buildDraftManifest(request);

        ReleaseEntity entity = ReleaseEntity.builder()
            .projectId(project.getId())
            .version(request.version())
            .name(request.name())
            .notes(request.notes())
            .status(ReleaseStatus.DRAFT)
            .createdBy(user.id())
            .manifest(manifest)
            .artifactSizeBytes(0L)
            .build();

        try {
            entity = releaseRepository.save(entity);
        } catch (DataIntegrityViolationException e) {
            log.warn("Duplicate release insert for project={} version={}",
                project.getId(), request.version());
            throw new PipelineValidationException(
                "Release version '" + request.version() + "' already exists for this project");
        }
        log.info("Created release draft: id={} project={} version={} by={}",
            entity.getId(), project.getId(), entity.getVersion(), user.id());
        return toDto(entity);
    }

    // ─────────────────────────── build ────────────────────────────

    /**
     * Build + publish a release. Synchronous in v1. On any downstream failure
     * the release is marked FAILED, a concise {@code failureReason} is
     * persisted, and any partially-written artifact is removed.
     */
    @Transactional
    public ReleaseBuildResponse build(UUID releaseId, AuthUser user) {
        requireAuthenticated(user);
        ReleaseEntity entity = findOrThrow(releaseId);
        ProjectEntity project = loadProjectForWrite(entity.getProjectId(), user);

        if (entity.getStatus() == ReleaseStatus.PUBLISHED) {
            // Idempotent: re-requesting publish on an already-built release returns the existing record.
            log.debug("Release {} already PUBLISHED — returning existing artifact", releaseId);
            return new ReleaseBuildResponse(
                entity.getId(), entity.getArtifactUri(), entity.getArtifactSizeBytes(),
                entity.getManifest(),
                extractGateResult(entity.getManifest()));
        }
        if (entity.getStatus() == ReleaseStatus.ARCHIVED) {
            throw new PipelineValidationException("Cannot build an archived release");
        }

        // Transition DRAFT/FAILED -> BUILDING
        entity.setStatus(ReleaseStatus.BUILDING);
        entity.setFailureReason(null);
        Map<String, Object> manifest = entity.getManifest() == null
            ? new HashMap<>() : new HashMap<>(entity.getManifest());
        manifest.remove("buildError");
        manifest.put("buildStartedAt", Instant.now().toString());
        entity.setManifest(manifest);
        releaseRepository.save(entity);

        Path zipPath = resolveArtifactPath(entity);
        Path tmpPath = resolveTempPath(zipPath);
        try {
            // 1. Mappings live in our JVM. Missing mapping id is a hard failure.
            List<MappingEntity> mappings = gatherMappingsOrFail(manifest);
            manifest.put("gatheredMappings", mappings.size());

            // 2. Fetch cross-service assets. These throw ShaclClientException
            //    on any non-2xx — ReleaseService catches below and FAILs.
            List<OntologySummary> ontologies = fetchOntologies(manifest, user);
            List<ShapeSummary> shapes = fetchShapes(manifest, user);
            Map<UUID, ValidationSuitePayload> validation = fetchValidationPayload(manifest, user);

            // 3. Gate evaluation (still a WARN_ONLY stub in v1). We keep the
            //    same contract the existing tests and UI expect.
            Map<String, Object> gate = evaluateValidationGate(manifest, validation);
            manifest.put("validationGateResult", gate);

            String mode = Objects.toString(gate.get("mode"), "WARN_ONLY");
            boolean passed = Boolean.TRUE.equals(gate.get("passed"));
            if (!passed && !"WARN_ONLY".equalsIgnoreCase(mode)) {
                // Treat gate failure as a FAILED transition — no artifact.
                String reason = "Validation gate failed (mode=" + mode + ")";
                return failRelease(entity, manifest, reason, tmpPath);
            }

            // 4. Assemble zip atomically: tmp -> rename.
            Files.createDirectories(zipPath.getParent());
            long bytes = writeBundle(tmpPath, project, entity, mappings, ontologies, shapes,
                validation, manifest);
            Files.move(tmpPath, zipPath,
                StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);

            // 5. Persist PUBLISHED.
            entity.setArtifactUri(zipPath.toAbsolutePath().toString());
            entity.setArtifactSizeBytes(bytes);
            entity.setStatus(ReleaseStatus.PUBLISHED);
            entity.setPublishedAt(Instant.now());
            entity.setFailureReason(null);
            manifest.put("buildCompletedAt", Instant.now().toString());
            manifest.put("artifactBytes", bytes);
            entity.setManifest(manifest);
            releaseRepository.save(entity);
            log.info("Published release: id={} version={} bytes={}",
                entity.getId(), entity.getVersion(), bytes);

            return new ReleaseBuildResponse(
                entity.getId(), entity.getArtifactUri(), bytes, manifest, gate);
        } catch (ShaclClientException ex) {
            // Downstream service rejected us — 401/403/404/5xx. Map to FAILED
            // with a concise, credential-free reason.
            String reason = ex.getMessage();
            log.warn("Release {} build failed: downstream shacl-service error: {}",
                entity.getId(), reason);
            return failRelease(entity, manifest, reason, tmpPath);
        } catch (PipelineValidationException ex) {
            // Raised by gatherMappingsOrFail on an unresolvable mapping id.
            return failRelease(entity, manifest, ex.getMessage(), tmpPath);
        } catch (IOException ex) {
            log.error("Release {} build IO error: {}", entity.getId(), ex.getMessage(), ex);
            return failRelease(entity, manifest,
                "I/O error writing bundle: " + safeShort(ex.getClass().getSimpleName()), tmpPath);
        } catch (RuntimeException ex) {
            log.error("Release {} build unexpected error: {}", entity.getId(), ex.getMessage(), ex);
            return failRelease(entity, manifest,
                "Build failed: " + safeShort(ex.getClass().getSimpleName()), tmpPath);
        }
    }

    /**
     * Mark the release FAILED with a concise {@code failureReason}. Best-effort
     * cleanup of any tmp file. Does NOT clear a previously-published artifactUri
     * for a release that was being rebuilt — but since BUILDING is only reached
     * from DRAFT/FAILED, there shouldn't be one.
     */
    private ReleaseBuildResponse failRelease(ReleaseEntity entity,
                                             Map<String, Object> manifest,
                                             String reason,
                                             Path tmpPath) {
        String safe = safeShort(reason == null ? "unknown error" : reason);
        entity.setStatus(ReleaseStatus.FAILED);
        entity.setFailureReason(safe);
        // Keep buildError in the manifest for backwards compat with the UI's
        // "Build error" panel, but the authoritative field is failureReason.
        manifest.put("buildError", safe);
        // Artifact must not survive a FAILED build.
        entity.setArtifactUri(null);
        entity.setArtifactSizeBytes(0L);
        entity.setManifest(manifest);
        releaseRepository.save(entity);
        if (tmpPath != null) {
            try { Files.deleteIfExists(tmpPath); }
            catch (IOException io) { log.debug("Failed to delete tmp bundle {}: {}", tmpPath, io.getMessage()); }
        }
        return new ReleaseBuildResponse(
            entity.getId(), null, 0L, manifest, extractGateResult(manifest));
    }

    // ─────────────────────────── list / get ────────────────────────

    @Transactional(readOnly = true)
    public List<ReleaseDto> list(UUID projectId, AuthUser user) {
        requireAuthenticated(user);
        loadProjectForRead(projectId, user);
        return releaseRepository.findByProjectIdOrderByCreatedAtDesc(projectId).stream()
            .map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public ReleaseDto get(UUID releaseId, AuthUser user) {
        requireAuthenticated(user);
        ReleaseEntity entity = findOrThrow(releaseId);
        loadProjectForRead(entity.getProjectId(), user);
        return toDto(entity);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getManifest(UUID releaseId, AuthUser user) {
        ReleaseEntity entity = findOrThrow(releaseId);
        loadProjectForRead(entity.getProjectId(), user);
        return entity.getManifest() == null ? Map.of() : new HashMap<>(entity.getManifest());
    }

    // ─────────────────────────── archive / delete ───────────────────

    @Transactional
    public ReleaseDto archive(UUID releaseId, AuthUser user) {
        requireAuthenticated(user);
        ReleaseEntity entity = findOrThrow(releaseId);
        loadProjectForWrite(entity.getProjectId(), user);
        if (entity.getStatus() == ReleaseStatus.ARCHIVED) return toDto(entity);
        entity.setStatus(ReleaseStatus.ARCHIVED);
        entity = releaseRepository.save(entity);
        log.info("Archived release: id={} by={}", entity.getId(), user.id());
        return toDto(entity);
    }

    @Transactional
    public void delete(UUID releaseId, AuthUser user) {
        requireAuthenticated(user);
        ReleaseEntity entity = findOrThrow(releaseId);
        loadProjectForWrite(entity.getProjectId(), user);
        // Best-effort cleanup of any built artifact on disk.
        if (entity.getArtifactUri() != null) {
            try {
                Files.deleteIfExists(Paths.get(entity.getArtifactUri()));
            } catch (IOException e) {
                log.warn("Failed to delete artifact file {} during release delete: {}",
                    entity.getArtifactUri(), e.getMessage());
            }
        }
        releaseRepository.delete(entity);
        log.info("Deleted release: id={} by={}", releaseId, user.id());
    }

    // ─────────────────────────── download ───────────────────────────

    @Transactional(readOnly = true)
    public ReleaseArtifact download(UUID releaseId, AuthUser user) {
        ReleaseEntity entity = findOrThrow(releaseId);
        loadProjectForRead(entity.getProjectId(), user);
        if (entity.getStatus() != ReleaseStatus.PUBLISHED && entity.getStatus() != ReleaseStatus.ARCHIVED) {
            throw new PipelineValidationException(
                "Release is not built (status=" + entity.getStatus() + ")");
        }
        if (entity.getArtifactUri() == null) {
            throw new PipelineValidationException("Release has no artifact");
        }
        Path path = Paths.get(entity.getArtifactUri());
        if (!Files.exists(path)) {
            throw new ResourceNotFoundException("ReleaseArtifact", entity.getId().toString());
        }
        String filename = entity.getName().replaceAll("[^A-Za-z0-9_.-]", "_")
            + "-" + entity.getVersion() + ".zip";
        return new ReleaseArtifact(new FileSystemResource(path), filename, entity.getArtifactSizeBytes());
    }

    /** Returned shape for a download request. */
    public record ReleaseArtifact(Resource resource, String filename, long sizeBytes) {}

    // ─────────────────────────── helpers ───────────────────────────

    /** Tuple used internally while staging validation suite payloads. */
    private record ValidationSuitePayload(ValidationRunSummary run,
                                          List<ValidationIssueSummary> issues) {}

    private Map<String, Object> buildDraftManifest(ReleaseCreateRequest req) {
        Map<String, Object> m = new LinkedHashMap<>();
        Map<String, Object> refs = new LinkedHashMap<>();
        ReleaseCreateRequest.ManifestRefs mr = req.manifestRefs();
        refs.put("dataSources", nullToEmpty(mr.dataSources()));
        refs.put("mappings", nullToEmpty(mr.mappings()));
        refs.put("shapes", nullToEmpty(mr.shapes()));
        refs.put("ontologies", nullToEmpty(mr.ontologies()));
        refs.put("triplestoreId", mr.triplestoreId() == null ? null : mr.triplestoreId().toString());
        refs.put("validationSuiteIds", nullToEmpty(mr.validationSuiteIds()));
        m.put("refs", refs);
        m.put("draftedAt", Instant.now().toString());
        return m;
    }

    private List<String> nullToEmpty(List<UUID> v) {
        if (v == null) return List.of();
        return v.stream().filter(Objects::nonNull).map(UUID::toString).toList();
    }

    /**
     * Resolve mapping entities. Any id in the manifest that can't be found
     * is a hard failure — we won't publish a bundle that claims to contain
     * a mapping we couldn't load.
     */
    private List<MappingEntity> gatherMappingsOrFail(Map<String, Object> manifest) {
        List<MappingEntity> out = new ArrayList<>();
        List<String> rawIds = readIdList(manifest, "mappings");
        for (String s : rawIds) {
            UUID id;
            try { id = UUID.fromString(s); }
            catch (IllegalArgumentException e) {
                throw new PipelineValidationException(
                    "Manifest references invalid mapping id: " + s);
            }
            Optional<MappingEntity> e = mappingRepository.findById(id);
            if (e.isEmpty()) {
                throw new PipelineValidationException(
                    "Failed to fetch mapping " + id + ": not found");
            }
            out.add(e.get());
        }
        return out;
    }

    private List<OntologySummary> fetchOntologies(Map<String, Object> manifest, AuthUser user) {
        List<String> ids = readIdList(manifest, "ontologies");
        if (ids.isEmpty()) return List.of();
        List<OntologySummary> out = new ArrayList<>();
        for (String s : ids) {
            UUID id = parseIdOrFail(s, "ontology");
            out.add(shaclClient.fetchOntology(id, user, "TURTLE"));
        }
        return out;
    }

    private List<ShapeSummary> fetchShapes(Map<String, Object> manifest, AuthUser user) {
        List<String> ids = readIdList(manifest, "shapes");
        if (ids.isEmpty()) return List.of();
        List<ShapeSummary> out = new ArrayList<>();
        for (String s : ids) {
            UUID id = parseIdOrFail(s, "shape");
            out.add(shaclClient.fetchShape(id, user));
        }
        return out;
    }

    private Map<UUID, ValidationSuitePayload> fetchValidationPayload(
            Map<String, Object> manifest, AuthUser user) {
        List<String> ids = readIdList(manifest, "validationSuiteIds");
        if (ids.isEmpty()) return Map.of();
        Map<UUID, ValidationSuitePayload> out = new LinkedHashMap<>();
        for (String s : ids) {
            UUID suiteId = parseIdOrFail(s, "validation suite");
            ValidationRunSummary run = shaclClient.fetchLatestRun(suiteId, user);
            List<ValidationIssueSummary> issues = (run == null)
                ? List.of()
                : shaclClient.fetchIssues(run.id(), user, MAX_ISSUES_PER_RUN);
            out.put(suiteId, new ValidationSuitePayload(run, issues));
        }
        return out;
    }

    private static UUID parseIdOrFail(String raw, String what) {
        try { return UUID.fromString(raw); }
        catch (IllegalArgumentException iae) {
            throw new PipelineValidationException(
                "Manifest references invalid " + what + " id: " + raw);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> readIdList(Map<String, Object> manifest, String key) {
        Map<String, Object> refs = (Map<String, Object>) manifest.getOrDefault("refs", Map.of());
        Object raw = refs.get(key);
        if (!(raw instanceof List<?> rawList)) return List.of();
        List<String> out = new ArrayList<>();
        for (Object o : rawList) {
            if (o == null) continue;
            String s = o.toString().trim();
            if (!s.isEmpty()) out.add(s);
        }
        return out;
    }

    /**
     * Validation gate evaluator. In v1 this remains WARN_ONLY; when
     * validation payloads are present we fold their pass/fail status into
     * the result so the manifest shows it. The build does not actually
     * block on a failing gate unless mode != WARN_ONLY.
     */
    private Map<String, Object> evaluateValidationGate(
            Map<String, Object> manifest,
            Map<UUID, ValidationSuitePayload> validation) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", "WARN_ONLY");
        boolean passed = true;
        int errors = 0;
        for (ValidationSuitePayload p : validation.values()) {
            if (p.run() == null) continue;
            errors += p.run().errorCount() + p.run().fatalCount();
            String st = p.run().status();
            if (st != null && st.equalsIgnoreCase("FAILED")) passed = false;
        }
        result.put("passed", passed);
        result.put("suitesEvaluated", validation.size());
        result.put("totalErrors", errors);
        result.put("evaluatedAt", Instant.now().toString());
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> extractGateResult(Map<String, Object> manifest) {
        if (manifest == null) return Map.of();
        Object g = manifest.get("validationGateResult");
        return g instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    private Path resolveArtifactPath(ReleaseEntity entity) {
        String safeName = entity.getName().replaceAll("[^A-Za-z0-9_.-]", "_");
        return Paths.get(artifactDir,
            entity.getProjectId().toString(),
            safeName + "-" + entity.getVersion() + "-" + entity.getId() + ".zip");
    }

    private static Path resolveTempPath(Path finalPath) {
        return finalPath.resolveSibling(finalPath.getFileName() + ".tmp");
    }

    /**
     * Assemble the bundle. Always writes {@code manifest.json} and
     * {@code README.md}; writes {@code mappings/*.json}, {@code ontologies/*.ttl},
     * {@code shapes/*.ttl}, {@code validation-summary.json} only when the
     * corresponding input is non-empty. No placeholders, ever.
     */
    private long writeBundle(Path zipPath,
                             ProjectEntity project,
                             ReleaseEntity release,
                             List<MappingEntity> mappings,
                             List<OntologySummary> ontologies,
                             List<ShapeSummary> shapes,
                             Map<UUID, ValidationSuitePayload> validation,
                             Map<String, Object> manifest) throws IOException {
        try (OutputStream out = Files.newOutputStream(zipPath);
             ZipOutputStream zos = new ZipOutputStream(out)) {

            // manifest.json
            Map<String, Object> manifestEnvelope = new LinkedHashMap<>();
            manifestEnvelope.put("release", toDto(release));
            manifestEnvelope.put("project", Map.of(
                "id", project.getId().toString(),
                "name", project.getName(),
                "baseUri", project.getBaseUri()
            ));
            manifestEnvelope.put("manifest", manifest);
            Map<String, Object> assets = new LinkedHashMap<>();
            assets.put("mappings", mappings.stream().map(m -> Map.of(
                "id", m.getId().toString(),
                "name", m.getName(),
                "version", m.getVersion()
            )).toList());
            if (!ontologies.isEmpty()) {
                assets.put("ontologies", ontologies.stream().map(o -> Map.of(
                    "id", o.id().toString(),
                    "name", safe(o.name()),
                    "prefix", safe(o.prefix())
                )).toList());
            }
            if (!shapes.isEmpty()) {
                assets.put("shapes", shapes.stream().map(s -> Map.of(
                    "id", s.id().toString(),
                    "name", safe(s.name())
                )).toList());
            }
            if (!validation.isEmpty()) {
                assets.put("validationSuites", validation.keySet().stream()
                    .map(UUID::toString).toList());
            }
            manifestEnvelope.put("assets", assets);
            writeEntry(zos, "manifest.json", toJson(manifestEnvelope));

            // README.md
            writeEntry(zos, "README.md",
                buildReadme(project, release, mappings, ontologies, shapes, validation));

            // mappings/*.json
            for (MappingEntity m : mappings) {
                Map<String, Object> body = new LinkedHashMap<>();
                body.put("id", m.getId().toString());
                body.put("projectId", m.getProjectId().toString());
                body.put("name", m.getName());
                body.put("description", m.getDescription());
                body.put("sourceType", m.getSourceType() == null ? null : m.getSourceType().name());
                body.put("sourceConfig", m.getSourceConfig());
                body.put("targetNamespace", m.getTargetNamespace());
                body.put("targetOntologies", m.getTargetOntologies());
                body.put("rules", m.getRules());
                body.put("mappingType", m.getMappingType() == null ? null : m.getMappingType().name());
                body.put("version", m.getVersion());
                String safe = m.getName().replaceAll("[^A-Za-z0-9_.-]", "_");
                writeEntry(zos, "mappings/" + safe + "-" + m.getVersion() + ".json", toJson(body));
            }

            // ontologies/*.ttl — real Turtle bytes.
            if (!ontologies.isEmpty()) {
                Set<String> used = new HashSet<>();
                for (OntologySummary o : ontologies) {
                    String slug = uniqueSlug(ontologySlug(o), used);
                    String content = o.content() == null ? "" : o.content();
                    writeEntry(zos, "ontologies/" + slug + ".ttl", content);
                }
            }

            // shapes/*.ttl — real Turtle (or whatever format shape declared).
            if (!shapes.isEmpty()) {
                Set<String> used = new HashSet<>();
                for (ShapeSummary s : shapes) {
                    String slug = uniqueSlug(shapeSlug(s), used);
                    String content = s.content() == null ? "" : s.content();
                    writeEntry(zos, "shapes/" + slug + ".ttl", content);
                }
            }

            // validation-summary.json — real aggregated data.
            if (!validation.isEmpty()) {
                writeEntry(zos, "validation-summary.json",
                    toJson(buildValidationSummary(validation)));
            }
        }

        long size = Files.size(zipPath);
        if (size <= 0) {
            throw new IOException("Zip bundle was written with zero bytes");
        }
        return size;
    }

    private static Map<String, Object> buildValidationSummary(
            Map<UUID, ValidationSuitePayload> validation) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("generatedAt", Instant.now().toString());
        List<Map<String, Object>> suites = new ArrayList<>();
        int totalIssues = 0, totalErrors = 0, totalWarnings = 0;
        for (Map.Entry<UUID, ValidationSuitePayload> e : validation.entrySet()) {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("suiteId", e.getKey().toString());
            ValidationRunSummary run = e.getValue().run();
            if (run == null) {
                s.put("latestRun", null);
                s.put("issueCount", 0);
                s.put("issues", List.of());
            } else {
                Map<String, Object> r = new LinkedHashMap<>();
                r.put("id", run.id() == null ? null : run.id().toString());
                r.put("ranAt", run.ranAt() == null ? null : run.ranAt().toString());
                r.put("status", run.status());
                r.put("issueCount", run.issueCount());
                r.put("errorCount", run.errorCount());
                r.put("warningCount", run.warningCount());
                r.put("infoCount", run.infoCount());
                r.put("fatalCount", run.fatalCount());
                r.put("summary", run.summary());
                s.put("latestRun", r);
                s.put("issueCount", run.issueCount());
                totalIssues += run.issueCount();
                totalErrors += run.errorCount() + run.fatalCount();
                totalWarnings += run.warningCount();
                List<Map<String, Object>> iss = new ArrayList<>();
                for (ValidationIssueSummary i : e.getValue().issues()) {
                    Map<String, Object> im = new LinkedHashMap<>();
                    im.put("id", i.id() == null ? null : i.id().toString());
                    im.put("ruleId", i.ruleId());
                    im.put("severity", i.severity());
                    im.put("resourceUri", i.resourceUri());
                    im.put("message", i.message());
                    im.put("sourcePath", i.sourcePath());
                    iss.add(im);
                }
                s.put("issues", iss);
            }
            suites.add(s);
        }
        root.put("suites", suites);
        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("totalIssues", totalIssues);
        totals.put("totalErrors", totalErrors);
        totals.put("totalWarnings", totalWarnings);
        root.put("totals", totals);
        return root;
    }

    private static String ontologySlug(OntologySummary o) {
        if (o.name() != null && !o.name().isBlank()) return slugify(o.name());
        if (o.prefix() != null && !o.prefix().isBlank()) return slugify(o.prefix());
        return o.id().toString();
    }

    private static String shapeSlug(ShapeSummary s) {
        if (s.name() != null && !s.name().isBlank()) return slugify(s.name());
        return s.id().toString();
    }

    private static String slugify(String s) {
        String t = s.trim().toLowerCase().replaceAll("[^a-z0-9]+", "-");
        while (t.startsWith("-")) t = t.substring(1);
        while (t.endsWith("-")) t = t.substring(0, t.length() - 1);
        return t.isBlank() ? "unnamed" : t;
    }

    private static String uniqueSlug(String base, Set<String> used) {
        if (used.add(base)) return base;
        int n = 2;
        while (!used.add(base + "-" + n)) n++;
        return base + "-" + n;
    }

    private static String safe(String s) { return s == null ? "" : s; }

    private void writeEntry(ZipOutputStream zos, String name, String content) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        zos.putNextEntry(entry);
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    private String buildReadme(ProjectEntity project, ReleaseEntity release,
                               List<MappingEntity> mappings,
                               List<OntologySummary> ontologies,
                               List<ShapeSummary> shapes,
                               Map<UUID, ValidationSuitePayload> validation) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(project.getName()).append(" - Release ")
            .append(release.getVersion()).append("\n\n");
        sb.append("Release name: ").append(release.getName()).append("\n\n");
        if (release.getNotes() != null && !release.getNotes().isBlank()) {
            sb.append("## Release Notes\n\n").append(release.getNotes()).append("\n\n");
        }
        sb.append("## Project\n\n");
        sb.append("- id: `").append(project.getId()).append("`\n");
        sb.append("- base URI: `").append(project.getBaseUri()).append("`\n");
        if (project.getDescription() != null) {
            sb.append("- description: ").append(project.getDescription()).append("\n");
        }
        sb.append("\n## Assets Included\n\n");
        sb.append("- mappings: ").append(mappings.size()).append("\n");
        for (MappingEntity m : mappings) {
            sb.append("  - `").append(m.getName()).append("` v").append(m.getVersion()).append("\n");
        }
        if (!ontologies.isEmpty()) {
            sb.append("- ontologies: ").append(ontologies.size()).append("\n");
            for (OntologySummary o : ontologies) {
                sb.append("  - `").append(safe(o.name())).append("`\n");
            }
        }
        if (!shapes.isEmpty()) {
            sb.append("- shapes: ").append(shapes.size()).append("\n");
            for (ShapeSummary s : shapes) {
                sb.append("  - `").append(safe(s.name())).append("`\n");
            }
        }
        if (!validation.isEmpty()) {
            sb.append("- validation suites: ").append(validation.size()).append("\n");
        }
        sb.append("\n## Manifest\n\n");
        sb.append("See `manifest.json` in this bundle for the full manifest including\n");
        sb.append("validation gate result and cross-service asset references.\n");
        return sb.toString();
    }

    private String toJson(Object v) {
        try {
            return objectMapper.writeValueAsString(v);
        } catch (JsonProcessingException e) {
            return "{\"error\":\"serialization_failed\"}";
        }
    }

    /** Keep failure_reason short and ASCII-safe; never log bearer tokens/passwords. */
    private static String safeShort(String s) {
        if (s == null) return null;
        String stripped = s.replaceAll("\\s+", " ").trim();
        if (stripped.length() > MAX_FAILURE_REASON_LEN) {
            stripped = stripped.substring(0, MAX_FAILURE_REASON_LEN - 3) + "...";
        }
        return stripped;
    }

    private ProjectEntity loadProjectForRead(UUID projectId, AuthUser user) {
        ProjectEntity project = projectRepository.findById(projectId)
            .orElseThrow(() -> new ResourceNotFoundException("Project", projectId.toString()));
        requireOwnerOrAdmin(project, user, "read");
        return project;
    }

    private ProjectEntity loadProjectForWrite(UUID projectId, AuthUser user) {
        ProjectEntity project = projectRepository.findById(projectId)
            .orElseThrow(() -> new ResourceNotFoundException("Project", projectId.toString()));
        requireOwnerOrAdmin(project, user, "write");
        return project;
    }

    private ReleaseEntity findOrThrow(UUID id) {
        if (id == null) throw new PipelineValidationException("Release id is required");
        return releaseRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Release", id.toString()));
    }

    private static void requireOwnerOrAdmin(ProjectEntity project, AuthUser user, String action) {
        if (user.isAdmin()) return;
        if (!Objects.equals(project.getCreatedBy(), user.id())) {
            throw new AccessDeniedException("Not authorized to " + action + " releases in this project");
        }
    }

    private static void requireAuthenticated(AuthUser user) {
        if (user == null || user.isAnonymous()) {
            throw new AccessDeniedException("Authentication required");
        }
    }

    private static void validateVersion(String version) {
        if (version == null || version.isBlank()) {
            throw new PipelineValidationException("Version is required");
        }
        if (!SEMVER.matcher(version).matches()) {
            throw new PipelineValidationException(
                "Version must be a SemVer string (e.g. '1.0.0' or '1.0.0-rc.1')");
        }
    }

    private static void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new PipelineValidationException("Release name is required");
        }
        if (name.length() > 255) {
            throw new PipelineValidationException("Release name must not exceed 255 characters");
        }
    }

    private ReleaseDto toDto(ReleaseEntity e) {
        Map<String, Object> manifest = e.getManifest() == null
            ? null : new HashMap<>(e.getManifest());
        return new ReleaseDto(
            e.getId(),
            e.getProjectId(),
            e.getVersion(),
            e.getName(),
            e.getNotes(),
            e.getStatus(),
            manifest,
            e.getArtifactUri(),
            e.getArtifactSizeBytes(),
            e.getCreatedBy(),
            e.getCreatedAt(),
            e.getUpdatedAt(),
            e.getPublishedAt(),
            e.getFailureReason()
        );
    }
}
