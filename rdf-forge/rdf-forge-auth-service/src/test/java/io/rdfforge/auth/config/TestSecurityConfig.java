package io.rdfforge.auth.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import io.rdfforge.common.security.CurrentUserAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Test-only security configuration that permits all requests.
 * This is imported by @WebMvcTest slices to prevent the default
 * Spring Security auto-configuration from blocking controller tests.
 *
 * Controller tests focus on HTTP request/response logic and service delegation;
 * they should not be affected by the production security filter chain.
 */
@TestConfiguration
@Import(CurrentUserAutoConfiguration.class)
public class TestSecurityConfig {

    @Bean
    public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
