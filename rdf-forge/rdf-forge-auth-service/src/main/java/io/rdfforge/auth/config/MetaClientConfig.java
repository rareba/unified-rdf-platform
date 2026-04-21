package io.rdfforge.auth.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Lightweight RestTemplate used by {@code MetaController} to fan out
 * to every backend service's /api/v1/extensions/* endpoint.
 *
 * <p>Timeouts are intentionally tight — the catalog UI is interactive and
 * must fail fast when a service is down.
 */
@Configuration
public class MetaClientConfig {

    @Bean("metaRestTemplate")
    public RestTemplate metaRestTemplate(RestTemplateBuilder builder) {
        return builder
                .connectTimeout(Duration.ofSeconds(2))
                .readTimeout(Duration.ofSeconds(5))
                .build();
    }
}
