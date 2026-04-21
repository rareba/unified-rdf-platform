package io.rdfforge.gateway.filter;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Global filter that adds a default X-User-Id header for no-auth mode.
 * This filter is only active when the 'noauth' profile is enabled.
 *
 * WARNING: This filter is for development only and should NOT be used in production!
 */
@Component
@Profile({"noauth"})
@Slf4j
public class NoAuthUserFilter implements GlobalFilter, Ordered {

    private static final String DEFAULT_USER_ID = "00000000-0000-0000-0000-000000000001";
    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_EMAIL_HEADER = "X-User-Email";
    private static final String USER_ROLES_HEADER = "X-User-Roles";
    private static final String AUTH_TYPE_HEADER = "X-Auth-Type";
    private static final String TOKEN_NAME_HEADER = "X-Token-Name";
    private static final Set<String> ALLOWED_PROFILES = Set.of(
        "noauth", "test", "local", "standalone", "graphdb", "fuseki"
    );

    @Autowired
    private Environment environment;

    @PostConstruct
    public void validateNotProduction() {
        List<String> activeProfiles = Arrays.asList(environment.getActiveProfiles());
        for (String profile : activeProfiles) {
            if (!ALLOWED_PROFILES.contains(profile)) {
                throw new IllegalStateException(
                    "CRITICAL SECURITY ERROR: NoAuthUserFilter cannot be used with profile '" + profile + "'! " +
                    "This filter bypasses all authentication and is only allowed with profiles: " + ALLOWED_PROFILES
                );
            }
        }
        log.warn("!!! SECURITY WARNING !!! NoAuth active -- NEVER use in production. Active profiles: {}",
                 activeProfiles);
        log.warn("!!! SECURITY WARNING !!! NoAuthUserFilter will inject default X-User-Id on every request.");
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();

        // Always strip client-supplied identity headers before setting our own.
        // Without this, a caller could spoof X-User-Id to impersonate any user.
        ServerHttpRequest mutatedRequest = request.mutate()
                .headers(headers -> {
                    headers.remove(USER_ID_HEADER);
                    headers.remove(USER_EMAIL_HEADER);
                    headers.remove(USER_ROLES_HEADER);
                    headers.remove(AUTH_TYPE_HEADER);
                    headers.remove(TOKEN_NAME_HEADER);
                })
                .header(USER_ID_HEADER, DEFAULT_USER_ID)
                .build();

        log.debug("NoAuth: stripped client identity headers, injected default X-User-Id");
        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    @Override
    public int getOrder() {
        // Run after authentication filters but before other filters
        return -50;
    }
}
