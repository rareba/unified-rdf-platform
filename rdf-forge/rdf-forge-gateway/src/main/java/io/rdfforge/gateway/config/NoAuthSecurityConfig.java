package io.rdfforge.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

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
@Profile("noauth")
public class NoAuthSecurityConfig {
    
    @Bean
    public SecurityWebFilterChain noAuthSecurityFilterChain(ServerHttpSecurity http) {
        return http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .authorizeExchange(exchanges -> exchanges.anyExchange().permitAll())
            .build();
    }
}