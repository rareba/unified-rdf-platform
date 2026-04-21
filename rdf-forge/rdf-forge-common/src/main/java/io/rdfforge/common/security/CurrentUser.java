package io.rdfforge.common.security;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Parameter annotation that injects the gateway-authenticated user
 * (parsed from X-User-Id / X-User-Email / X-User-Roles headers)
 * into a controller method parameter of type {@link AuthUser}.
 *
 * <p>Example:
 * <pre>{@code
 * @DeleteMapping("/{id}")
 * public ResponseEntity<Void> delete(@PathVariable UUID id, @CurrentUser AuthUser user) {
 *     ...
 * }
 * }</pre>
 *
 * <p>If {@code required = true} (default) and the gateway did not forward
 * an X-User-Id header, a 401 Unauthorized is raised.  The headers are only
 * ever trusted when forwarded by the gateway — the gateway strips any
 * client-supplied identity headers before injecting its own.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {

    /**
     * Whether the user is required. If true (default), a missing X-User-Id
     * header results in an AuthenticationCredentialsNotFoundException → 401.
     * If false, the argument resolver returns an anonymous {@link AuthUser}.
     */
    boolean required() default true;
}
