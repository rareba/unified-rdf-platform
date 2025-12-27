package io.rdfforge.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RouteConfig {
    
    @Value("${PIPELINE_SERVICE_URL:http://pipeline-service:8080}")
    private String pipelineServiceUrl;
    
    @Value("${SHACL_SERVICE_URL:http://shacl-service:8080}")
    private String shaclServiceUrl;
    
    @Value("${JOB_SERVICE_URL:http://job-service:8080}")
    private String jobServiceUrl;
    
    @Value("${DATA_SERVICE_URL:http://data-service:8080}")
    private String dataServiceUrl;
    
    @Value("${DIMENSION_SERVICE_URL:http://dimension-service:8080}")
    private String dimensionServiceUrl;
    
    @Value("${TRIPLESTORE_SERVICE_URL:http://triplestore-service:8080}")
    private String triplestoreServiceUrl;

    @Value("${AUTH_SERVICE_URL:http://auth-service:8086}")
    private String authServiceUrl;

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
            .route("pipeline-service", r -> r
                .path("/api/v1/pipelines/**", "/api/v1/operations/**", "/api/v1/templates/**")
                .filters(f -> f
                    .stripPrefix(0)
                    .addRequestHeader("X-Gateway-Time", String.valueOf(System.currentTimeMillis()))
                )
                .uri(pipelineServiceUrl)
            )
            .route("shacl-service", r -> r
                .path("/api/v1/shapes/**", "/api/v1/validation/**", "/api/v1/cubes/validate/**")
                .filters(f -> f
                    .stripPrefix(0)
                    .addRequestHeader("X-Gateway-Time", String.valueOf(System.currentTimeMillis()))
                )
                .uri(shaclServiceUrl)
            )
            .route("job-service", r -> r
                .path("/api/v1/jobs/**", "/api/v1/schedules/**")
                .filters(f -> f
                    .stripPrefix(0)
                    .addRequestHeader("X-Gateway-Time", String.valueOf(System.currentTimeMillis()))
                )
                .uri(jobServiceUrl)
            )
            .route("data-service", r -> r
                .path("/api/v1/data/**")
                .filters(f -> f
                    .stripPrefix(0)
                    .addRequestHeader("X-Gateway-Time", String.valueOf(System.currentTimeMillis()))
                )
                .uri(dataServiceUrl)
            )
            .route("dimension-service", r -> r
                .path("/api/v1/dimensions/**", "/api/v1/hierarchies/**", "/api/v1/cubes/**")
                .filters(f -> f
                    .stripPrefix(0)
                    .addRequestHeader("X-Gateway-Time", String.valueOf(System.currentTimeMillis()))
                )
                .uri(dimensionServiceUrl)
            )
            .route("triplestore-service", r -> r
                .path("/api/v1/triplestores/**", "/api/v1/sparql/**", "/api/v1/graphs/**")
                .filters(f -> f
                    .stripPrefix(0)
                    .addRequestHeader("X-Gateway-Time", String.valueOf(System.currentTimeMillis()))
                )
                .uri(triplestoreServiceUrl)
            )
            .route("auth-service", r -> r
                .path("/api/v1/auth/**")
                .filters(f -> f
                    .stripPrefix(0)
                    .addRequestHeader("X-Gateway-Time", String.valueOf(System.currentTimeMillis()))
                )
                .uri(authServiceUrl)
            )
            .build();
    }
}
