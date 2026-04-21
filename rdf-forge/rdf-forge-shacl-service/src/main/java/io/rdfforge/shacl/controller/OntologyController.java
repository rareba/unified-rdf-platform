package io.rdfforge.shacl.controller;

import io.rdfforge.common.security.AuthUser;
import io.rdfforge.common.security.CurrentUser;
import io.rdfforge.shacl.entity.OntologyEntity.RdfFormat;
import io.rdfforge.shacl.ontology.OntologyService;
import io.rdfforge.shacl.ontology.dto.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST surface for the Ontology / Vocabulary Studio.
 *
 * <p>All endpoints expect the gateway to have already authenticated the caller
 * and forwarded X-User-* headers (handled by {@link CurrentUser}).
 */
@RestController
@RequestMapping("/api/v1/ontologies")
@RequiredArgsConstructor
@Tag(name = "Ontologies", description = "Ontology and vocabulary management")
@CrossOrigin(origins = "${app.cors.allowed-origins:http://localhost:4200}")
public class OntologyController {

    private final OntologyService ontologyService;

    @GetMapping
    @Operation(summary = "List project ontologies (metadata only)")
    public ResponseEntity<List<OntologyDto>> list(@RequestParam UUID projectId,
                                                  @CurrentUser AuthUser user) {
        return ResponseEntity.ok(ontologyService.listByProject(projectId, user));
    }

    @PostMapping("/import")
    @Operation(summary = "Import an ontology from raw RDF content")
    public ResponseEntity<OntologyDto> importOntology(@Valid @RequestBody OntologyImportRequest req,
                                                      @CurrentUser AuthUser user) {
        OntologyDto created = ontologyService.importOntology(req, user);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get ontology metadata")
    public ResponseEntity<OntologyDto> get(@PathVariable UUID id, @CurrentUser AuthUser user) {
        return ResponseEntity.ok(ontologyService.getMetadata(id, user));
    }

    @GetMapping("/{id}/content")
    @Operation(summary = "Download ontology content (optionally re-serialized)")
    public ResponseEntity<OntologyContentDto> getContent(@PathVariable UUID id,
                                                         @RequestParam(required = false) RdfFormat format,
                                                         @CurrentUser AuthUser user) {
        return ResponseEntity.ok(ontologyService.getContent(id, format, user));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update ontology metadata")
    public ResponseEntity<OntologyDto> update(@PathVariable UUID id,
                                              @RequestBody OntologyUpdateRequest req,
                                              @CurrentUser AuthUser user) {
        return ResponseEntity.ok(ontologyService.updateMetadata(id, req, user));
    }

    @PutMapping("/{id}/content")
    @Operation(summary = "Replace ontology content (bumps version)")
    public ResponseEntity<OntologyDto> updateContent(@PathVariable UUID id,
                                                     @Valid @RequestBody OntologyContentUpdateRequest req,
                                                     @CurrentUser AuthUser user) {
        return ResponseEntity.ok(ontologyService.updateContent(id, req, user));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an ontology")
    public ResponseEntity<Void> delete(@PathVariable UUID id, @CurrentUser AuthUser user) {
        ontologyService.delete(id, user);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/namespaces")
    @Operation(summary = "List the namespace prefix map declared by the ontology")
    public ResponseEntity<NamespaceMap> namespaces(@PathVariable UUID id, @CurrentUser AuthUser user) {
        return ResponseEntity.ok(ontologyService.namespaces(id, user));
    }

    @GetMapping("/{id}/classes")
    @Operation(summary = "List owl:Class / rdfs:Class URIs (optionally filtered)")
    public ResponseEntity<List<TermSearchResult>> classes(@PathVariable UUID id,
                                                          @RequestParam(name = "q", required = false) String q,
                                                          @RequestParam(defaultValue = "50") int limit,
                                                          @CurrentUser AuthUser user) {
        return ResponseEntity.ok(ontologyService.searchTerms(id, TermType.CLASS, q, limit, user));
    }

    @GetMapping("/{id}/properties")
    @Operation(summary = "List rdf:Property / owl:*Property URIs (optionally filtered)")
    public ResponseEntity<List<TermSearchResult>> properties(@PathVariable UUID id,
                                                             @RequestParam(name = "q", required = false) String q,
                                                             @RequestParam(defaultValue = "50") int limit,
                                                             @CurrentUser AuthUser user) {
        return ResponseEntity.ok(ontologyService.searchTerms(id, TermType.PROPERTY, q, limit, user));
    }

    @GetMapping("/{id}/skos-concepts")
    @Operation(summary = "List skos:Concept URIs (optionally filtered)")
    public ResponseEntity<List<TermSearchResult>> skosConcepts(@PathVariable UUID id,
                                                               @RequestParam(name = "q", required = false) String q,
                                                               @RequestParam(defaultValue = "50") int limit,
                                                               @CurrentUser AuthUser user) {
        return ResponseEntity.ok(ontologyService.searchTerms(id, TermType.SKOS_CONCEPT, q, limit, user));
    }

    @GetMapping("/{id}/term")
    @Operation(summary = "Fetch full detail for a single term URI")
    public ResponseEntity<TermDetail> term(@PathVariable UUID id,
                                           @RequestParam String uri,
                                           @CurrentUser AuthUser user) {
        return ResponseEntity.ok(ontologyService.getTerm(id, uri, user));
    }

    @PostMapping("/{id}/validate")
    @Operation(summary = "Re-parse content and report syntax errors")
    public ResponseEntity<OntologyValidationResult> validate(@PathVariable UUID id,
                                                             @CurrentUser AuthUser user) {
        return ResponseEntity.ok(ontologyService.validateContent(id, user));
    }
}
