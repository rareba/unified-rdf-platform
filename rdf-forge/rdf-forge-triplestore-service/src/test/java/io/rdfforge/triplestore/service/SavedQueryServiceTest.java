package io.rdfforge.triplestore.service;

import io.rdfforge.common.exception.ResourceNotFoundException;
import io.rdfforge.common.security.AuthUser;
import io.rdfforge.triplestore.connector.TriplestoreConnector.QueryResult;
import io.rdfforge.triplestore.connector.TriplestoreConnector.RdfValue;
import io.rdfforge.triplestore.dto.SavedQueryDtos.SavedQueryCreateRequest;
import io.rdfforge.triplestore.dto.SavedQueryDtos.SavedQueryDto;
import io.rdfforge.triplestore.dto.SavedQueryDtos.SavedQueryRunRequest;
import io.rdfforge.triplestore.dto.SavedQueryDtos.SavedQueryRunResponse;
import io.rdfforge.triplestore.entity.SavedQueryEntity;
import io.rdfforge.triplestore.entity.SavedQueryEntity.QueryType;
import io.rdfforge.triplestore.entity.TriplestoreConnectionEntity;
import io.rdfforge.triplestore.repository.SavedQueryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SavedQueryService — Phase 7")
class SavedQueryServiceTest {

    @Mock private SavedQueryRepository repository;
    @Mock private TriplestoreService triplestoreService;

    private SavedQueryService service;

    private UUID projectId;
    private UUID userId;
    private UUID otherUserId;
    private UUID triplestoreId;
    private AuthUser user;
    private AuthUser other;
    private AuthUser admin;

    @BeforeEach
    void setUp() {
        service = new SavedQueryService(repository, triplestoreService);
        projectId = UUID.randomUUID();
        userId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();
        triplestoreId = UUID.randomUUID();
        user = new AuthUser(userId, "u@example.com", Set.of("USER"));
        other = new AuthUser(otherUserId, "o@example.com", Set.of("USER"));
        admin = new AuthUser(UUID.randomUUID(), "a@example.com", Set.of("ADMIN"));
    }

    private SavedQueryEntity sampleEntity() {
        SavedQueryEntity e = new SavedQueryEntity();
        e.setId(UUID.randomUUID());
        e.setProjectId(projectId);
        e.setName("sample");
        e.setType(QueryType.SELECT);
        e.setQueryText("SELECT * WHERE { ?s ?p ?o }");
        e.setCreatedBy(userId);
        e.setRunCount(0);
        return e;
    }

    private TriplestoreConnectionEntity ownedTriplestore() {
        TriplestoreConnectionEntity c = new TriplestoreConnectionEntity();
        c.setId(triplestoreId);
        c.setCreatedBy(userId);
        return c;
    }

    @Test
    void create_success_persistsAndReturnsDto() {
        when(repository.existsByProjectIdAndName(projectId, "q1")).thenReturn(false);
        when(repository.save(any(SavedQueryEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        SavedQueryCreateRequest req = new SavedQueryCreateRequest(
            projectId, "q1", "desc", QueryType.SELECT,
            "SELECT * WHERE { ?s ?p ?o }", Map.of(), List.of("foo"));
        SavedQueryDto dto = service.create(req, user);

        assertEquals("q1", dto.name());
        assertEquals(QueryType.SELECT, dto.type());
        assertEquals(userId, dto.createdBy());
        verify(repository).save(any(SavedQueryEntity.class));
    }

    @Test
    void create_duplicateName_rejected() {
        when(repository.existsByProjectIdAndName(projectId, "dup")).thenReturn(true);
        SavedQueryCreateRequest req = new SavedQueryCreateRequest(
            projectId, "dup", null, QueryType.SELECT, "SELECT * WHERE { ?s ?p ?o }", null, null);

        assertThrows(IllegalArgumentException.class, () -> service.create(req, user));
        verify(repository, never()).save(any());
    }

    @Test
    void create_invalidQueryText_rejected() {
        when(repository.existsByProjectIdAndName(projectId, "bad")).thenReturn(false);
        SavedQueryCreateRequest req = new SavedQueryCreateRequest(
            projectId, "bad", null, QueryType.SELECT, "This is not SPARQL", null, null);
        assertThrows(IllegalArgumentException.class, () -> service.create(req, user));
    }

    @Test
    void create_anonymous_denied() {
        SavedQueryCreateRequest req = new SavedQueryCreateRequest(
            projectId, "q", null, QueryType.SELECT, "SELECT * WHERE { ?s ?p ?o }", null, null);
        assertThrows(AccessDeniedException.class, () -> service.create(req, AuthUser.anonymous()));
    }

    @Test
    void get_byOtherNonAdmin_denied() {
        SavedQueryEntity e = sampleEntity();
        when(repository.findById(e.getId())).thenReturn(Optional.of(e));
        assertThrows(AccessDeniedException.class, () -> service.get(e.getId(), other));
    }

    @Test
    void get_byAdmin_allowed() {
        SavedQueryEntity e = sampleEntity();
        when(repository.findById(e.getId())).thenReturn(Optional.of(e));
        SavedQueryDto dto = service.get(e.getId(), admin);
        assertEquals(e.getId(), dto.id());
    }

    @Test
    void delete_byOwner_ok() {
        SavedQueryEntity e = sampleEntity();
        when(repository.findById(e.getId())).thenReturn(Optional.of(e));
        service.delete(e.getId(), user);
        verify(repository).deleteById(e.getId());
    }

    @Test
    void delete_notFound_throws() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.delete(id, user));
    }

    @Test
    void run_bumpsCountersAndLastRun() {
        SavedQueryEntity e = sampleEntity();
        when(repository.findById(e.getId())).thenReturn(Optional.of(e));
        when(triplestoreService.getConnection(triplestoreId)).thenReturn(Optional.of(ownedTriplestore()));
        when(triplestoreService.executeQuery(eq(triplestoreId), anyString(), any()))
            .thenReturn(new QueryResult(List.of("s"), List.of(), 12L));
        when(repository.save(any(SavedQueryEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        SavedQueryRunRequest req = new SavedQueryRunRequest(null, triplestoreId, null, Map.of());
        SavedQueryRunResponse resp = service.run(e.getId(), req, user);

        assertEquals(QueryType.SELECT, resp.type());
        ArgumentCaptor<SavedQueryEntity> cap = ArgumentCaptor.forClass(SavedQueryEntity.class);
        verify(repository).save(cap.capture());
        assertEquals(1, cap.getValue().getRunCount());
        assertNotNull(cap.getValue().getLastRun());
    }

    @Test
    void run_askQuery_returnsBoolean() {
        SavedQueryEntity e = sampleEntity();
        e.setType(QueryType.ASK);
        e.setQueryText("ASK { ?s ?p ?o }");
        when(repository.findById(e.getId())).thenReturn(Optional.of(e));
        when(triplestoreService.getConnection(triplestoreId)).thenReturn(Optional.of(ownedTriplestore()));
        when(triplestoreService.executeQuery(eq(triplestoreId), anyString(), any()))
            .thenReturn(new QueryResult(
                List.of("result"),
                List.of(Map.of("result", new RdfValue("literal", "true", null, null))),
                5L));
        when(repository.save(any(SavedQueryEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        SavedQueryRunResponse resp = service.run(e.getId(),
            new SavedQueryRunRequest(null, triplestoreId, null, Map.of()), user);

        assertEquals(QueryType.ASK, resp.type());
        assertEquals(Boolean.TRUE, resp.askResult());
    }

    @Test
    void run_triplestoreNotOwned_denied() {
        SavedQueryEntity e = sampleEntity();
        TriplestoreConnectionEntity conn = new TriplestoreConnectionEntity();
        conn.setId(triplestoreId);
        conn.setCreatedBy(otherUserId);
        when(repository.findById(e.getId())).thenReturn(Optional.of(e));
        when(triplestoreService.getConnection(triplestoreId)).thenReturn(Optional.of(conn));

        assertThrows(AccessDeniedException.class, () -> service.run(e.getId(),
            new SavedQueryRunRequest(null, triplestoreId, null, Map.of()), user));
    }

    @Test
    @DisplayName("Parameter substitution is SAFE against SPARQL injection")
    void substituteParameters_doesNotInjectMaliciousSparql() {
        String base = "SELECT ?s WHERE { ?s rdfs:label ?q }";
        // Attack payload trying to break out of the literal and inject an UPDATE
        String malicious = "foo\". DELETE WHERE { ?s ?p ?o }; #";
        String substituted = service.substituteParameters(base, Map.of(
            "q", Map.of("type", "literal", "value", malicious)
        ));

        assertNotNull(substituted);
        // The dangerous keywords MUST NOT appear unescaped as SPARQL operators —
        // Jena will escape the quotes. We verify the raw payload tokens are
        // contained inside a quoted literal (the escape chars are present).
        assertTrue(substituted.contains("\\\"") || substituted.contains("\\u0022"),
                   "Expected quote escaping in substituted query: " + substituted);
        // The overall query structure remains the original SELECT.
        assertTrue(substituted.toUpperCase(Locale.ROOT).contains("SELECT"));
    }

    @Test
    void substituteParameters_uriType_bindsAsIri() {
        String base = "SELECT ?s WHERE { ?s a ?cls }";
        String result = service.substituteParameters(base, Map.of(
            "cls", Map.of("type", "uri", "value", "http://example.org/Person")
        ));
        assertTrue(result.contains("<http://example.org/Person>"),
                   "Expected IRI angle brackets: " + result);
    }

    @Test
    void substituteParameters_numberType_bindsAsTypedLiteral() {
        String base = "SELECT ?s WHERE { ?s :age ?a }";
        String result = service.substituteParameters(base, Map.of(
            "a", Map.of("type", "number", "value", "42")
        ));
        assertTrue(result.contains("42"), "Expected numeric literal: " + result);
    }

    @Test
    void runInline_requiresQueryText() {
        assertThrows(IllegalArgumentException.class, () -> service.runInline(
            new SavedQueryRunRequest(null, triplestoreId, null, Map.of()), user));
    }

    @Test
    void runInline_requiresTriplestoreId() {
        assertThrows(IllegalArgumentException.class, () -> service.runInline(
            new SavedQueryRunRequest("SELECT * WHERE { ?s ?p ?o }", null, null, Map.of()), user));
    }

    @Test
    void runInline_success() {
        when(triplestoreService.getConnection(triplestoreId)).thenReturn(Optional.of(ownedTriplestore()));
        when(triplestoreService.executeQuery(eq(triplestoreId), anyString(), any()))
            .thenReturn(new QueryResult(List.of("s"), List.of(), 3L));

        SavedQueryRunResponse resp = service.runInline(
            new SavedQueryRunRequest("SELECT * WHERE { ?s ?p ?o }", triplestoreId, null, Map.of()), user);

        assertEquals(QueryType.SELECT, resp.type());
        assertNotNull(resp.bindings());
    }

    @Test
    void inferQueryType_detectsAllTypes() {
        assertEquals(QueryType.SELECT,
                SavedQueryService.inferQueryType("SELECT * WHERE { ?s ?p ?o }"));
        assertEquals(QueryType.ASK,
                SavedQueryService.inferQueryType("ASK { ?s ?p ?o }"));
        assertEquals(QueryType.CONSTRUCT,
                SavedQueryService.inferQueryType("CONSTRUCT { ?s ?p ?o } WHERE { ?s ?p ?o }"));
        assertEquals(QueryType.DESCRIBE,
                SavedQueryService.inferQueryType("DESCRIBE <http://example.org/x>"));
        assertEquals(QueryType.UPDATE,
                SavedQueryService.inferQueryType("DELETE WHERE { ?s ?p ?o }"));
    }
}
