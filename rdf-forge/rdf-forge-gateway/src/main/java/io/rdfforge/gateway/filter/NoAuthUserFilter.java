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
@Profile("noauth")
@Slf4j
public class NoAuthUserFilter implements GlobalFilter, Ordered {

    private static final String DEFAULT_USER_ID = "00000000-0000-0000-0000-000000000001";
    private static final String USER_ID_HEADER = "X-User-Id";
    private static final Set<String> ALLOWED_PROFILES = Set.of("dev", "local", "test", "noauth", "standalone");

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
        log.warn("NoAuthUserFilter is active. All requests will be processed with a default user ID. " +
                 "This should ONLY be used in development environments.");
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        
        // Only add default user ID if not already present
        if (!request.getHeaders().containsKey(USER_ID_HEADER)) {
            log.debug("Adding default X-User-Id header for no-auth mode");
            
            ServerHttpRequest mutatedRequest = request.mutate()
                    .header(USER_ID_HEADER, DEFAULT_USER_ID)
                    .build();
            
            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        }
        
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        // Run after authentication filters but before other filters
        return -50;
    }
}
