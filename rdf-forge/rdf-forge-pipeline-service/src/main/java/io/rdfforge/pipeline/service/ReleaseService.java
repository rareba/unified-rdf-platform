package io.rdfforge.pipeline.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.rdfforge.common.exception.PipelineValidationException;
import io.rdfforge.common.exception.ResourceNotFoundException;
import io.rdfforge.common.security.AuthUser;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Release Factory service (Phase 6).
 *
 * <p>Lifecycle: a release is created as {@link ReleaseStatus#DRAFT}. Calling
 * {@link #build(UUID, AuthUser)} transitions it to {@code BUILDING}, gathers
 * referenced assets, evaluates any validation gate, assembles a zip bundle,
 * writes the zip to a configurable directory, and transitions to
 * {@code PUBLISHED} (or {@code FAILED} on any step).
 *
 * <p>Cross-service asset resolution is done best-effort: in-JVM beans
 * ({@link MappingRepository} / {@link ProjectRepository}) give us real
 * mappings + project metadata. Shapes, ontologies, data sources and
 * validation suites live in sibling services and would need WebClient calls
 * — for v1 they are stamped into the manifest as {@code kind=REFERENCE}
 * placeholders. See TODO notes below. The zip is still a REAL valid zip and
 * the mappings/project bits are persisted as real files.
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

    private final ReleaseRepository releaseRepository;
    private final ProjectRepository projectRepository;
    private final MappingRepository mappingRepository;
    private final ObjectMapper objectMapper;

    @Value("${rdf-forge.release.artifact.dir:/tmp/rdf-forge-releases}")
    private String artifactDir;

    public ReleaseService(ReleaseRepository releaseRepository,
                          ProjectRepository projectRepository,
                          MappingRepository mappingRepository) {
        this.releaseRepository = releaseRepository;
        this.projectRepository = projectRepository;
        this.mappingRepository = mappingRepository;
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
     * Build + publish a release. This is a synchronous single-transaction
     * implementation; in Phase 6.1 this becomes async with a job id and
     * progress stream. Failures mark the release as FAILED with the error
     * recorded in manifest.buildError.
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
        Map<String, Object> manifest = entity.getManifest() == null
            ? new HashMap<>() : new HashMap<>(entity.getManifest());
        manifest.remove("buildError");
        manifest.put("buildStartedAt", Instant.now().toString());
        entity.setManifest(manifest);
        releaseRepository.save(entity);

        try {
            // 1. Gather assets
            List<MappingEntity> mappings = gatherMappings(manifest);
            manifest.put("gatheredMappings", mappings.size());

            // 2. Evaluate validation gate (stub for v1 — see TODO)
            Map<String, Object> gate = evaluateValidationGate(manifest);
            manifest.put("validationGateResult", gate);

            String mode = Objects.toString(gate.get("mode"), "WARN_ONLY");
            boolean passed = Boolean.TRUE.equals(gate.get("passed"));
            if (!passed && !"WARN_ONLY".equalsIgnoreCase(mode)) {
                manifest.put("buildError",
                    "Validation gate failed (mode=" + mode + ")");
                entity.setManifest(manifest);
                entity.setStatus(ReleaseStatus.FAILED);
                releaseRepository.save(entity);
                return new ReleaseBuildResponse(
                    entity.getId(), null, 0L, manifest, gate);
            }

            // 3. Assemble zip
            Path zipPath = resolveArtifactPath(entity);
            Files.createDirectories(zipPath.getParent());
            long bytes = writeBundle(zipPath, project, entity, mappings, manifest);

            // 4. Persist
            entity.setArtifactUri(zipPath.toAbsolutePath().toString());
            entity.setArtifactSizeBytes(bytes);
            entity.setStatus(ReleaseStatus.PUBLISHED);
            entity.setPublishedAt(Instant.now());
            manifest.put("buildCompletedAt", Instant.now().toString());
            manifest.put("artifactBytes", bytes);
            entity.setManifest(manifest);
            releaseRepository.save(entity);
            log.info("Published release: id={} version={} bytes={}",
                entity.getId(), entity.getVersion(), bytes);

            return new ReleaseBuildResponse(
                entity.getId(), entity.getArtifactUri(), bytes, manifest, gate);
        } catch (Exception ex) {
            log.error("Release build failed for id={}: {}", entity.getId(), ex.getMessage(), ex);
            manifest.put("buildError", ex.getMessage() == null ? ex.getClass().getSimpleName()
                                                                : ex.getMessage());
            entity.setManifest(manifest);
            entity.setStatus(ReleaseStatus.FAILED);
            releaseRepository.save(entity);
            // Do NOT rethrow — the FAILED record is the response contract.
            return new ReleaseBuildResponse(
                entity.getId(), null, 0L, manifest, extractGateResult(manifest));
        }
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
     * Resolve mapping entities we own in this JVM. Missing IDs are tracked in
     * {@code manifest.missingMappings} so the bundle is explicit about what
     * could not be resolved.
     */
    @SuppressWarnings("unchecked")
    private List<MappingEntity> gatherMappings(Map<String, Object> manifest) {
        List<MappingEntity> out = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        Map<String, Object> refs = (Map<String, Object>) manifest.getOrDefault("refs", Map.of());
        Object raw = refs.get("mappings");
        if (!(raw instanceof List<?> rawList)) return out;
        for (Object o : rawList) {
            if (o == null) continue;
            try {
                UUID id = UUID.fromString(o.toString());
                Optional<MappingEntity> e = mappingRepository.findById(id);
                if (e.isPresent()) out.add(e.get());
                else missing.add(id.toString());
            } catch (IllegalArgumentException iae) {
                missing.add(o.toString());
            }
        }
        if (!missing.isEmpty()) manifest.put("missingMappings", missing);
        return out;
    }

    /**
     * Validation gate evaluator stub. In v1 the gate always PASSES in
     * WARN_ONLY mode — the real implementation calls across to
     * shacl-service.ValidationService. TODO(Phase 6.1): wire a WebClient to
     * {@code /api/v1/validation/runs?projectId=&suiteIds=} and fold the
     * latest conformance status per suite into the result.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> evaluateValidationGate(Map<String, Object> manifest) {
        Map<String, Object> refs = (Map<String, Object>) manifest.getOrDefault("refs", Map.of());
        List<?> suites = (List<?>) refs.getOrDefault("validationSuiteIds", List.of());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", "WARN_ONLY");
        result.put("passed", true);
        result.put("suitesEvaluated", suites.size());
        result.put("evaluatedAt", Instant.now().toString());
        result.put("todo", "Wire shacl-service ValidationService via WebClient in Phase 6.1");
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

    /**
     * Assemble the bundle. Writes, at minimum:
     *   manifest.json, README.md, mappings/*.json
     * plus placeholders for cross-service assets. Must produce a structurally
     * valid zip regardless of what the manifest contained.
     */
    private long writeBundle(Path zipPath,
                             ProjectEntity project,
                             ReleaseEntity release,
                             List<MappingEntity> mappings,
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
            manifestEnvelope.put("assets", Map.of(
                "mappings", mappings.stream().map(m -> Map.of(
                    "id", m.getId().toString(),
                    "name", m.getName(),
                    "version", m.getVersion()
                )).toList()
            ));
            writeEntry(zos, "manifest.json", toJson(manifestEnvelope));

            // README.md
            writeEntry(zos, "README.md", buildReadme(project, release, mappings, manifest));

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

            // placeholders for assets owned by sibling services — resolved in Phase 6.1
            @SuppressWarnings("unchecked")
            Map<String, Object> refs = (Map<String, Object>) manifest.getOrDefault("refs", Map.of());
            writeCrossServicePlaceholder(zos, "ontologies", refs);
            writeCrossServicePlaceholder(zos, "shapes", refs);
            writeCrossServicePlaceholder(zos, "validation-summary.json", refs);
            writeCrossServicePlaceholder(zos, "sample-queries.sparql", refs);
        }

        long size = Files.size(zipPath);
        if (size <= 0) {
            throw new IOException("Zip bundle was written with zero bytes");
        }
        return size;
    }

    private void writeCrossServicePlaceholder(ZipOutputStream zos, String path, Map<String, Object> refs)
            throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "NOT_YET_FETCHED");
        body.put("note", "Asset resolution for this kind requires a WebClient call to the owning "
            + "sibling service. Wired in Phase 6.1.");
        body.put("refs", refs);
        String filename = path.endsWith(".json") || path.endsWith(".sparql")
            ? path : path + "/.placeholder.json";
        writeEntry(zos, filename, path.endsWith(".sparql")
            ? "# Sample queries not yet fetched from saved-queries service.\n"
            : toJson(body));
    }

    private void writeEntry(ZipOutputStream zos, String name, String content) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        zos.putNextEntry(entry);
        zos.write(content.getBytes(StandardCharsets.UTF_8));
        zos.closeEntry();
    }

    private String buildReadme(ProjectEntity project, ReleaseEntity release,
                               List<MappingEntity> mappings, Map<String, Object> manifest) {
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
            e.getPublishedAt()
        );
    }
}
