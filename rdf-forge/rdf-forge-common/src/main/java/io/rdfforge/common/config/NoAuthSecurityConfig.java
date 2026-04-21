package io.rdfforge.common.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Auto-configured no-auth security for backend services when running with the noauth profile.
 * The gateway handles authentication; backend services trust internal network calls.
 *
 * WARNING: Do not use this profile in production environments!
 */
@AutoConfiguration
@Profile({"noauth"})
@ConditionalOnClass(name = "org.springframework.security.config.annotation.web.builders.HttpSecurity")
@Slf4j
public class NoAuthSecurityConfig {

    // Only profiles that must NEVER touch production. "docker" was removed because
    // production compose sets SPRING_PROFILES_ACTIVE=docker, which previously caused
    // NoAuth to activate in prod. Keep strictly dev/test-only profiles here.
    private static final Set<String> ALLOWED_PROFILES = Set.of("noauth", "test", "local");

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
        log.warn("!!! SECURITY WARNING !!! common NoAuthSecurityConfig bypasses ALL backend authentication.");
    }

    @Bean
    public SecurityFilterChain commonNoAuthFilterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
            .build();
    }
}
