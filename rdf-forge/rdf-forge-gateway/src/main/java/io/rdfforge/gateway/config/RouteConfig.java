package io.rdfforge.gateway.config;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RouteConfig {
    
    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
            .route("pipeline-service", r -> r
                .path("/api/v1/pipelines/**", "/api/v1/operations/**", "/api/v1/templates/**")
                .filters(f -> f
                    .stripPrefix(0)
                    .addRequestHeader("X-Gateway-Time", String.valueOf(System.currentTimeMillis()))
                )
                .uri("lb://pipeline-service")
            )
            .route("shacl-service", r -> r
                .path("/api/v1/shapes/**", "/api/v1/validation/**")
                .filters(f -> f
                    .stripPrefix(0)
                    .addRequestHeader("X-Gateway-Time", String.valueOf(System.currentTimeMillis()))
                )
                .uri("lb://shacl-service")
            )
            .route("job-service", r -> r
                .path("/api/v1/jobs/**", "/api/v1/schedules/**")
                .filters(f -> f
                    .stripPrefix(0)
                    .addRequestHeader("X-Gateway-Time", String.valueOf(System.currentTimeMillis()))
                )
                .uri("lb://job-service")
            )
            .route("data-service", r -> r
                .path("/api/v1/data/**")
                .filters(f -> f
                    .stripPrefix(0)
                    .addRequestHeader("X-Gateway-Time", String.valueOf(System.currentTimeMillis()))
                )
                .uri("lb://data-service")
            )
            .route("dimension-service", r -> r
                .path("/api/v1/dimensions/**", "/api/v1/hierarchies/**")
                .filters(f -> f
                    .stripPrefix(0)
                    .addRequestHeader("X-Gateway-Time", String.valueOf(System.currentTimeMillis()))
                )
                .uri("lb://dimension-service")
            )
            .route("triplestore-service", r -> r
                .path("/api/v1/triplestores/**", "/api/v1/sparql/**", "/api/v1/graphs/**")
                .filters(f -> f
                    .stripPrefix(0)
                    .addRequestHeader("X-Gateway-Time", String.valueOf(System.currentTimeMillis()))
                )
                .uri("lb://triplestore-service")
            )
            .build();
    }
}
