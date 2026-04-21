package io.rdfforge.shacl.ontology;

import io.rdfforge.common.exception.RdfForgeException;
import io.rdfforge.common.exception.ResourceNotFoundException;
import io.rdfforge.common.security.AuthUser;
import io.rdfforge.shacl.entity.OntologyEntity;
import io.rdfforge.shacl.entity.OntologyEntity.RdfFormat;
import io.rdfforge.shacl.ontology.dto.*;
import io.rdfforge.shacl.repository.OntologyRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.rdf.model.Model;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Stream;

/**
 * Orchestrates ontology CRUD, import/export, and term search.
 *
 * <p>Authorization follows the existing SHACL service pattern:
 * {@code createdBy == caller.id() || caller.isAdmin()} for mutations, and the
 * same predicate for reads (we do NOT treat ontologies as "public catalog"
 * items the way we do template shapes).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OntologyService {

    private final OntologyRepository repository;
    private final OntologyParserService parser;

    // ------------------------------------------------------------------
    // Read
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<OntologyDto> listByProject(UUID projectId, AuthUser user) {
        if (projectId == null) {
            throw new RdfForgeException("BAD_REQUEST", "projectId is required", HttpStatus.BAD_REQUEST);
        }
        return repository.findByProjectId(projectId).stream()
            .filter(e -> canRead(e, user))
            .map(OntologyService::toDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public OntologyDto getMetadata(UUID id, AuthUser user) {
        return toDto(requireReadable(id, user));
    }

    @Transactional(readOnly = true)
    public OntologyContentDto getContent(UUID id, RdfFormat requestedFormat, AuthUser user) {
        OntologyEntity entity = requireReadable(id, user);
        if (requestedFormat == null || requestedFormat == entity.getFormat()) {
            return OntologyContentDto.builder()
                .id(entity.getId())
                .name(entity.getName())
                .format(entity.getFormat())
                .content(entity.getContent())
                .build();
        }
        // Re-serialize into the requested format.
        Model model = parser.parse(entity.getContent(), entity.getFormat());
        String reserialized = parser.serialize(model, requestedFormat);
        return OntologyContentDto.builder()
            .id(entity.getId())
            .name(entity.getName())
            .format(requestedFormat)
            .content(reserialized)
            .build();
    }

    // ------------------------------------------------------------------
    // Create / update
    // ------------------------------------------------------------------

    @Transactional
    public OntologyDto importOntology(OntologyImportRequest req, AuthUser user) {
        requireAuthenticated(user);
        if (repository.existsByProjectIdAndName(req.projectId(), req.name())) {
            throw new RdfForgeException(
                "DUPLICATE_NAME",
                "An ontology named '" + req.name() + "' already exists in this project",
                HttpStatus.CONFLICT);
        }

        String rawContent = decodeContent(req.content());
        Model model = parser.parse(rawContent, req.format());
        String namespace = Optional.ofNullable(req.namespace())
            .filter(s -> !s.isBlank())
            .orElseGet(() -> inferNamespace(model));

        Map<String, Object> meta = new HashMap<>();
        meta.put("tripleCount", parser.countStatements(model));
        meta.put("classCount", parser.listClasses(model).count());
        meta.put("propertyCount", parser.listProperties(model).count());
        meta.put("skosConceptCount", parser.listSkosConcepts(model).count());

        OntologyEntity entity = OntologyEntity.builder()
            .projectId(req.projectId())
            .name(req.name())
            .description(req.description())
            .namespace(namespace)
            .prefix(req.prefix())
            .format(req.format())
            .content(rawContent)
            .version(1)
            .metadata(meta)
            .createdBy(user.id())
            .build();

        entity = repository.save(entity);
        log.info("Imported ontology {} ({}) into project {} — {} triples",
            entity.getName(), entity.getId(), entity.getProjectId(), parser.countStatements(model));
        return toDto(entity);
    }

    @Transactional
    public OntologyDto updateMetadata(UUID id, OntologyUpdateRequest req, AuthUser user) {
        OntologyEntity entity = requireWritable(id, user);
        if (req.name() != null && !req.name().isBlank() && !req.name().equals(entity.getName())) {
            if (repository.existsByProjectIdAndName(entity.getProjectId(), req.name())) {
                throw new RdfForgeException(
                    "DUPLICATE_NAME",
                    "An ontology named '" + req.name() + "' already exists in this project",
                    HttpStatus.CONFLICT);
            }
            entity.setName(req.name());
        }
        if (req.description() != null) entity.setDescription(req.description());
        if (req.namespace() != null && !req.namespace().isBlank()) entity.setNamespace(req.namespace());
        if (req.prefix() != null) entity.setPrefix(req.prefix());
        return toDto(repository.save(entity));
    }

    @Transactional
    public OntologyDto updateContent(UUID id, OntologyContentUpdateRequest req, AuthUser user) {
        OntologyEntity entity = requireWritable(id, user);
        String rawContent = decodeContent(req.content());
        Model model = parser.parse(rawContent, req.format());

        Map<String, Object> meta = entity.getMetadata() != null
            ? new HashMap<>(entity.getMetadata())
            : new HashMap<>();
        meta.put("tripleCount", parser.countStatements(model));
        meta.put("classCount", parser.listClasses(model).count());
        meta.put("propertyCount", parser.listProperties(model).count());
        meta.put("skosConceptCount", parser.listSkosConcepts(model).count());

        entity.setContent(rawContent);
        entity.setFormat(req.format());
        entity.setVersion((entity.getVersion() == null ? 1 : entity.getVersion()) + 1);
        entity.setMetadata(meta);
        return toDto(repository.save(entity));
    }

    @Transactional
    public void delete(UUID id, AuthUser user) {
        OntologyEntity entity = requireWritable(id, user);
        repository.delete(entity);
        log.info("Deleted ontology {} ({})", entity.getName(), entity.getId());
    }

    // ------------------------------------------------------------------
    // Introspection
    // ------------------------------------------------------------------

    @Transactional(readOnly = true)
    public NamespaceMap namespaces(UUID id, AuthUser user) {
        OntologyEntity entity = requireReadable(id, user);
        return parser.extractNamespaces(parser.parse(entity.getContent(), entity.getFormat()));
    }

    @Transactional(readOnly = true)
    public List<TermSearchResult> searchTerms(UUID id, TermType type, String query, int limit, AuthUser user) {
        OntologyEntity entity = requireReadable(id, user);
        Model model = parser.parse(entity.getContent(), entity.getFormat());
        Stream<String> uris = switch (type) {
            case CLASS -> parser.listClasses(model);
            case PROPERTY -> parser.listProperties(model);
            case SKOS_CONCEPT -> parser.listSkosConcepts(model);
        };
        String typeName = type.name();
        int effectiveLimit = limit <= 0 ? 50 : Math.min(limit, 500);
        String needle = query == null ? null : query.toLowerCase(Locale.ROOT).trim();

        return uris
            .map(uri -> parser.toSearchResult(model, uri, typeName))
            .filter(t -> needle == null || needle.isBlank() || matches(t, needle))
            .limit(effectiveLimit)
            .toList();
    }

    @Transactional(readOnly = true)
    public TermDetail getTerm(UUID id, String uri, AuthUser user) {
        if (uri == null || uri.isBlank()) {
            throw new RdfForgeException("BAD_REQUEST", "uri is required", HttpStatus.BAD_REQUEST);
        }
        OntologyEntity entity = requireReadable(id, user);
        Model model = parser.parse(entity.getContent(), entity.getFormat());
        return parser.getTermDetail(model, uri);
    }

    @Transactional(readOnly = true)
    public OntologyValidationResult validateContent(UUID id, AuthUser user) {
        OntologyEntity entity = requireReadable(id, user);
        try {
            Model model = parser.parse(entity.getContent(), entity.getFormat());
            return OntologyValidationResult.builder()
                .valid(true)
                .errors(List.of())
                .tripleCount(parser.countStatements(model))
                .build();
        } catch (OntologyParseException ex) {
            return OntologyValidationResult.builder()
                .valid(false)
                .errors(List.of(ex.getMessage()))
                .tripleCount(0)
                .build();
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private OntologyEntity requireReadable(UUID id, AuthUser user) {
        OntologyEntity e = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Ontology", id.toString()));
        if (!canRead(e, user)) {
            throw new AccessDeniedException("Not authorized to read this ontology");
        }
        return e;
    }

    private OntologyEntity requireWritable(UUID id, AuthUser user) {
        OntologyEntity e = repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Ontology", id.toString()));
        if (!canWrite(e, user)) {
            throw new AccessDeniedException("Not authorized to modify this ontology");
        }
        return e;
    }

    private static boolean canRead(OntologyEntity e, AuthUser user) {
        if (user == null || user.isAnonymous()) return false;
        return user.ownsOrIsAdmin(e.getCreatedBy());
    }

    private static boolean canWrite(OntologyEntity e, AuthUser user) {
        if (user == null || user.isAnonymous()) return false;
        return user.ownsOrIsAdmin(e.getCreatedBy());
    }

    private static void requireAuthenticated(AuthUser user) {
        if (user == null || user.isAnonymous() || user.id() == null) {
            throw new AccessDeniedException("Authentication required");
        }
    }

    private static boolean matches(TermSearchResult t, String needle) {
        if (t.uri() != null && t.uri().toLowerCase(Locale.ROOT).contains(needle)) return true;
        if (t.label() != null && t.label().toLowerCase(Locale.ROOT).contains(needle)) return true;
        if (t.comment() != null && t.comment().toLowerCase(Locale.ROOT).contains(needle)) return true;
        if (t.altLabels() != null) {
            for (String alt : t.altLabels()) {
                if (alt != null && alt.toLowerCase(Locale.ROOT).contains(needle)) return true;
            }
        }
        return false;
    }

    /**
     * Decode ontology content. If the string looks like base64 (no whitespace,
     * only b64 chars, and is non-trivial in length) we decode it — otherwise
     * we pass it through as raw text.
     */
    static String decodeContent(String content) {
        if (content == null) return "";
        String trimmed = content.trim();
        if (trimmed.isEmpty()) return "";
        // Heuristic: base64 payloads have no whitespace, no '<', no '@', no '.'
        // Turtle, RDF/XML and JSON-LD all contain one of those within the first
        // few characters of anything meaningful, so we fall through to raw.
        if (trimmed.length() > 32
                && trimmed.matches("^[A-Za-z0-9+/=\\r\\n]+$")) {
            try {
                byte[] decoded = Base64.getMimeDecoder().decode(trimmed);
                return new String(decoded, java.nio.charset.StandardCharsets.UTF_8);
            } catch (IllegalArgumentException ignored) {
                // fall through
            }
        }
        return content;
    }

    private static String inferNamespace(Model model) {
        String defaultNs = model.getNsPrefixURI("");
        if (defaultNs != null && !defaultNs.isBlank()) return defaultNs;
        // Fall back to any declared prefix — ordering is stable with LinkedHashMap
        // which Jena returns from getNsPrefixMap().
        for (Map.Entry<String, String> entry : model.getNsPrefixMap().entrySet()) {
            if (entry.getValue() != null && !entry.getValue().isBlank()) {
                return entry.getValue();
            }
        }
        return "urn:rdfforge:ontology:unknown#";
    }

    private static OntologyDto toDto(OntologyEntity e) {
        return OntologyDto.builder()
            .id(e.getId())
            .projectId(e.getProjectId())
            .name(e.getName())
            .description(e.getDescription())
            .namespace(e.getNamespace())
            .prefix(e.getPrefix())
            .format(e.getFormat())
            .version(e.getVersion())
            .metadata(e.getMetadata())
            .createdBy(e.getCreatedBy())
            .createdAt(e.getCreatedAt())
            .updatedAt(e.getUpdatedAt())
            .build();
    }
}
