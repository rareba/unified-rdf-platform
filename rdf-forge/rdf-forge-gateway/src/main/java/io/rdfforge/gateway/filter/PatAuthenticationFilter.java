package io.rdfforge.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Global filter that intercepts requests with Personal Access Tokens (PAT)
 * and validates them against the auth service.
 *
 * PAT tokens are expected in the Authorization header as:
 * Authorization: Bearer ccx_xxxxx...
 *
 * This filter runs before JWT authentication and handles PAT tokens specifically.
 */
@Component
@Slf4j
public class PatAuthenticationFilter implements GlobalFilter, Ordered {

    private static final String PAT_PREFIX = "ccx_";
    private static final String BEARER_PREFIX = "Bearer ";

    private final WebClient webClient;
    private final boolean patEnabled;

    public PatAuthenticationFilter(
            WebClient.Builder webClientBuilder,
            @Value("${AUTH_SERVICE_URL:http://auth-service:8086}") String authServiceUrl,
            @Value("${pat.authentication.enabled:true}") boolean patEnabled) {
        this.webClient = webClientBuilder.baseUrl(authServiceUrl).build();
        this.patEnabled = patEnabled;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!patEnabled) {
            return chain.filter(exchange);
        }

        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        // Skip authentication for public endpoints
        if (isPublicEndpoint(path)) {
            return chain.filter(exchange);
        }

        // Check for Authorization header
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            return chain.filter(exchange);
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        // Only process PAT tokens (starting with ccx_)
        if (!token.startsWith(PAT_PREFIX)) {
            // Let JWT authentication handle this
            return chain.filter(exchange);
        }

        // Validate PAT token
        return validatePatToken(token, request)
                .flatMap(validationResult -> {
                    if (validationResult.isValid()) {
                        // Add user ID header and continue
                        ServerHttpRequest mutatedRequest = request.mutate()
                                .header("X-User-Id", validationResult.userId())
                                .header("X-Auth-Type", "PAT")
                                .header("X-Token-Name", validationResult.tokenName())
                                .build();
                        return chain.filter(exchange.mutate().request(mutatedRequest).build());
                    } else {
                        // Invalid token
                        exchange.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
                        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
                        return exchange.getResponse().writeWith(
                                Mono.just(exchange.getResponse().bufferFactory()
                                        .wrap("{\"error\":\"Invalid or expired token\"}".getBytes()))
                        );
                    }
                })
                .onErrorResume(e -> {
                    log.error("Error validating PAT token", e);
                    // On error, let the request continue without authentication
                    // The backend service will handle unauthorized access
                    return chain.filter(exchange);
                });
    }

    private Mono<PatValidationResult> validatePatToken(String token, ServerHttpRequest request) {
        String clientIp = extractClientIp(request);

        return webClient.post()
                .uri("/api/v1/auth/tokens/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(new ValidateTokenRequest(token))
                .header("X-Forwarded-For", clientIp)
                .retrieve()
                .bodyToMono(PatTokenResponse.class)
                .map(response -> new PatValidationResult(
                        true,
                        response.userId().toString(),
                        response.name()
                ))
                .onErrorReturn(new PatValidationResult(false, null, null));
    }

    private String extractClientIp(ServerHttpRequest request) {
        List<String> forwardedFor = request.getHeaders().get("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isEmpty()) {
            return forwardedFor.get(0).split(",")[0].trim();
        }
        String realIp = request.getHeaders().getFirst("X-Real-IP");
        if (realIp != null) {
            return realIp;
        }
        if (request.getRemoteAddress() != null) {
            return request.getRemoteAddress().getAddress().getHostAddress();
        }
        return "unknown";
    }

    private boolean isPublicEndpoint(String path) {
        return path.startsWith("/actuator/") ||
               path.startsWith("/api-docs") ||
               path.startsWith("/swagger-ui") ||
               path.equals("/swagger-ui.html") ||
               path.startsWith("/api/v1/public/") ||
               path.equals("/api/v1/auth/tokens/validate");
    }

    @Override
    public int getOrder() {
        // Run before other filters but after security
        return -100;
    }

    record ValidateTokenRequest(String token) {}
    record PatTokenResponse(java.util.UUID userId, String name) {}
    record PatValidationResult(boolean isValid, String userId, String tokenName) {}
}
