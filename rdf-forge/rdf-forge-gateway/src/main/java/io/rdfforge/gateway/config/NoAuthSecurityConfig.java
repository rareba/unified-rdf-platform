package io.rdfforge.gateway.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * No-authentication security configuration for local development and offline mode.
 * This configuration is active when the 'noauth' profile is enabled.
 *
 * Usage: Run the application with --spring.profiles.active=noauth
 * or set SPRING_PROFILES_ACTIVE=noauth environment variable.
 *
 * WARNING: Do not use this profile in production environments!
 */
@Configuration
@EnableWebFluxSecurity
@Profile({"noauth"})
@Slf4j
public class NoAuthSecurityConfig {

    // Only profiles that must NEVER touch production. "docker" was removed because
    // production compose sets SPRING_PROFILES_ACTIVE=docker, which previously caused
    // NoAuth to activate in prod. Keep strictly dev/test-only profiles here.
    // NoAuth may only activate alongside profiles that are NOT valid for a
    // real production deployment. `graphdb` / `fuseki` / `standalone` are
    // triplestore-backend or topology selectors used by the standalone
    // compose and similar dev/demo paths; they never imply auth. `docker`
    // is intentionally absent because the production compose used that
    // name and historically that was how auth got silently disabled.
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
                    "CRITICAL SECURITY ERROR: NoAuthSecurityConfig cannot be used with profile '" + profile + "'! " +
                    "This configuration bypasses all authentication and is only allowed with profiles: " + ALLOWED_PROFILES
                );
            }
        }
        log.warn("!!! SECURITY WARNING !!! NoAuth active -- NEVER use in production. Active profiles: {}",
                 activeProfiles);
        log.warn("!!! SECURITY WARNING !!! NoAuthSecurityConfig bypasses ALL authentication. All endpoints are publicly accessible.");
    }

    @Bean
    public SecurityWebFilterChain noAuthSecurityFilterChain(ServerHttpSecurity http) {
        return http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .authorizeExchange(exchanges -> exchanges.anyExchange().permitAll())
            .build();
    }
}