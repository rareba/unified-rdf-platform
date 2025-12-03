package io.rdfforge.auth.service;

import io.rdfforge.auth.entity.PersonalAccessTokenEntity;
import io.rdfforge.auth.repository.PersonalAccessTokenRepository;
import io.rdfforge.common.model.PersonalAccessToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class PersonalAccessTokenService {

    private static final String TOKEN_PREFIX = "ccx_";
    private static final int TOKEN_LENGTH = 40;
    private static final int MAX_TOKENS_PER_USER = 20;

    private final PersonalAccessTokenRepository repository;
    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Create a new Personal Access Token for a user.
     * Returns both the token entity and the plain token (only shown once).
     */
    @Transactional
    public CreateTokenResult createToken(UUID userId, String name, String description,
                                         PersonalAccessToken.Expiration expiration,
                                         Set<String> scopes) {
        // Check if user has reached maximum tokens
        long activeTokens = repository.countActiveTokensByUserId(userId);
        if (activeTokens >= MAX_TOKENS_PER_USER) {
            throw new IllegalStateException("Maximum number of active tokens reached (" + MAX_TOKENS_PER_USER + ")");
        }

        // Generate secure random token
        String plainToken = generateSecureToken();
        String tokenHash = hashToken(plainToken);

        // Create entity
        PersonalAccessTokenEntity entity = PersonalAccessTokenEntity.builder()
                .userId(userId)
                .name(name)
                .description(description)
                .tokenHash(tokenHash)
                .tokenPrefix(TOKEN_PREFIX + plainToken.substring(0, 8))
                .scopes(scopes != null ? scopes : new HashSet<>())
                .expiresAt(expiration != null ? expiration.toExpirationDate() : null)
                .build();

        entity = repository.save(entity);
        log.info("Created new PAT for user {} with name '{}'", userId, name);

        return new CreateTokenResult(
                mapToModel(entity),
                TOKEN_PREFIX + plainToken
        );
    }

    /**
     * List all tokens for a user (does not include token values).
     */
    @Transactional(readOnly = true)
    public List<PersonalAccessToken> listUserTokens(UUID userId, boolean includeRevoked) {
        List<PersonalAccessTokenEntity> entities = includeRevoked
                ? repository.findByUserIdOrderByCreatedAtDesc(userId)
                : repository.findByUserIdAndRevokedFalseOrderByCreatedAtDesc(userId);

        return entities.stream()
                .map(this::mapToModel)
                .toList();
    }

    /**
     * Get a specific token by ID (for the token owner).
     */
    @Transactional(readOnly = true)
    public Optional<PersonalAccessToken> getToken(UUID tokenId, UUID userId) {
        return repository.findByIdAndUserId(tokenId, userId)
                .map(this::mapToModel);
    }

    /**
     * Validate a token and return the associated token info if valid.
     */
    @Transactional
    public Optional<PersonalAccessToken> validateToken(String plainToken, String clientIp) {
        if (plainToken == null || !plainToken.startsWith(TOKEN_PREFIX)) {
            return Optional.empty();
        }

        String tokenWithoutPrefix = plainToken.substring(TOKEN_PREFIX.length());
        String tokenHash = hashToken(tokenWithoutPrefix);

        return repository.findByTokenHash(tokenHash)
                .filter(PersonalAccessTokenEntity::isValid)
                .map(entity -> {
                    // Update last used timestamp
                    repository.updateLastUsed(entity.getId(), Instant.now(), clientIp);
                    return mapToModel(entity);
                });
    }

    /**
     * Revoke a specific token.
     */
    @Transactional
    public boolean revokeToken(UUID tokenId, UUID userId) {
        Optional<PersonalAccessTokenEntity> tokenOpt = repository.findByIdAndUserId(tokenId, userId);
        if (tokenOpt.isEmpty()) {
            return false;
        }

        PersonalAccessTokenEntity entity = tokenOpt.get();
        if (entity.isRevoked()) {
            return false;
        }

        repository.revokeToken(tokenId, Instant.now());
        log.info("Revoked PAT {} for user {}", tokenId, userId);
        return true;
    }

    /**
     * Revoke all tokens for a user.
     */
    @Transactional
    public int revokeAllTokens(UUID userId) {
        long activeTokens = repository.countActiveTokensByUserId(userId);
        if (activeTokens > 0) {
            repository.revokeAllUserTokens(userId, Instant.now());
            log.info("Revoked {} tokens for user {}", activeTokens, userId);
        }
        return (int) activeTokens;
    }

    /**
     * Generate a cryptographically secure random token.
     */
    private String generateSecureToken() {
        byte[] bytes = new byte[TOKEN_LENGTH];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Hash a token using SHA-256.
     */
    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private PersonalAccessToken mapToModel(PersonalAccessTokenEntity entity) {
        return PersonalAccessToken.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .name(entity.getName())
                .description(entity.getDescription())
                .tokenPrefix(entity.getTokenPrefix())
                .scopes(entity.getScopes())
                .createdAt(entity.getCreatedAt())
                .expiresAt(entity.getExpiresAt())
                .lastUsedAt(entity.getLastUsedAt())
                .lastUsedIp(entity.getLastUsedIp())
                .revoked(entity.isRevoked())
                .revokedAt(entity.getRevokedAt())
                .build();
    }

    public record CreateTokenResult(PersonalAccessToken token, String plainToken) {}
}
