package io.rdfforge.job.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Dedicated home for the outbound RestTemplate. Keeping this here (rather
 * than on {@link io.rdfforge.job.JobServiceApplication}) makes @WebMvcTest
 * slices work: otherwise Spring tries to create the @Bean during slice
 * initialization and fails because RestTemplateAutoConfiguration (which
 * exposes RestTemplateBuilder) is not included in the MVC slice.
 */
@Configuration
public class HttpClientConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(30))
                .build();
    }
}
