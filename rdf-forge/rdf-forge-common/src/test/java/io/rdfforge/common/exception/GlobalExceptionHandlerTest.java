package io.rdfforge.common.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.WebRequest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("GlobalExceptionHandler Tests")
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;

    @Mock
    private WebRequest webRequest;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
        when(webRequest.getDescription(false)).thenReturn("test description");
    }

    @Nested
    @DisplayName("ResourceNotFoundException Tests")
    class ResourceNotFoundExceptionTests {

        @Test
        @DisplayName("Should return 404 for ResourceNotFoundException")
        void handleResourceNotFoundException_Returns404() {
            ResourceNotFoundException ex = new ResourceNotFoundException("Pipeline", "123");

            ResponseEntity<?> response = exceptionHandler.handleResourceNotFoundException(ex, webRequest);

            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
            assertNotNull(response.getBody());
        }

        @Test
        @DisplayName("Should include error details in response body")
        @SuppressWarnings("unchecked")
        void handleResourceNotFoundException_IncludesErrorDetails() {
            ResourceNotFoundException ex = new ResourceNotFoundException("Pipeline", "123");

            ResponseEntity<?> response = exceptionHandler.handleResourceNotFoundException(ex, webRequest);

            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertNotNull(body);
            assertEquals(404, body.get("status"));
            assertTrue(body.get("message").toString().contains("Pipeline"));
        }
    }

    @Nested
    @DisplayName("PipelineValidationException Tests")
    class PipelineValidationExceptionTests {

        @Test
        @DisplayName("Should return 400 for PipelineValidationException")
        void handlePipelineValidationException_Returns400() {
            PipelineValidationException ex = new PipelineValidationException("Invalid pipeline definition");

            ResponseEntity<?> response = exceptionHandler.handlePipelineValidationException(ex, webRequest);

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        }

        @Test
        @DisplayName("Should include validation message in response")
        @SuppressWarnings("unchecked")
        void handlePipelineValidationException_IncludesMessage() {
            PipelineValidationException ex = new PipelineValidationException("Step 'xyz' has circular dependency");

            ResponseEntity<?> response = exceptionHandler.handlePipelineValidationException(ex, webRequest);

            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertNotNull(body);
            assertTrue(body.get("message").toString().contains("circular"));
        }
    }

    @Nested
    @DisplayName("ShaclValidationException Tests")
    class ShaclValidationExceptionTests {

        @Test
        @DisplayName("Should return 400 for ShaclValidationException")
        void handleShaclValidationException_Returns400() {
            ShaclValidationException ex = new ShaclValidationException("Invalid SHACL syntax");

            ResponseEntity<?> response = exceptionHandler.handleShaclValidationException(ex, webRequest);

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        }
    }

    @Nested
    @DisplayName("IllegalArgumentException Tests")
    class IllegalArgumentExceptionTests {

        @Test
        @DisplayName("Should return 400 for IllegalArgumentException")
        void handleIllegalArgumentException_Returns400() {
            IllegalArgumentException ex = new IllegalArgumentException("Invalid input parameter");

            ResponseEntity<?> response = exceptionHandler.handleIllegalArgumentException(ex, webRequest);

            assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        }
    }

    @Nested
    @DisplayName("General Exception Tests")
    class GeneralExceptionTests {

        @Test
        @DisplayName("Should return 500 for general exceptions")
        void handleAllExceptions_Returns500() {
            Exception ex = new RuntimeException("Unexpected error");

            ResponseEntity<?> response = exceptionHandler.handleAllExceptions(ex, webRequest);

            assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        }

        @Test
        @DisplayName("Should mask internal error details")
        @SuppressWarnings("unchecked")
        void handleAllExceptions_MasksInternalDetails() {
            Exception ex = new RuntimeException("Database connection failed with password: secret123");

            ResponseEntity<?> response = exceptionHandler.handleAllExceptions(ex, webRequest);

            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertNotNull(body);
            // Should not expose sensitive information
            assertFalse(body.get("message").toString().contains("secret123"));
        }
    }

    @Nested
    @DisplayName("Response Structure Tests")
    class ResponseStructureTests {

        @Test
        @DisplayName("Should include timestamp in error response")
        @SuppressWarnings("unchecked")
        void errorResponse_IncludesTimestamp() {
            ResourceNotFoundException ex = new ResourceNotFoundException("Test", "123");

            ResponseEntity<?> response = exceptionHandler.handleResourceNotFoundException(ex, webRequest);

            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertNotNull(body);
            assertTrue(body.containsKey("timestamp"));
        }

        @Test
        @DisplayName("Should include status code in error response")
        @SuppressWarnings("unchecked")
        void errorResponse_IncludesStatusCode() {
            ResourceNotFoundException ex = new ResourceNotFoundException("Test", "123");

            ResponseEntity<?> response = exceptionHandler.handleResourceNotFoundException(ex, webRequest);

            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertNotNull(body);
            assertTrue(body.containsKey("status"));
            assertEquals(404, body.get("status"));
        }

        @Test
        @DisplayName("Should include error type in error response")
        @SuppressWarnings("unchecked")
        void errorResponse_IncludesErrorType() {
            ResourceNotFoundException ex = new ResourceNotFoundException("Test", "123");

            ResponseEntity<?> response = exceptionHandler.handleResourceNotFoundException(ex, webRequest);

            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertNotNull(body);
            assertTrue(body.containsKey("error"));
        }

        @Test
        @DisplayName("Should include path in error response")
        @SuppressWarnings("unchecked")
        void errorResponse_IncludesPath() {
            ResourceNotFoundException ex = new ResourceNotFoundException("Test", "123");

            ResponseEntity<?> response = exceptionHandler.handleResourceNotFoundException(ex, webRequest);

            Map<String, Object> body = (Map<String, Object>) response.getBody();
            assertNotNull(body);
            assertTrue(body.containsKey("path"));
        }
    }
}