package io.rdfforge.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;

/**
 * Security configuration for OAuth2/JWT authentication via Keycloak.
 * This configuration is active when the 'noauth' profile is NOT active.
 * For development without Keycloak, use the 'noauth' profile instead.
 */
@Configuration
@EnableWebFluxSecurity
@Profile("!noauth")
public class SecurityConfig {

    @Value("${springdoc.swagger-ui.enabled:false}")
    private boolean swaggerEnabled;

    @Bean
    public SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .authorizeExchange(exchanges -> {
                exchanges
                    .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                    .pathMatchers("/api/v1/auth/**").permitAll()
                    .pathMatchers("/api/v1/public/**").permitAll()
                    .pathMatchers("/actuator/health", "/actuator/info").permitAll();

                if (swaggerEnabled) {
                    exchanges.pathMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll();
                }

                exchanges.anyExchange().authenticated();
            })
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())
                )
            );

        return http.build();
    }

    private ReactiveJwtAuthenticationConverterAdapter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        return new ReactiveJwtAuthenticationConverterAdapter(converter);
    }
}
