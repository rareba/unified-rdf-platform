package io.rdfforge.auth.service;

import io.rdfforge.auth.entity.PersonalAccessTokenEntity;
import io.rdfforge.auth.repository.PersonalAccessTokenRepository;
import io.rdfforge.common.model.PersonalAccessToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive unit tests for PersonalAccessTokenService.
 * Covers token creation, validation, revocation, and listing with mocked repository.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PersonalAccessTokenService Tests")
class PersonalAccessTokenServiceTest {

    @Mock
    private PersonalAccessTokenRepository repository;

    private PersonalAccessTokenService tokenService;

    private UUID userId;
    private UUID tokenId;
    private PersonalAccessTokenEntity sampleEntity;

    @BeforeEach
    void setUp() {
        tokenService = new PersonalAccessTokenService(repository);

        userId = UUID.randomUUID();
        tokenId = UUID.randomUUID();

        sampleEntity = PersonalAccessTokenEntity.builder()
                .id(tokenId)
                .userId(userId)
                .name("Test Token")
                .description("A test token")
                .tokenHash("abc123hashvalue")
                .tokenPrefix("ccx_abc12345")
                .scopes(Set.of("read", "write"))
                .createdAt(Instant.now())
                .revoked(false)
                .build();
    }

    @Nested
    @DisplayName("createToken Tests")
    class CreateTokenTests {

        @Test
        @DisplayName("Should create token and return plain token with ccx_ prefix")
        void createToken_ValidRequest_ReturnsPlainTokenWithPrefix() {
            when(repository.countActiveTokensByUserId(userId)).thenReturn(0L);
            when(repository.save(any(PersonalAccessTokenEntity.class))).thenAnswer(inv -> {
                PersonalAccessTokenEntity entity = inv.getArgument(0);
                entity = PersonalAccessTokenEntity.builder()
                        .id(UUID.randomUUID())
                        .userId(entity.getUserId())
                        .name(entity.getName())
                        .description(entity.getDescription())
                        .tokenHash(entity.getTokenHash())
                        .tokenPrefix(entity.getTokenPrefix())
                        .scopes(entity.getScopes())
                        .createdAt(Instant.now())
                        .revoked(false)
                        .build();
                return entity;
            });

            PersonalAccessTokenService.CreateTokenResult result = tokenService.createToken(
                    userId, "My Token", "A description", null, Set.of("read")
            );

            assertNotNull(result);
            assertNotNull(result.token());
            assertNotNull(result.plainToken());
            assertTrue(result.plainToken().startsWith("ccx_"),
                    "Plain token must start with ccx_ prefix");
        }

        @Test
        @DisplayName("Should persist entity with hashed token, not plain token")
        void createToken_ValidRequest_PersistsHashedToken() {
            when(repository.countActiveTokensByUserId(userId)).thenReturn(0L);
            ArgumentCaptor<PersonalAccessTokenEntity> entityCaptor =
                    ArgumentCaptor.forClass(PersonalAccessTokenEntity.class);
            when(repository.save(entityCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));

            PersonalAccessTokenService.CreateTokenResult result = tokenService.createToken(
                    userId, "My Token", null, null, null
            );

            PersonalAccessTokenEntity savedEntity = entityCaptor.getValue();

            // The stored hash must not equal the plain token - it should be a SHA-256 hex string
            assertNotEquals(result.plainToken(), savedEntity.getTokenHash());
            // SHA-256 hex is always 64 characters
            assertEquals(64, savedEntity.getTokenHash().length());
            assertTrue(savedEntity.getTokenHash().matches("[0-9a-f]{64}"),
                    "Token hash should be a lowercase hex SHA-256 string");
        }

        @Test
        @DisplayName("Should store correct userId and name in entity")
        void createToken_ValidRequest_StoresUserIdAndName() {
            when(repository.countActiveTokensByUserId(userId)).thenReturn(0L);
            ArgumentCaptor<PersonalAccessTokenEntity> entityCaptor =
                    ArgumentCaptor.forClass(PersonalAccessTokenEntity.class);
            when(repository.save(entityCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));

            tokenService.createToken(userId, "Pipeline Token", "Used by CI", null, Set.of("read"));

            PersonalAccessTokenEntity saved = entityCaptor.getValue();
            assertEquals(userId, saved.getUserId());
            assertEquals("Pipeline Token", saved.getName());
            assertEquals("Used by CI", saved.getDescription());
        }

        @Test
        @DisplayName("Should store provided scopes in entity")
        void createToken_WithScopes_StoresScopesOnEntity() {
            Set<String> scopes = Set.of("read", "write", "admin");
            when(repository.countActiveTokensByUserId(userId)).thenReturn(0L);
            ArgumentCaptor<PersonalAccessTokenEntity> entityCaptor =
                    ArgumentCaptor.forClass(PersonalAccessTokenEntity.class);
            when(repository.save(entityCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));

            tokenService.createToken(userId, "Admin Token", null, null, scopes);

            assertEquals(scopes, entityCaptor.getValue().getScopes());
        }

        @Test
        @DisplayName("Should use empty set for scopes when null is provided")
        void createToken_NullScopes_StoresEmptySet() {
            when(repository.countActiveTokensByUserId(userId)).thenReturn(0L);
            ArgumentCaptor<PersonalAccessTokenEntity> entityCaptor =
                    ArgumentCaptor.forClass(PersonalAccessTokenEntity.class);
            when(repository.save(entityCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));

            tokenService.createToken(userId, "Token", null, null, null);

            assertNotNull(entityCaptor.getValue().getScopes());
            assertTrue(entityCaptor.getValue().getScopes().isEmpty());
        }

        @Test
        @DisplayName("Should set expiration date when expiration is provided")
        void createToken_WithExpiration_SetsExpiresAt() {
            when(repository.countActiveTokensByUserId(userId)).thenReturn(0L);
            ArgumentCaptor<PersonalAccessTokenEntity> entityCaptor =
                    ArgumentCaptor.forClass(PersonalAccessTokenEntity.class);
            when(repository.save(entityCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));

            Instant before = Instant.now();
            tokenService.createToken(userId, "Token", null, PersonalAccessToken.Expiration.ONE_WEEK, null);
            Instant after = Instant.now();

            Instant expiresAt = entityCaptor.getValue().getExpiresAt();
            assertNotNull(expiresAt);
            // Should be approximately 7 days from now
            assertTrue(expiresAt.isAfter(before.plusSeconds(6 * 24 * 60 * 60)),
                    "Expiry should be at least 6 days in the future");
            assertTrue(expiresAt.isBefore(after.plusSeconds(8 * 24 * 60 * 60)),
                    "Expiry should not be more than 8 days in the future");
        }

        @Test
        @DisplayName("Should set null expiration when NEVER expiration is provided")
        void createToken_NeverExpiration_SetsNullExpiresAt() {
            when(repository.countActiveTokensByUserId(userId)).thenReturn(0L);
            ArgumentCaptor<PersonalAccessTokenEntity> entityCaptor =
                    ArgumentCaptor.forClass(PersonalAccessTokenEntity.class);
            when(repository.save(entityCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));

            tokenService.createToken(userId, "Token", null, PersonalAccessToken.Expiration.NEVER, null);

            assertNull(entityCaptor.getValue().getExpiresAt());
        }

        @Test
        @DisplayName("Should set null expiration when no expiration is provided")
        void createToken_NullExpiration_SetsNullExpiresAt() {
            when(repository.countActiveTokensByUserId(userId)).thenReturn(0L);
            ArgumentCaptor<PersonalAccessTokenEntity> entityCaptor =
                    ArgumentCaptor.forClass(PersonalAccessTokenEntity.class);
            when(repository.save(entityCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));

            tokenService.createToken(userId, "Token", null, null, null);

            assertNull(entityCaptor.getValue().getExpiresAt());
        }

        @Test
        @DisplayName("Should throw IllegalStateException when user has 20 active tokens")
        void createToken_MaxTokensReached_ThrowsIllegalStateException() {
            when(repository.countActiveTokensByUserId(userId)).thenReturn(20L);

            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> tokenService.createToken(userId, "Token", null, null, null));

            assertTrue(ex.getMessage().contains("20"),
                    "Exception message should mention the max token limit");
            verify(repository, never()).save(any());
        }

        @Test
        @DisplayName("Should allow creation when user has 19 active tokens (boundary)")
        void createToken_OneBelowMaxTokens_Succeeds() {
            when(repository.countActiveTokensByUserId(userId)).thenReturn(19L);
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            assertDoesNotThrow(() ->
                    tokenService.createToken(userId, "Token", null, null, null));
        }

        @Test
        @DisplayName("Should store token prefix with ccx_ prefix and first 8 chars")
        void createToken_ValidRequest_StoresTokenPrefix() {
            when(repository.countActiveTokensByUserId(userId)).thenReturn(0L);
            ArgumentCaptor<PersonalAccessTokenEntity> entityCaptor =
                    ArgumentCaptor.forClass(PersonalAccessTokenEntity.class);
            when(repository.save(entityCaptor.capture())).thenAnswer(inv -> inv.getArgument(0));

            tokenService.createToken(userId, "Token", null, null, null);

            String prefix = entityCaptor.getValue().getTokenPrefix();
            assertNotNull(prefix);
            assertTrue(prefix.startsWith("ccx_"),
                    "Token prefix must start with ccx_");
            // ccx_ (4) + 8 chars = 12 chars minimum
            assertTrue(prefix.length() >= 12,
                    "Token prefix should include at least 8 chars after ccx_");
        }
    }

    @Nested
    @DisplayName("listUserTokens Tests")
    class ListUserTokensTests {

        @Test
        @DisplayName("Should return only active tokens by default")
        void listUserTokens_ExcludeRevoked_ReturnsOnlyActiveTokens() {
            when(repository.findByUserIdAndRevokedFalseOrderByCreatedAtDesc(userId))
                    .thenReturn(List.of(sampleEntity));

            List<PersonalAccessToken> result = tokenService.listUserTokens(userId, false);

            assertEquals(1, result.size());
            verify(repository).findByUserIdAndRevokedFalseOrderByCreatedAtDesc(userId);
            verify(repository, never()).findByUserIdOrderByCreatedAtDesc(any());
        }

        @Test
        @DisplayName("Should return all tokens including revoked when includeRevoked is true")
        void listUserTokens_IncludeRevoked_ReturnsAllTokens() {
            PersonalAccessTokenEntity revokedEntity = PersonalAccessTokenEntity.builder()
                    .id(UUID.randomUUID())
                    .userId(userId)
                    .name("Old Token")
                    .tokenHash("oldhash")
                    .tokenPrefix("ccx_oldtoken")
                    .createdAt(Instant.now().minusSeconds(3600))
                    .revoked(true)
                    .revokedAt(Instant.now())
                    .build();

            when(repository.findByUserIdOrderByCreatedAtDesc(userId))
                    .thenReturn(List.of(sampleEntity, revokedEntity));

            List<PersonalAccessToken> result = tokenService.listUserTokens(userId, true);

            assertEquals(2, result.size());
            verify(repository).findByUserIdOrderByCreatedAtDesc(userId);
            verify(repository, never()).findByUserIdAndRevokedFalseOrderByCreatedAtDesc(any());
        }

        @Test
        @DisplayName("Should return empty list when user has no tokens")
        void listUserTokens_NoTokens_ReturnsEmptyList() {
            when(repository.findByUserIdAndRevokedFalseOrderByCreatedAtDesc(userId))
                    .thenReturn(List.of());

            List<PersonalAccessToken> result = tokenService.listUserTokens(userId, false);

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should map entity fields to model correctly")
        void listUserTokens_EntityExists_MapsFieldsCorrectly() {
            when(repository.findByUserIdAndRevokedFalseOrderByCreatedAtDesc(userId))
                    .thenReturn(List.of(sampleEntity));

            List<PersonalAccessToken> result = tokenService.listUserTokens(userId, false);

            PersonalAccessToken token = result.get(0);
            assertEquals(tokenId, token.getId());
            assertEquals(userId, token.getUserId());
            assertEquals("Test Token", token.getName());
            assertEquals("A test token", token.getDescription());
            assertEquals("ccx_abc12345", token.getTokenPrefix());
            assertEquals(Set.of("read", "write"), token.getScopes());
            assertFalse(token.isRevoked());
        }

        @Test
        @DisplayName("Should not expose tokenHash in mapped model")
        void listUserTokens_EntityExists_DoesNotExposeTokenHash() {
            when(repository.findByUserIdAndRevokedFalseOrderByCreatedAtDesc(userId))
                    .thenReturn(List.of(sampleEntity));

            List<PersonalAccessToken> result = tokenService.listUserTokens(userId, false);

            // tokenHash on the model should be null since mapToModel does not copy it
            assertNull(result.get(0).getTokenHash());
        }
    }

    @Nested
    @DisplayName("getToken Tests")
    class GetTokenTests {

        @Test
        @DisplayName("Should return token when it belongs to the requesting user")
        void getToken_OwnedByUser_ReturnsToken() {
            when(repository.findByIdAndUserId(tokenId, userId)).thenReturn(Optional.of(sampleEntity));

            Optional<PersonalAccessToken> result = tokenService.getToken(tokenId, userId);

            assertTrue(result.isPresent());
            assertEquals(tokenId, result.get().getId());
        }

        @Test
        @DisplayName("Should return empty when token does not belong to user")
        void getToken_NotOwnedByUser_ReturnsEmpty() {
            UUID otherUserId = UUID.randomUUID();
            when(repository.findByIdAndUserId(tokenId, otherUserId)).thenReturn(Optional.empty());

            Optional<PersonalAccessToken> result = tokenService.getToken(tokenId, otherUserId);

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should return empty when token does not exist")
        void getToken_TokenNotFound_ReturnsEmpty() {
            UUID nonExistentId = UUID.randomUUID();
            when(repository.findByIdAndUserId(nonExistentId, userId)).thenReturn(Optional.empty());

            Optional<PersonalAccessToken> result = tokenService.getToken(nonExistentId, userId);

            assertTrue(result.isEmpty());
        }
    }

    @Nested
    @DisplayName("validateToken Tests")
    class ValidateTokenTests {

        @Test
        @DisplayName("Should return token info when valid token is provided")
        void validateToken_ValidToken_ReturnsTokenInfo() {
            // We need to know the hash of a known token to configure the mock.
            // The service strips the prefix, hashes the remainder, and looks it up.
            // We capture this by accepting any tokenHash lookup and returning our entity.
            when(repository.findByTokenHash(anyString())).thenReturn(Optional.of(sampleEntity));
            doNothing().when(repository).updateLastUsed(any(), any(), any());

            Optional<PersonalAccessToken> result = tokenService.validateToken("ccx_validtoken1234", "127.0.0.1");

            assertTrue(result.isPresent());
        }

        @Test
        @DisplayName("Should return empty when token does not have ccx_ prefix")
        void validateToken_MissingPrefix_ReturnsEmpty() {
            Optional<PersonalAccessToken> result = tokenService.validateToken("no-prefix-token", "127.0.0.1");

            assertTrue(result.isEmpty());
            verify(repository, never()).findByTokenHash(any());
        }

        @Test
        @DisplayName("Should return empty when token is null")
        void validateToken_NullToken_ReturnsEmpty() {
            Optional<PersonalAccessToken> result = tokenService.validateToken(null, "127.0.0.1");

            assertTrue(result.isEmpty());
            verify(repository, never()).findByTokenHash(any());
        }

        @Test
        @DisplayName("Should return empty when token hash is not found in repository")
        void validateToken_UnknownToken_ReturnsEmpty() {
            when(repository.findByTokenHash(anyString())).thenReturn(Optional.empty());

            Optional<PersonalAccessToken> result = tokenService.validateToken("ccx_unknowntoken123", "192.168.1.1");

            assertTrue(result.isEmpty());
        }

        @Test
        @DisplayName("Should return empty when found token is revoked")
        void validateToken_RevokedToken_ReturnsEmpty() {
            PersonalAccessTokenEntity revokedEntity = PersonalAccessTokenEntity.builder()
                    .id(tokenId)
                    .userId(userId)
                    .name("Revoked Token")
                    .tokenHash("somehash")
                    .tokenPrefix("ccx_revoked1")
                    .createdAt(Instant.now().minusSeconds(3600))
                    .revoked(true)
                    .revokedAt(Instant.now().minusSeconds(60))
                    .build();

            when(repository.findByTokenHash(anyString())).thenReturn(Optional.of(revokedEntity));

            Optional<PersonalAccessToken> result = tokenService.validateToken("ccx_revokedtoken12", "127.0.0.1");

            assertTrue(result.isEmpty());
            verify(repository, never()).updateLastUsed(any(), any(), any());
        }

        @Test
        @DisplayName("Should return empty when found token is expired")
        void validateToken_ExpiredToken_ReturnsEmpty() {
            PersonalAccessTokenEntity expiredEntity = PersonalAccessTokenEntity.builder()
                    .id(tokenId)
                    .userId(userId)
                    .name("Expired Token")
                    .tokenHash("expiredhash")
                    .tokenPrefix("ccx_expired1")
                    .createdAt(Instant.now().minusSeconds(3600))
                    .expiresAt(Instant.now().minusSeconds(60)) // expired 1 minute ago
                    .revoked(false)
                    .build();

            when(repository.findByTokenHash(anyString())).thenReturn(Optional.of(expiredEntity));

            Optional<PersonalAccessToken> result = tokenService.validateToken("ccx_expiredtoken12", "127.0.0.1");

            assertTrue(result.isEmpty());
            verify(repository, never()).updateLastUsed(any(), any(), any());
        }

        @Test
        @DisplayName("Should update lastUsed timestamp and IP on successful validation")
        void validateToken_ValidToken_UpdatesLastUsed() {
            when(repository.findByTokenHash(anyString())).thenReturn(Optional.of(sampleEntity));

            ArgumentCaptor<UUID> idCaptor = ArgumentCaptor.forClass(UUID.class);
            ArgumentCaptor<Instant> timestampCaptor = ArgumentCaptor.forClass(Instant.class);
            ArgumentCaptor<String> ipCaptor = ArgumentCaptor.forClass(String.class);
            doNothing().when(repository).updateLastUsed(idCaptor.capture(), timestampCaptor.capture(), ipCaptor.capture());

            Instant before = Instant.now();
            tokenService.validateToken("ccx_validtoken1234", "10.0.0.1");

            assertEquals(tokenId, idCaptor.getValue());
            assertTrue(timestampCaptor.getValue().isAfter(before.minusSeconds(1)));
            assertEquals("10.0.0.1", ipCaptor.getValue());
        }

        @Test
        @DisplayName("Should accept token with no expiry date as valid")
        void validateToken_NoExpiryDate_ReturnsToken() {
            // sampleEntity has no expiresAt set, so it never expires
            when(repository.findByTokenHash(anyString())).thenReturn(Optional.of(sampleEntity));
            doNothing().when(repository).updateLastUsed(any(), any(), any());

            Optional<PersonalAccessToken> result = tokenService.validateToken("ccx_validtoken1234", "127.0.0.1");

            assertTrue(result.isPresent());
        }

        @Test
        @DisplayName("Should accept token that expires in the future")
        void validateToken_FutureExpiry_ReturnsToken() {
            sampleEntity = PersonalAccessTokenEntity.builder()
                    .id(tokenId)
                    .userId(userId)
                    .name("Test Token")
                    .tokenHash("hash")
                    .tokenPrefix("ccx_abc12345")
                    .createdAt(Instant.now().minusSeconds(3600))
                    .expiresAt(Instant.now().plusSeconds(3600)) // expires in 1 hour
                    .revoked(false)
                    .build();

            when(repository.findByTokenHash(anyString())).thenReturn(Optional.of(sampleEntity));
            doNothing().when(repository).updateLastUsed(any(), any(), any());

            Optional<PersonalAccessToken> result = tokenService.validateToken("ccx_validtoken1234", "127.0.0.1");

            assertTrue(result.isPresent());
        }
    }

    @Nested
    @DisplayName("revokeToken Tests")
    class RevokeTokenTests {

        @Test
        @DisplayName("Should revoke active token and return true")
        void revokeToken_ActiveToken_RevokesAndReturnsTrue() {
            when(repository.findByIdAndUserId(tokenId, userId)).thenReturn(Optional.of(sampleEntity));

            boolean result = tokenService.revokeToken(tokenId, userId);

            assertTrue(result);
            verify(repository).revokeToken(eq(tokenId), any(Instant.class));
        }

        @Test
        @DisplayName("Should return false when token does not belong to user")
        void revokeToken_NotOwnedByUser_ReturnsFalse() {
            UUID otherUserId = UUID.randomUUID();
            when(repository.findByIdAndUserId(tokenId, otherUserId)).thenReturn(Optional.empty());

            boolean result = tokenService.revokeToken(tokenId, otherUserId);

            assertFalse(result);
            verify(repository, never()).revokeToken(any(), any());
        }

        @Test
        @DisplayName("Should return false when token does not exist")
        void revokeToken_TokenNotFound_ReturnsFalse() {
            UUID nonExistentId = UUID.randomUUID();
            when(repository.findByIdAndUserId(nonExistentId, userId)).thenReturn(Optional.empty());

            boolean result = tokenService.revokeToken(nonExistentId, userId);

            assertFalse(result);
            verify(repository, never()).revokeToken(any(), any());
        }

        @Test
        @DisplayName("Should return false when token is already revoked")
        void revokeToken_AlreadyRevoked_ReturnsFalse() {
            PersonalAccessTokenEntity alreadyRevoked = PersonalAccessTokenEntity.builder()
                    .id(tokenId)
                    .userId(userId)
                    .name("Revoked Token")
                    .tokenHash("hash")
                    .tokenPrefix("ccx_abc12345")
                    .createdAt(Instant.now().minusSeconds(3600))
                    .revoked(true)
                    .revokedAt(Instant.now().minusSeconds(60))
                    .build();

            when(repository.findByIdAndUserId(tokenId, userId)).thenReturn(Optional.of(alreadyRevoked));

            boolean result = tokenService.revokeToken(tokenId, userId);

            assertFalse(result);
            verify(repository, never()).revokeToken(any(), any());
        }

        @Test
        @DisplayName("Should pass a recent timestamp to revokeToken")
        void revokeToken_ActiveToken_PassesCurrentTimestamp() {
            when(repository.findByIdAndUserId(tokenId, userId)).thenReturn(Optional.of(sampleEntity));

            ArgumentCaptor<Instant> timestampCaptor = ArgumentCaptor.forClass(Instant.class);
            Instant before = Instant.now();

            tokenService.revokeToken(tokenId, userId);

            verify(repository).revokeToken(eq(tokenId), timestampCaptor.capture());
            Instant capturedTime = timestampCaptor.getValue();
            assertFalse(capturedTime.isBefore(before.minusSeconds(1)),
                    "Revocation timestamp should be close to the current time");
        }
    }

    @Nested
    @DisplayName("revokeAllTokens Tests")
    class RevokeAllTokensTests {

        @Test
        @DisplayName("Should revoke all active tokens and return count")
        void revokeAllTokens_MultipleActiveTokens_RevokesAllAndReturnsCount() {
            when(repository.countActiveTokensByUserId(userId)).thenReturn(3L);

            int result = tokenService.revokeAllTokens(userId);

            assertEquals(3, result);
            verify(repository).revokeAllUserTokens(eq(userId), any(Instant.class));
        }

        @Test
        @DisplayName("Should return 0 and not call revokeAll when no active tokens exist")
        void revokeAllTokens_NoActiveTokens_ReturnsZeroWithoutRevoking() {
            when(repository.countActiveTokensByUserId(userId)).thenReturn(0L);

            int result = tokenService.revokeAllTokens(userId);

            assertEquals(0, result);
            verify(repository, never()).revokeAllUserTokens(any(), any());
        }

        @Test
        @DisplayName("Should revoke when exactly one active token exists")
        void revokeAllTokens_OneActiveToken_RevokesAndReturnsOne() {
            when(repository.countActiveTokensByUserId(userId)).thenReturn(1L);

            int result = tokenService.revokeAllTokens(userId);

            assertEquals(1, result);
            verify(repository).revokeAllUserTokens(eq(userId), any(Instant.class));
        }

        @Test
        @DisplayName("Should pass a recent timestamp when revoking all tokens")
        void revokeAllTokens_ActiveTokens_PassesCurrentTimestamp() {
            when(repository.countActiveTokensByUserId(userId)).thenReturn(2L);

            ArgumentCaptor<Instant> timestampCaptor = ArgumentCaptor.forClass(Instant.class);
            Instant before = Instant.now();

            tokenService.revokeAllTokens(userId);

            verify(repository).revokeAllUserTokens(eq(userId), timestampCaptor.capture());
            assertFalse(timestampCaptor.getValue().isBefore(before.minusSeconds(1)),
                    "Revocation timestamp should be close to the current time");
        }
    }

    @Nested
    @DisplayName("Token Generation and Hashing Tests")
    class TokenGenerationTests {

        @Test
        @DisplayName("Should generate unique tokens on each call")
        void createToken_CalledTwice_GeneratesDifferentTokens() {
            when(repository.countActiveTokensByUserId(userId)).thenReturn(0L);
            when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            PersonalAccessTokenService.CreateTokenResult result1 =
                    tokenService.createToken(userId, "Token 1", null, null, null);
            PersonalAccessTokenService.CreateTokenResult result2 =
                    tokenService.createToken(userId, "Token 2", null, null, null);

            assertNotEquals(result1.plainToken(), result2.plainToken(),
                    "Each generated token must be unique");
        }

        @Test
        @DisplayName("Should produce consistent hash for the same input")
        void createToken_SameRawValue_ProducesSameHash() {
            // Validate that the hashing function is deterministic by creating two services
            // and verifying that the validation lookup uses the hash consistently.
            // We verify this indirectly: if the same raw value is given to validateToken twice,
            // the same hash should be passed to the repository each time.
            when(repository.findByTokenHash(anyString())).thenReturn(Optional.empty());

            ArgumentCaptor<String> hashCaptor1 = ArgumentCaptor.forClass(String.class);
            tokenService.validateToken("ccx_stabletokenvalue1", "127.0.0.1");
            verify(repository).findByTokenHash(hashCaptor1.capture());

            String firstHash = hashCaptor1.getValue();
            reset(repository);
            when(repository.findByTokenHash(anyString())).thenReturn(Optional.empty());

            ArgumentCaptor<String> hashCaptor2 = ArgumentCaptor.forClass(String.class);
            tokenService.validateToken("ccx_stabletokenvalue1", "127.0.0.1");
            verify(repository).findByTokenHash(hashCaptor2.capture());

            assertEquals(firstHash, hashCaptor2.getValue(),
                    "The same raw token value must always produce the same hash");
        }

        @Test
        @DisplayName("Should produce different hashes for different raw values")
        void createToken_DifferentRawValues_ProduceDifferentHashes() {
            when(repository.findByTokenHash(anyString())).thenReturn(Optional.empty());

            ArgumentCaptor<String> hashCaptor1 = ArgumentCaptor.forClass(String.class);
            tokenService.validateToken("ccx_tokenvalue_aaa123", "127.0.0.1");
            verify(repository).findByTokenHash(hashCaptor1.capture());
            String hash1 = hashCaptor1.getValue();

            reset(repository);
            when(repository.findByTokenHash(anyString())).thenReturn(Optional.empty());

            ArgumentCaptor<String> hashCaptor2 = ArgumentCaptor.forClass(String.class);
            tokenService.validateToken("ccx_tokenvalue_bbb456", "127.0.0.1");
            verify(repository).findByTokenHash(hashCaptor2.capture());
            String hash2 = hashCaptor2.getValue();

            assertNotEquals(hash1, hash2,
                    "Different raw token values must produce different hashes");
        }
    }
}
