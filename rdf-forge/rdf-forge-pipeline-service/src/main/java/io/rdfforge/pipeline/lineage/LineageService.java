package io.rdfforge.pipeline.lineage;

import io.rdfforge.common.exception.PipelineValidationException;
import io.rdfforge.common.exception.ResourceNotFoundException;
import io.rdfforge.common.security.AuthUser;
import io.rdfforge.pipeline.dto.LineageDto;
import io.rdfforge.pipeline.entity.MappingEntity;
import io.rdfforge.pipeline.entity.PipelineEntity;
import io.rdfforge.pipeline.entity.ProjectEntity;
import io.rdfforge.pipeline.entity.ReleaseEntity;
import io.rdfforge.pipeline.repository.MappingRepository;
import io.rdfforge.pipeline.repository.PipelineRepository;
import io.rdfforge.pipeline.repository.ProjectRepository;
import io.rdfforge.pipeline.repository.ReleaseRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Builds a {@link LineageDto} for a project — or a focused sub-graph around a
 * single resource — from the entities this module owns.
 *
 * <p>v1 scope: pipeline-service entities only. Data-source, shape, ontology,
 * job and triplestore nodes are surfaced via the IDs captured in
 * manifests/mapping-rule sources/release manifests, so the UI gets a usable
 * graph even before sibling services expose count/list-by-project endpoints.
 * TODO(Phase 6.1): fan out via WebClient to resolve labels for those nodes
 * rather than showing the UUID.
 */
@Slf4j
@Service
public class LineageService {

    private static final String PREFIX_PROJECT = "uuid:project-";
    private static final String PREFIX_DATA_SOURCE = "uuid:data-";
    private static final String PREFIX_MAPPING = "uuid:mapping-";
    private static final String PREFIX_ONTOLOGY = "uuid:ontology-";
    private static final String PREFIX_SHAPE = "uuid:shape-";
    private static final String PREFIX_PIPELINE = "uuid:pipeline-";
    private static final String PREFIX_JOB = "uuid:job-";
    private static final String PREFIX_TRIPLESTORE = "uuid:triplestore-";
    private static final String PREFIX_RELEASE = "uuid:release-";

    private final ProjectRepository projectRepository;
    private final MappingRepository mappingRepository;
    private final PipelineRepository pipelineRepository;
    private final ReleaseRepository releaseRepository;

    public LineageService(ProjectRepository projectRepository,
                          MappingRepository mappingRepository,
                          PipelineRepository pipelineRepository,
                          ReleaseRepository releaseRepository) {
        this.projectRepository = projectRepository;
        this.mappingRepository = mappingRepository;
        this.pipelineRepository = pipelineRepository;
        this.releaseRepository = releaseRepository;
    }

    // ─────────────────────────── project graph ───────────────────────────

    @Transactional(readOnly = true)
    public LineageDto forProject(UUID projectId, AuthUser user) {
        requireAuthenticated(user);
        if (projectId == null) {
            throw new PipelineValidationException("projectId is required");
        }
        ProjectEntity project = projectRepository.findById(projectId)
            .orElseThrow(() -> new ResourceNotFoundException("Project", projectId.toString()));
        requireOwnerOrAdmin(project, user);

        List<LineageDto.Node> nodes = new ArrayList<>();
        List<LineageDto.Edge> edges = new ArrayList<>();
        Set<String> seenNodes = new LinkedHashSet<>();

        // 1. Project node (root)
        String projectNodeId = PREFIX_PROJECT + project.getId();
        addNode(nodes, seenNodes, new LineageDto.Node(
            projectNodeId, LineageDto.NodeKind.PROJECT, project.getName(),
            project.getUpdatedAt(),
            Map.of("baseUri", project.getBaseUri() == null ? "" : project.getBaseUri())
        ));

        // 2. Mappings + referenced data sources
        List<MappingEntity> mappings = mappingRepository.findByProjectIdOrderByUpdatedAtDesc(projectId);
        for (MappingEntity m : mappings) {
            String mappingId = PREFIX_MAPPING + m.getId();
            addNode(nodes, seenNodes, new LineageDto.Node(
                mappingId, LineageDto.NodeKind.MAPPING, m.getName(),
                m.getUpdatedAt(),
                Map.of(
                    "version", m.getVersion(),
                    "type", m.getMappingType() == null ? "GENERIC" : m.getMappingType().name(),
                    "sourceType", m.getSourceType() == null ? "" : m.getSourceType().name()
                )
            ));
            edges.add(new LineageDto.Edge(
                mappingId, projectNodeId, LineageDto.EdgeKind.BELONGS_TO, Map.of()));

            // Derive data-source ids from sourceConfig when present. The Mapping
            // Studio stores {"sourceDataRef": "<uuid>"} or a direct sourceId so
            // treat either as the dataSource edge.
            UUID dataSourceId = extractDataSourceId(m);
            if (dataSourceId != null) {
                String dsNode = PREFIX_DATA_SOURCE + dataSourceId;
                addNode(nodes, seenNodes, new LineageDto.Node(
                    dsNode, LineageDto.NodeKind.DATA_SOURCE,
                    "data-source:" + shortId(dataSourceId),
                    m.getUpdatedAt(),
                    Map.of("sourceType", m.getSourceType() == null ? "" : m.getSourceType().name())
                ));
                edges.add(new LineageDto.Edge(
                    mappingId, dsNode, LineageDto.EdgeKind.USED_BY, Map.of()));
                edges.add(new LineageDto.Edge(
                    dsNode, projectNodeId, LineageDto.EdgeKind.BELONGS_TO, Map.of()));
            }

            // Ontology references are captured in targetOntologies.prefixes {prefix:uri}.
            // We don't have ontology entity UUIDs in this module — surface the prefixes
            // as REFERENCES edges to synthetic ontology nodes keyed by URI. The UI will
            // resolve labels in Phase 6.1.
            addOntologyRefsFromMapping(m, mappingId, nodes, edges, seenNodes);
        }

        // 3. Pipelines
        List<PipelineEntity> pipelines = pipelineRepository.findByProjectIdOrderByUpdatedAtDesc(projectId);
        for (PipelineEntity p : pipelines) {
            String pipelineId = PREFIX_PIPELINE + p.getId();
            Map<String, Object> pAttrs = new LinkedHashMap<>();
            pAttrs.put("version", p.getVersion() == null ? 0 : p.getVersion());
            pAttrs.put("isTemplate", p.getIsTemplate() != null && p.getIsTemplate());
            addNode(nodes, seenNodes, new LineageDto.Node(
                pipelineId, LineageDto.NodeKind.PIPELINE, p.getName(),
                p.getUpdatedAt(),
                pAttrs
            ));
            edges.add(new LineageDto.Edge(
                pipelineId, projectNodeId, LineageDto.EdgeKind.BELONGS_TO, Map.of()));
        }

        // 4. Releases + their manifest-declared links
        List<ReleaseEntity> releases = releaseRepository.findByProjectIdOrderByCreatedAtDesc(projectId);
        for (ReleaseEntity r : releases) {
            String releaseId = PREFIX_RELEASE + r.getId();
            Map<String, Object> attrs = new HashMap<>();
            attrs.put("version", r.getVersion());
            attrs.put("status", r.getStatus() == null ? "" : r.getStatus().name());
            if (r.getPublishedAt() != null) {
                attrs.put("publishedAt", r.getPublishedAt().toString());
            }
            addNode(nodes, seenNodes, new LineageDto.Node(
                releaseId, LineageDto.NodeKind.RELEASE,
                r.getName() + " " + r.getVersion(),
                r.getUpdatedAt(),
                attrs
            ));
            edges.add(new LineageDto.Edge(
                releaseId, projectNodeId, LineageDto.EdgeKind.BELONGS_TO, Map.of()));
            addReleaseManifestEdges(r, releaseId, nodes, edges, seenNodes);
        }

        return new LineageDto(projectId, List.copyOf(nodes), List.copyOf(edges));
    }

    // ─────────────────────────── resource sub-graph ───────────────────────

    /**
     * Returns a focused graph containing the requested node and its direct
     * neighbours (one hop upstream + downstream). For unknown kinds the
     * result contains just the root node with no edges.
     */
    @Transactional(readOnly = true)
    public LineageDto forResource(String kind, UUID id, AuthUser user) {
        requireAuthenticated(user);
        if (id == null) throw new PipelineValidationException("id is required");
        if (kind == null || kind.isBlank()) throw new PipelineValidationException("kind is required");

        // We rebuild the project graph then filter to the neighbourhood, which is
        // correct and simple at the scales we expect (tens of nodes per project).
        UUID projectId = resolveProjectForResource(kind, id);
        LineageDto full = forProject(projectId, user);

        String focusId = prefixFor(kind) + id;
        List<LineageDto.Node> filteredNodes = new ArrayList<>();
        List<LineageDto.Edge> filteredEdges = new ArrayList<>();
        Set<String> neighborIds = new LinkedHashSet<>();
        neighborIds.add(focusId);
        for (LineageDto.Edge e : full.edges()) {
            if (e.from().equals(focusId) || e.to().equals(focusId)) {
                filteredEdges.add(e);
                neighborIds.add(e.from());
                neighborIds.add(e.to());
            }
        }
        for (LineageDto.Node n : full.nodes()) {
            if (neighborIds.contains(n.id())) filteredNodes.add(n);
        }
        return new LineageDto(full.projectId(), filteredNodes, filteredEdges);
    }

    // ─────────────────────────── helpers ───────────────────────────

    private void addNode(List<LineageDto.Node> out, Set<String> seen, LineageDto.Node node) {
        if (seen.add(node.id())) out.add(node);
    }

    @SuppressWarnings("unchecked")
    private UUID extractDataSourceId(MappingEntity m) {
        if (m.getSourceConfig() == null) return null;
        Object ref = m.getSourceConfig().get("sourceDataRef");
        if (ref == null) ref = m.getSourceConfig().get("sourceId");
        if (ref == null) ref = m.getSourceConfig().get("dataSourceId");
        if (ref == null) return null;
        try {
            return UUID.fromString(ref.toString());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private void addOntologyRefsFromMapping(MappingEntity m, String mappingNodeId,
                                            List<LineageDto.Node> nodes,
                                            List<LineageDto.Edge> edges,
                                            Set<String> seen) {
        if (m.getTargetOntologies() == null) return;
        Object p = m.getTargetOntologies().get("prefixes");
        if (!(p instanceof Map<?, ?> prefixes)) return;
        for (Map.Entry<?, ?> e : prefixes.entrySet()) {
            String prefix = Objects.toString(e.getKey(), null);
            String uri = Objects.toString(e.getValue(), null);
            if (uri == null || uri.isBlank()) continue;
            // Surrogate id derived from the ontology URI so the UI has stable keys.
            String ontNodeId = PREFIX_ONTOLOGY + uri.hashCode();
            addNode(nodes, seen, new LineageDto.Node(
                ontNodeId, LineageDto.NodeKind.ONTOLOGY,
                prefix == null ? uri : (prefix + ": " + uri),
                m.getUpdatedAt(),
                Map.of("uri", uri, "prefix", prefix == null ? "" : prefix)
            ));
            edges.add(new LineageDto.Edge(
                mappingNodeId, ontNodeId, LineageDto.EdgeKind.REFERENCES,
                Map.of("via", "prefixes")));
        }
    }

    @SuppressWarnings("unchecked")
    private void addReleaseManifestEdges(ReleaseEntity r, String releaseNodeId,
                                         List<LineageDto.Node> nodes,
                                         List<LineageDto.Edge> edges,
                                         Set<String> seen) {
        if (r.getManifest() == null) return;
        Object refsRaw = r.getManifest().get("refs");
        if (!(refsRaw instanceof Map<?, ?> refs)) return;
        addRefEdges(refs, "mappings", releaseNodeId,
            LineageDto.EdgeKind.DERIVED_FROM, LineageDto.NodeKind.MAPPING,
            PREFIX_MAPPING, nodes, edges, seen);
        addRefEdges(refs, "dataSources", releaseNodeId,
            LineageDto.EdgeKind.DERIVED_FROM, LineageDto.NodeKind.DATA_SOURCE,
            PREFIX_DATA_SOURCE, nodes, edges, seen);
        addRefEdges(refs, "shapes", releaseNodeId,
            LineageDto.EdgeKind.DERIVED_FROM, LineageDto.NodeKind.SHAPE,
            PREFIX_SHAPE, nodes, edges, seen);
        addRefEdges(refs, "ontologies", releaseNodeId,
            LineageDto.EdgeKind.DERIVED_FROM, LineageDto.NodeKind.ONTOLOGY,
            PREFIX_ONTOLOGY, nodes, edges, seen);
        addRefEdges(refs, "validationSuiteIds", releaseNodeId,
            LineageDto.EdgeKind.VALIDATED_BY, LineageDto.NodeKind.SHAPE,
            PREFIX_SHAPE, nodes, edges, seen);

        Object tsRaw = refs.get("triplestoreId");
        if (tsRaw != null && !tsRaw.toString().isBlank()) {
            String tsId = PREFIX_TRIPLESTORE + tsRaw;
            addNode(nodes, seen, new LineageDto.Node(
                tsId, LineageDto.NodeKind.TRIPLESTORE,
                "triplestore:" + tsRaw,
                r.getUpdatedAt(), Map.of()));
            edges.add(new LineageDto.Edge(
                releaseNodeId, tsId, LineageDto.EdgeKind.PRODUCED, Map.of()));
        }
    }

    private void addRefEdges(Map<?, ?> refs, String key, String fromNode,
                             LineageDto.EdgeKind edgeKind,
                             LineageDto.NodeKind targetKind, String prefix,
                             List<LineageDto.Node> nodes,
                             List<LineageDto.Edge> edges,
                             Set<String> seen) {
        Object v = refs.get(key);
        if (!(v instanceof List<?> list)) return;
        for (Object o : list) {
            if (o == null) continue;
            String nodeId = prefix + o;
            addNode(nodes, seen, new LineageDto.Node(
                nodeId, targetKind, targetKind.name().toLowerCase() + ":" + shortString(o.toString()),
                null, Map.of()));
            edges.add(new LineageDto.Edge(fromNode, nodeId, edgeKind, Map.of()));
        }
    }

    /**
     * Resolve the owning project of a resource so {@link #forResource} can
     * reuse the project-scoped graph. Only kinds we manage in this module are
     * authoritatively resolvable; for unknown kinds we throw NotFound so the
     * controller returns 404 rather than 500.
     */
    private UUID resolveProjectForResource(String kind, UUID id) {
        return switch (kind.toUpperCase()) {
            case "PROJECT" -> id;
            case "MAPPING" -> mappingRepository.findById(id)
                .map(MappingEntity::getProjectId)
                .orElseThrow(() -> new ResourceNotFoundException("Mapping", id.toString()));
            case "PIPELINE" -> pipelineRepository.findById(id)
                .map(PipelineEntity::getProjectId)
                .orElseThrow(() -> new ResourceNotFoundException("Pipeline", id.toString()));
            case "RELEASE" -> releaseRepository.findById(id)
                .map(ReleaseEntity::getProjectId)
                .orElseThrow(() -> new ResourceNotFoundException("Release", id.toString()));
            default -> throw new PipelineValidationException(
                "Unsupported lineage kind: " + kind
                    + ". Supported: PROJECT, MAPPING, PIPELINE, RELEASE");
        };
    }

    private static String prefixFor(String kind) {
        return switch (kind.toUpperCase()) {
            case "PROJECT" -> PREFIX_PROJECT;
            case "DATA_SOURCE" -> PREFIX_DATA_SOURCE;
            case "MAPPING" -> PREFIX_MAPPING;
            case "ONTOLOGY" -> PREFIX_ONTOLOGY;
            case "SHAPE" -> PREFIX_SHAPE;
            case "PIPELINE" -> PREFIX_PIPELINE;
            case "JOB" -> PREFIX_JOB;
            case "TRIPLESTORE" -> PREFIX_TRIPLESTORE;
            case "RELEASE" -> PREFIX_RELEASE;
            default -> "uuid:";
        };
    }

    private static String shortId(UUID id) {
        String s = id.toString();
        return s.length() > 8 ? s.substring(0, 8) : s;
    }

    private static String shortString(String s) {
        return s.length() > 12 ? s.substring(0, 12) : s;
    }

    private static void requireAuthenticated(AuthUser user) {
        if (user == null || user.isAnonymous()) {
            throw new AccessDeniedException("Authentication required");
        }
    }

    private static void requireOwnerOrAdmin(ProjectEntity project, AuthUser user) {
        if (user.isAdmin()) return;
        if (!Objects.equals(project.getCreatedBy(), user.id())) {
            throw new AccessDeniedException("Not authorized to view lineage for this project");
        }
    }

}
