package io.rdfforge.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Represents a Personal Access Token (PAT) for API authentication.
 * PATs can be used as an alternative to Keycloak JWT tokens for
 * programmatic API access.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PersonalAccessToken {
    private UUID id;
    private UUID userId;
    private String name;
    private String description;

    /**
     * The hashed token value. The plain token is only shown once
     * at creation time and cannot be retrieved later.
     */
    private String tokenHash;

    /**
     * Prefix shown to users to identify the token (e.g., "ccx_abc123...")
     */
    private String tokenPrefix;

    /**
     * Scopes/permissions granted to this token.
     * Empty set means full access (same as the user).
     */
    @Builder.Default
    private Set<String> scopes = Set.of();

    private Instant createdAt;
    private Instant expiresAt;
    private Instant lastUsedAt;

    /**
     * IP address that last used this token.
     */
    private String lastUsedIp;

    private boolean revoked;
    private Instant revokedAt;

    /**
     * Check if the token is currently valid (not expired and not revoked).
     */
    public boolean isValid() {
        if (revoked) {
            return false;
        }
        if (expiresAt == null) {
            // No expiration (unlimited)
            return true;
        }
        return Instant.now().isBefore(expiresAt);
    }

    /**
     * Expiration options for PAT tokens.
     */
    public enum Expiration {
        ONE_WEEK(7),
        TWO_WEEKS(14),
        ONE_MONTH(30),
        THREE_MONTHS(90),
        SIX_MONTHS(180),
        ONE_YEAR(365),
        NEVER(0);  // 0 means no expiration

        private final int days;

        Expiration(int days) {
            this.days = days;
        }

        public int getDays() {
            return days;
        }

        public Instant toExpirationDate() {
            if (days == 0) {
                return null;  // Never expires
            }
            return Instant.now().plusSeconds(days * 24L * 60 * 60);
        }
    }
}
