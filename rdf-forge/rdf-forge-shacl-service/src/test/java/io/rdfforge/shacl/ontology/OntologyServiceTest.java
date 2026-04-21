package io.rdfforge.shacl.ontology;

import io.rdfforge.common.exception.RdfForgeException;
import io.rdfforge.common.exception.ResourceNotFoundException;
import io.rdfforge.common.security.AuthUser;
import io.rdfforge.shacl.entity.OntologyEntity;
import io.rdfforge.shacl.entity.OntologyEntity.RdfFormat;
import io.rdfforge.shacl.ontology.dto.OntologyContentUpdateRequest;
import io.rdfforge.shacl.ontology.dto.OntologyDto;
import io.rdfforge.shacl.ontology.dto.OntologyImportRequest;
import io.rdfforge.shacl.ontology.dto.OntologyUpdateRequest;
import io.rdfforge.shacl.repository.OntologyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OntologyService Tests")
class OntologyServiceTest {

    @Mock private OntologyRepository repository;

    private OntologyParserService parser;
    private OntologyService service;

    private UUID ontologyId;
    private UUID projectId;
    private UUID ownerId;
    private AuthUser owner;
    private AuthUser stranger;
    private AuthUser admin;
    private OntologyEntity entity;

    private static final String TURTLE = """
        @prefix owl: <http://www.w3.org/2002/07/owl#> .
        @prefix ex: <http://example.org/schema/> .
        ex:Person a owl:Class .
        """;

    @BeforeEach
    void setUp() {
        parser = new OntologyParserService();
        service = new OntologyService(repository, parser);

        ontologyId = UUID.randomUUID();
        projectId = UUID.randomUUID();
        ownerId = UUID.randomUUID();
        owner = new AuthUser(ownerId, "owner@example.com", Set.of("USER"));
        stranger = new AuthUser(UUID.randomUUID(), "stranger@example.com", Set.of("USER"));
        admin = new AuthUser(UUID.randomUUID(), "admin@example.com", Set.of("ADMIN"));

        entity = OntologyEntity.builder()
            .id(ontologyId)
            .projectId(projectId)
            .name("Sample Ontology")
            .namespace("http://example.org/schema/")
            .prefix("ex")
            .format(RdfFormat.TURTLE)
            .content(TURTLE)
            .version(1)
            .createdBy(ownerId)
            .build();
    }

    @Test
    @DisplayName("importOntology stores parsed content and stamps createdBy")
    void importOntology_storesEntity() {
        OntologyImportRequest req = new OntologyImportRequest(
            projectId, "My Ontology", "desc",
            RdfFormat.TURTLE, TURTLE,
            "http://example.org/schema/", "ex");

        when(repository.existsByProjectIdAndName(projectId, "My Ontology")).thenReturn(false);
        when(repository.save(any(OntologyEntity.class)))
            .thenAnswer(inv -> {
                OntologyEntity e = inv.getArgument(0);
                e.setId(UUID.randomUUID());
                return e;
            });

        OntologyDto dto = service.importOntology(req, owner);

        assertNotNull(dto);
        assertEquals("My Ontology", dto.name());
        assertEquals(ownerId, dto.createdBy());
        assertEquals(1, dto.version());
        verify(repository).save(any(OntologyEntity.class));
    }

    @Test
    @DisplayName("importOntology rejects duplicate name in same project")
    void importOntology_duplicateName_throws() {
        OntologyImportRequest req = new OntologyImportRequest(
            projectId, "Dup", null, RdfFormat.TURTLE, TURTLE, null, null);
        when(repository.existsByProjectIdAndName(projectId, "Dup")).thenReturn(true);

        RdfForgeException ex = assertThrows(RdfForgeException.class,
            () -> service.importOntology(req, owner));
        assertEquals("DUPLICATE_NAME", ex.getErrorCode());
        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("importOntology rejects anonymous caller")
    void importOntology_anonymous_throws() {
        OntologyImportRequest req = new OntologyImportRequest(
            projectId, "X", null, RdfFormat.TURTLE, TURTLE, null, null);
        assertThrows(AccessDeniedException.class,
            () -> service.importOntology(req, AuthUser.anonymous()));
    }

    @Test
    @DisplayName("importOntology fails on invalid content")
    void importOntology_invalidContent_throws() {
        OntologyImportRequest req = new OntologyImportRequest(
            projectId, "Bad", null, RdfFormat.TURTLE, "not turtle ???", null, null);
        when(repository.existsByProjectIdAndName(any(), any())).thenReturn(false);
        assertThrows(OntologyParseException.class,
            () -> service.importOntology(req, owner));
    }

    @Test
    @DisplayName("getMetadata throws for non-existent id")
    void getMetadata_notFound_throws() {
        when(repository.findById(ontologyId)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class,
            () -> service.getMetadata(ontologyId, owner));
    }

    @Test
    @DisplayName("getMetadata forbids non-owner, non-admin")
    void getMetadata_stranger_forbidden() {
        when(repository.findById(ontologyId)).thenReturn(Optional.of(entity));
        assertThrows(AccessDeniedException.class,
            () -> service.getMetadata(ontologyId, stranger));
    }

    @Test
    @DisplayName("getMetadata allows the owner")
    void getMetadata_owner_allowed() {
        when(repository.findById(ontologyId)).thenReturn(Optional.of(entity));
        OntologyDto dto = service.getMetadata(ontologyId, owner);
        assertEquals(entity.getName(), dto.name());
    }

    @Test
    @DisplayName("getMetadata allows an admin")
    void getMetadata_admin_allowed() {
        when(repository.findById(ontologyId)).thenReturn(Optional.of(entity));
        OntologyDto dto = service.getMetadata(ontologyId, admin);
        assertEquals(entity.getName(), dto.name());
    }

    @Test
    @DisplayName("listByProject returns only readable entities")
    void listByProject_returnsOwnedOnly() {
        OntologyEntity other = OntologyEntity.builder()
            .id(UUID.randomUUID())
            .projectId(projectId)
            .name("Someone Else")
            .namespace("http://x/")
            .format(RdfFormat.TURTLE)
            .content(TURTLE)
            .version(1)
            .createdBy(UUID.randomUUID())
            .build();
        when(repository.findByProjectId(projectId)).thenReturn(List.of(entity, other));

        List<OntologyDto> result = service.listByProject(projectId, owner);
        assertEquals(1, result.size());
        assertEquals(entity.getName(), result.get(0).name());
    }

    @Test
    @DisplayName("delete rejects non-owner")
    void delete_stranger_throws() {
        when(repository.findById(ontologyId)).thenReturn(Optional.of(entity));
        assertThrows(AccessDeniedException.class,
            () -> service.delete(ontologyId, stranger));
        verify(repository, never()).delete(any());
    }

    @Test
    @DisplayName("delete allows owner")
    void delete_owner_allowed() {
        when(repository.findById(ontologyId)).thenReturn(Optional.of(entity));
        service.delete(ontologyId, owner);
        verify(repository).delete(entity);
    }

    @Test
    @DisplayName("updateContent bumps version")
    void updateContent_incrementsVersion() {
        when(repository.findById(ontologyId)).thenReturn(Optional.of(entity));
        when(repository.save(any(OntologyEntity.class)))
            .thenAnswer(inv -> inv.getArgument(0));

        OntologyDto dto = service.updateContent(ontologyId,
            new OntologyContentUpdateRequest(TURTLE, RdfFormat.TURTLE), owner);

        assertEquals(2, dto.version());
    }

    @Test
    @DisplayName("updateMetadata rejects rename that collides with existing")
    void updateMetadata_duplicateName_throws() {
        when(repository.findById(ontologyId)).thenReturn(Optional.of(entity));
        when(repository.existsByProjectIdAndName(projectId, "Taken")).thenReturn(true);

        RdfForgeException ex = assertThrows(RdfForgeException.class,
            () -> service.updateMetadata(ontologyId,
                new OntologyUpdateRequest("Taken", null, null, null), owner));
        assertEquals("DUPLICATE_NAME", ex.getErrorCode());
    }
}
