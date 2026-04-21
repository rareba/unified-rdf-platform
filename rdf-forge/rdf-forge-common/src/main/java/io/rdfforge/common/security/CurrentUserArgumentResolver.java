package io.rdfforge.common.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Resolves {@code @CurrentUser AuthUser} parameters on controller methods
 * by parsing the gateway-forwarded identity headers.
 *
 * <p>Headers are only trusted because the gateway strips any client-supplied
 * X-User-* values before injecting its own — see AuthenticationFilter and
 * NoAuthUserFilter in rdf-forge-gateway. Downstream services must never be
 * reached directly from the internet.
 */
@Slf4j
public class CurrentUserArgumentResolver implements HandlerMethodArgumentResolver {

    public static final String HEADER_USER_ID = "X-User-Id";
    public static final String HEADER_USER_EMAIL = "X-User-Email";
    public static final String HEADER_USER_ROLES = "X-User-Roles";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentUser.class)
            && AuthUser.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter,
                                  ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest,
                                  WebDataBinderFactory binderFactory) {
        CurrentUser annotation = parameter.getParameterAnnotation(CurrentUser.class);
        boolean required = annotation == null || annotation.required();

        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        String userIdHeader = request != null ? request.getHeader(HEADER_USER_ID) : null;

        if (userIdHeader == null || userIdHeader.isBlank()) {
            if (required) {
                throw new AuthenticationCredentialsNotFoundException(
                    "Missing X-User-Id header. This endpoint requires an authenticated user " +
                    "(requests must go through the gateway)."
                );
            }
            return AuthUser.anonymous();
        }

        UUID userId;
        try {
            userId = UUID.fromString(userIdHeader.trim());
        } catch (IllegalArgumentException e) {
            // Malformed UUIDs should never reach us from the gateway, but if they
            // do, treat as unauthenticated rather than 500.
            log.warn("Malformed X-User-Id header value (ignored): {}", userIdHeader);
            if (required) {
                throw new AuthenticationCredentialsNotFoundException(
                    "Invalid X-User-Id header value."
                );
            }
            return AuthUser.anonymous();
        }

        String email = request.getHeader(HEADER_USER_EMAIL);
        String rolesHeader = request.getHeader(HEADER_USER_ROLES);
        Set<String> roles = parseRoles(rolesHeader);

        return new AuthUser(userId, email, roles);
    }

    private Set<String> parseRoles(String rolesHeader) {
        if (rolesHeader == null || rolesHeader.isBlank()) {
            return Set.of();
        }
        Set<String> parsed = new HashSet<>();
        Arrays.stream(rolesHeader.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .forEach(parsed::add);
        return parsed;
    }
}
