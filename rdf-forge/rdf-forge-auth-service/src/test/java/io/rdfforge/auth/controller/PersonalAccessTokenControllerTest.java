package io.rdfforge.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.rdfforge.auth.config.TestSecurityConfig;
import io.rdfforge.auth.service.PersonalAccessTokenService;
import io.rdfforge.common.model.PersonalAccessToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller-layer tests for PersonalAccessTokenController using MockMvc.
 * Security is disabled via TestSecurityConfig so tests focus purely on HTTP
 * request/response handling, header reading, validation, and status codes.
 */
@WebMvcTest(PersonalAccessTokenController.class)
@Import(TestSecurityConfig.class)
@DisplayName("PersonalAccessTokenController Tests")
class PersonalAccessTokenControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PersonalAccessTokenService tokenService;

    private UUID userId;
    private UUID tokenId;
    private PersonalAccessToken sampleToken;
    private PersonalAccessTokenService.CreateTokenResult sampleCreateResult;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        tokenId = UUID.randomUUID();

        sampleToken = PersonalAccessToken.builder()
                .id(tokenId)
                .userId(userId)
                .name("CI Token")
                .description("Used by CI pipeline")
                .tokenPrefix("ccx_citoken12")
                .scopes(Set.of("read", "write"))
                .createdAt(Instant.now())
                .revoked(false)
                .build();

        sampleCreateResult = new PersonalAccessTokenService.CreateTokenResult(
                sampleToken,
                "ccx_plaintokenvalue123456"
        );
    }

    @Nested
    @DisplayName("GET /api/v1/auth/tokens - listTokens")
    class ListTokensTests {

        @Test
        @DisplayName("Should return 200 with token list when X-User-Id header is present")
        void listTokens_WithUserId_Returns200WithTokenList() throws Exception {
            when(tokenService.listUserTokens(userId, false)).thenReturn(List.of(sampleToken));

            mockMvc.perform(get("/api/v1/auth/tokens")
                            .header("X-User-Id", userId.toString()))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].id", is(tokenId.toString())))
                    .andExpect(jsonPath("$[0].name", is("CI Token")));
        }

        @Test
        @DisplayName("Should return 401 when X-User-Id header is missing")
        void listTokens_MissingUserId_Returns401() throws Exception {
            mockMvc.perform(get("/api/v1/auth/tokens"))
                    .andExpect(status().isUnauthorized());

            verify(tokenService, never()).listUserTokens(any(), anyBoolean());
        }

        @Test
        @DisplayName("Should pass includeRevoked=true to service when requested")
        void listTokens_WithIncludeRevoked_PassesTrueToService() throws Exception {
            when(tokenService.listUserTokens(userId, true)).thenReturn(List.of(sampleToken));

            mockMvc.perform(get("/api/v1/auth/tokens")
                            .header("X-User-Id", userId.toString())
                            .param("includeRevoked", "true"))
                    .andExpect(status().isOk());

            verify(tokenService).listUserTokens(userId, true);
        }

        @Test
        @DisplayName("Should default includeRevoked to false when not specified")
        void listTokens_WithoutIncludeRevoked_PassesFalseToService() throws Exception {
            when(tokenService.listUserTokens(userId, false)).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/auth/tokens")
                            .header("X-User-Id", userId.toString()))
                    .andExpect(status().isOk());

            verify(tokenService).listUserTokens(userId, false);
        }

        @Test
        @DisplayName("Should return empty array when user has no tokens")
        void listTokens_NoTokens_ReturnsEmptyArray() throws Exception {
            when(tokenService.listUserTokens(userId, false)).thenReturn(List.of());

            mockMvc.perform(get("/api/v1/auth/tokens")
                            .header("X-User-Id", userId.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(0)));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/tokens - createToken")
    class CreateTokenTests {

        @Test
        @DisplayName("Should return 201 with token and plainToken on successful creation")
        void createToken_ValidRequest_Returns201WithPlainToken() throws Exception {
            when(tokenService.createToken(eq(userId), eq("CI Token"), eq("For CI"), any(), any()))
                    .thenReturn(sampleCreateResult);

            String requestBody = objectMapper.writeValueAsString(Map.of(
                    "name", "CI Token",
                    "description", "For CI",
                    "scopes", Set.of("read", "write")
            ));

            mockMvc.perform(post("/api/v1/auth/tokens")
                            .header("X-User-Id", userId.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.plainToken", is("ccx_plaintokenvalue123456")))
                    .andExpect(jsonPath("$.token.id", is(tokenId.toString())))
                    .andExpect(jsonPath("$.token.name", is("CI Token")));
        }

        @Test
        @DisplayName("Should return 401 when X-User-Id header is missing")
        void createToken_MissingUserId_Returns401() throws Exception {
            String requestBody = objectMapper.writeValueAsString(Map.of("name", "My Token"));

            mockMvc.perform(post("/api/v1/auth/tokens")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isUnauthorized());

            verify(tokenService, never()).createToken(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("Should return 400 when name is blank")
        void createToken_BlankName_Returns400() throws Exception {
            String requestBody = objectMapper.writeValueAsString(Map.of("name", ""));

            mockMvc.perform(post("/api/v1/auth/tokens")
                            .header("X-User-Id", userId.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest());

            verify(tokenService, never()).createToken(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("Should return 400 when name exceeds 100 characters")
        void createToken_NameTooLong_Returns400() throws Exception {
            String longName = "a".repeat(101);
            String requestBody = objectMapper.writeValueAsString(Map.of("name", longName));

            mockMvc.perform(post("/api/v1/auth/tokens")
                            .header("X-User-Id", userId.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should return 400 when service throws IllegalStateException (max tokens reached)")
        void createToken_MaxTokensReached_Returns400() throws Exception {
            when(tokenService.createToken(any(), any(), any(), any(), any()))
                    .thenThrow(new IllegalStateException("Maximum number of active tokens reached (20)"));

            String requestBody = objectMapper.writeValueAsString(Map.of("name", "New Token"));

            mockMvc.perform(post("/api/v1/auth/tokens")
                            .header("X-User-Id", userId.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Should accept request with minimal body (name only)")
        void createToken_NameOnly_Returns201() throws Exception {
            when(tokenService.createToken(eq(userId), eq("Minimal"), isNull(), isNull(), isNull()))
                    .thenReturn(sampleCreateResult);

            String requestBody = objectMapper.writeValueAsString(Map.of("name", "Minimal"));

            mockMvc.perform(post("/api/v1/auth/tokens")
                            .header("X-User-Id", userId.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("Should pass expiration enum value to service")
        void createToken_WithExpiration_PassesExpirationToService() throws Exception {
            when(tokenService.createToken(eq(userId), any(), any(), eq(PersonalAccessToken.Expiration.ONE_MONTH), any()))
                    .thenReturn(sampleCreateResult);

            String requestBody = objectMapper.writeValueAsString(Map.of(
                    "name", "Expiring Token",
                    "expiration", "ONE_MONTH"
            ));

            mockMvc.perform(post("/api/v1/auth/tokens")
                            .header("X-User-Id", userId.toString())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isCreated());

            verify(tokenService).createToken(eq(userId), any(), any(),
                    eq(PersonalAccessToken.Expiration.ONE_MONTH), any());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/auth/tokens/{tokenId} - getToken")
    class GetTokenTests {

        @Test
        @DisplayName("Should return 200 with token details when token belongs to user")
        void getToken_OwnedByUser_Returns200() throws Exception {
            when(tokenService.getToken(tokenId, userId)).thenReturn(Optional.of(sampleToken));

            mockMvc.perform(get("/api/v1/auth/tokens/{tokenId}", tokenId)
                            .header("X-User-Id", userId.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is(tokenId.toString())))
                    .andExpect(jsonPath("$.name", is("CI Token")));
        }

        @Test
        @DisplayName("Should return 404 when token does not exist or does not belong to user")
        void getToken_TokenNotFound_Returns404() throws Exception {
            when(tokenService.getToken(tokenId, userId)).thenReturn(Optional.empty());

            mockMvc.perform(get("/api/v1/auth/tokens/{tokenId}", tokenId)
                            .header("X-User-Id", userId.toString()))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Should return 401 when X-User-Id header is missing")
        void getToken_MissingUserId_Returns401() throws Exception {
            mockMvc.perform(get("/api/v1/auth/tokens/{tokenId}", tokenId))
                    .andExpect(status().isUnauthorized());

            verify(tokenService, never()).getToken(any(), any());
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/auth/tokens/{tokenId} - revokeToken")
    class RevokeTokenTests {

        @Test
        @DisplayName("Should return 204 when token is successfully revoked")
        void revokeToken_ActiveOwnedToken_Returns204() throws Exception {
            when(tokenService.revokeToken(tokenId, userId)).thenReturn(true);

            mockMvc.perform(delete("/api/v1/auth/tokens/{tokenId}", tokenId)
                            .header("X-User-Id", userId.toString()))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("Should return 404 when token is not found or not owned by user")
        void revokeToken_TokenNotFound_Returns404() throws Exception {
            when(tokenService.revokeToken(tokenId, userId)).thenReturn(false);

            mockMvc.perform(delete("/api/v1/auth/tokens/{tokenId}", tokenId)
                            .header("X-User-Id", userId.toString()))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("Should return 401 when X-User-Id header is missing")
        void revokeToken_MissingUserId_Returns401() throws Exception {
            mockMvc.perform(delete("/api/v1/auth/tokens/{tokenId}", tokenId))
                    .andExpect(status().isUnauthorized());

            verify(tokenService, never()).revokeToken(any(), any());
        }
    }

    @Nested
    @DisplayName("DELETE /api/v1/auth/tokens - revokeAllTokens")
    class RevokeAllTokensTests {

        @Test
        @DisplayName("Should return 200 with revoked count when tokens are revoked")
        void revokeAllTokens_WithActiveTokens_Returns200WithCount() throws Exception {
            when(tokenService.revokeAllTokens(userId)).thenReturn(3);

            mockMvc.perform(delete("/api/v1/auth/tokens")
                            .header("X-User-Id", userId.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.revokedCount", is(3)));
        }

        @Test
        @DisplayName("Should return 200 with count 0 when no tokens were active")
        void revokeAllTokens_NoActiveTokens_Returns200WithZeroCount() throws Exception {
            when(tokenService.revokeAllTokens(userId)).thenReturn(0);

            mockMvc.perform(delete("/api/v1/auth/tokens")
                            .header("X-User-Id", userId.toString()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.revokedCount", is(0)));
        }

        @Test
        @DisplayName("Should return 401 when X-User-Id header is missing")
        void revokeAllTokens_MissingUserId_Returns401() throws Exception {
            mockMvc.perform(delete("/api/v1/auth/tokens"))
                    .andExpect(status().isUnauthorized());

            verify(tokenService, never()).revokeAllTokens(any());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/tokens/validate - validateToken")
    class ValidateTokenTests {

        @Test
        @DisplayName("Should return 200 with token info when token is valid")
        void validateToken_ValidToken_Returns200WithTokenInfo() throws Exception {
            when(tokenService.validateToken(eq("ccx_sometoken12345"), any()))
                    .thenReturn(Optional.of(sampleToken));

            String requestBody = objectMapper.writeValueAsString(Map.of("token", "ccx_sometoken12345"));

            mockMvc.perform(post("/api/v1/auth/tokens/validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id", is(tokenId.toString())))
                    .andExpect(jsonPath("$.userId", is(userId.toString())));
        }

        @Test
        @DisplayName("Should return 401 when token is invalid or not found")
        void validateToken_InvalidToken_Returns401() throws Exception {
            when(tokenService.validateToken(any(), any())).thenReturn(Optional.empty());

            String requestBody = objectMapper.writeValueAsString(Map.of("token", "ccx_badtoken999"));

            mockMvc.perform(post("/api/v1/auth/tokens/validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Should extract client IP from X-Forwarded-For header")
        void validateToken_WithXForwardedFor_PassesFirstIpToService() throws Exception {
            when(tokenService.validateToken(any(), eq("10.0.0.1"))).thenReturn(Optional.of(sampleToken));

            String requestBody = objectMapper.writeValueAsString(Map.of("token", "ccx_sometoken12345"));

            mockMvc.perform(post("/api/v1/auth/tokens/validate")
                            .header("X-Forwarded-For", "10.0.0.1, 172.16.0.1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk());

            verify(tokenService).validateToken(any(), eq("10.0.0.1"));
        }

        @Test
        @DisplayName("Should fall back to X-Real-IP when X-Forwarded-For is absent")
        void validateToken_WithXRealIP_PassesRealIpToService() throws Exception {
            when(tokenService.validateToken(any(), eq("192.168.1.100"))).thenReturn(Optional.of(sampleToken));

            String requestBody = objectMapper.writeValueAsString(Map.of("token", "ccx_sometoken12345"));

            mockMvc.perform(post("/api/v1/auth/tokens/validate")
                            .header("X-Real-IP", "192.168.1.100")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk());

            verify(tokenService).validateToken(any(), eq("192.168.1.100"));
        }

        @Test
        @DisplayName("Should use 'unknown' as client IP when no IP headers are present")
        void validateToken_NoIpHeaders_PassesUnknownToService() throws Exception {
            when(tokenService.validateToken(any(), eq("unknown"))).thenReturn(Optional.of(sampleToken));

            String requestBody = objectMapper.writeValueAsString(Map.of("token", "ccx_sometoken12345"));

            mockMvc.perform(post("/api/v1/auth/tokens/validate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk());

            verify(tokenService).validateToken(any(), eq("unknown"));
        }

        @Test
        @DisplayName("Should prefer X-Forwarded-For over X-Real-IP when both are present")
        void validateToken_BothIpHeaders_PrefersForwardedFor() throws Exception {
            when(tokenService.validateToken(any(), eq("10.0.0.1"))).thenReturn(Optional.of(sampleToken));

            String requestBody = objectMapper.writeValueAsString(Map.of("token", "ccx_sometoken12345"));

            mockMvc.perform(post("/api/v1/auth/tokens/validate")
                            .header("X-Forwarded-For", "10.0.0.1")
                            .header("X-Real-IP", "192.168.1.1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestBody))
                    .andExpect(status().isOk());

            verify(tokenService).validateToken(any(), eq("10.0.0.1"));
        }
    }
}
