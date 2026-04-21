package io.rdfforge.common.security;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Immutable snapshot of the gateway-authenticated principal for a single request.
 *
 * <p>Populated from the following request headers which are injected by the
 * gateway after it has validated the caller's credentials. Any client-supplied
 * X-User-* headers are stripped by the gateway before these are set — see
 * {@code AuthenticationFilter} / {@code NoAuthUserFilter} in rdf-forge-gateway.
 *
 * <ul>
 *   <li>X-User-Id — UUID of the user (required for authenticated endpoints)</li>
 *   <li>X-User-Email — display email (optional)</li>
 *   <li>X-User-Roles — comma-separated role list, e.g. "USER,ADMIN" (optional)</li>
 * </ul>
 */
public final class AuthUser {

    public static final String ROLE_ADMIN = "ADMIN";
    public static final String ROLE_SPRING_ADMIN = "ROLE_ADMIN";

    private static final AuthUser ANONYMOUS = new AuthUser(null, null, Collections.emptySet());

    private final UUID id;
    private final String email;
    private final Set<String> roles;

    public AuthUser(UUID id, String email, Set<String> roles) {
        this.id = id;
        this.email = email;
        this.roles = roles == null ? Collections.emptySet() : Set.copyOf(roles);
    }

    /** Sentinel anonymous user — id is null, email is null, roles are empty. */
    public static AuthUser anonymous() {
        return ANONYMOUS;
    }

    public UUID id() {
        return id;
    }

    public String email() {
        return email;
    }

    public Set<String> roles() {
        return roles;
    }

    public boolean isAnonymous() {
        return id == null;
    }

    /**
     * Check if the user has the given role. Matching is case-insensitive and
     * the "ROLE_" prefix is tolerated on either side (Keycloak sends "ADMIN",
     * Spring Security conventions use "ROLE_ADMIN").
     */
    public boolean hasRole(String role) {
        if (role == null || roles.isEmpty()) {
            return false;
        }
        String target = role.toUpperCase();
        String withPrefix = target.startsWith("ROLE_") ? target : "ROLE_" + target;
        String withoutPrefix = target.startsWith("ROLE_") ? target.substring(5) : target;
        for (String r : roles) {
            String up = r.toUpperCase();
            if (up.equals(target) || up.equals(withPrefix) || up.equals(withoutPrefix)) {
                return true;
            }
        }
        return false;
    }

    /** Convenience: is the user an admin (ADMIN or ROLE_ADMIN). */
    public boolean isAdmin() {
        return hasRole(ROLE_ADMIN);
    }

    /**
     * Returns true if this user owns the given resource.
     * Null ownerId means unowned (treat as not-owned by anyone).
     */
    public boolean owns(UUID ownerId) {
        return id != null && ownerId != null && id.equals(ownerId);
    }

    /**
     * Ownership check with admin bypass — the standard authorization predicate
     * for "can this user read/modify/delete this resource?".
     */
    public boolean ownsOrIsAdmin(UUID ownerId) {
        return isAdmin() || owns(ownerId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AuthUser that)) return false;
        return Objects.equals(id, that.id)
            && Objects.equals(email, that.email)
            && Objects.equals(roles, that.roles);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, email, roles);
    }

    @Override
    public String toString() {
        return "AuthUser{id=" + id + ", email=" + email + ", roles=" + roles + "}";
    }
}
