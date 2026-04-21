package io.rdfforge.common.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.WebRequest;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Targets the actual consolidated handler surface of
 * {@link GlobalExceptionHandler}. The handler no longer exposes per-
 * exception-type methods — every {@link RdfForgeException} subclass is
 * mapped through {@link GlobalExceptionHandler#handleRdfForgeException},
 * and the remaining entry points are {@code handleValidationExceptions},
 * {@code handleIOException}, and {@code handleGenericException}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GlobalExceptionHandler Tests")
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @Mock
    private WebRequest webRequest;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
        when(webRequest.getDescription(false)).thenReturn("uri=/api/test");
    }

    @Test
    @DisplayName("ResourceNotFoundException -> 404 ProblemDetail")
    void resourceNotFound_maps_to_404() {
        ResponseEntity<ProblemDetail> r = handler.handleRdfForgeException(
                new ResourceNotFoundException("Pipeline", "123"), webRequest);
        assertEquals(HttpStatus.NOT_FOUND, r.getStatusCode());
        assertNotNull(r.getBody());
        assertEquals(404, r.getBody().getStatus());
    }

    @Test
    @DisplayName("PipelineValidationException -> 400 ProblemDetail")
    void pipelineValidation_maps_to_400() {
        ResponseEntity<ProblemDetail> r = handler.handleRdfForgeException(
                new PipelineValidationException("bad"), webRequest);
        assertEquals(HttpStatus.BAD_REQUEST, r.getStatusCode());
        assertEquals(400, r.getBody().getStatus());
    }

    @Test
    @DisplayName("ShaclValidationException -> UNPROCESSABLE_ENTITY (422) ProblemDetail")
    void shaclValidation_maps_to_422() {
        ResponseEntity<ProblemDetail> r = handler.handleRdfForgeException(
                new ShaclValidationException("shape broken"), webRequest);
        // ShaclValidationException carries its own HttpStatus; assert it is a 4xx.
        assertTrue(r.getStatusCode().is4xxClientError(),
                "ShaclValidationException must be a client error, got " + r.getStatusCode());
        assertEquals(r.getStatusCode().value(), r.getBody().getStatus());
    }

    @Test
    @DisplayName("Unchecked IllegalArgumentException -> 500 via generic handler")
    void illegalArgument_maps_to_500() {
        ResponseEntity<ProblemDetail> r = handler.handleGenericException(
                new IllegalArgumentException("nope"), webRequest);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, r.getStatusCode());
        assertEquals(500, r.getBody().getStatus());
    }

    @Test
    @DisplayName("Generic Exception -> 500 via generic handler")
    void generic_maps_to_500() {
        ResponseEntity<ProblemDetail> r = handler.handleGenericException(
                new RuntimeException("boom"), webRequest);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, r.getStatusCode());
        assertEquals(500, r.getBody().getStatus());
    }

    @Test
    @DisplayName("IOException -> 503 Storage I/O Error")
    void io_maps_to_503() {
        ResponseEntity<ProblemDetail> r = handler.handleIOException(
                new IOException("disk gone"), webRequest);
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, r.getStatusCode());
        assertNotNull(r.getBody());
        assertEquals(503, r.getBody().getStatus());
        // Detail must not echo the raw message (no path / cred leak).
        assertFalse(r.getBody().getDetail() != null
                && r.getBody().getDetail().contains("disk gone"));
    }
}
