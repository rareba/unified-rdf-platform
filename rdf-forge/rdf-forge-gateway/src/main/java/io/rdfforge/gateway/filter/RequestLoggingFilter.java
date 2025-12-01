package io.rdfforge.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Component
public class RequestLoggingFilter implements GlobalFilter, Ordered {
    
    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);
    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String START_TIME_ATTR = "startTime";
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startTime = System.currentTimeMillis();
        
        String requestId = exchange.getRequest().getHeaders().getFirst(REQUEST_ID_HEADER);
        if (requestId == null) {
            requestId = UUID.randomUUID().toString();
        }
        
        ServerHttpRequest request = exchange.getRequest().mutate()
            .header(REQUEST_ID_HEADER, requestId)
            .build();
        
        ServerWebExchange mutatedExchange = exchange.mutate()
            .request(request)
            .build();
        mutatedExchange.getAttributes().put(START_TIME_ATTR, startTime);
        
        final String reqId = requestId;
        
        log.info("[{}] {} {} - Started", reqId, request.getMethod(), request.getPath());
        
        return chain.filter(mutatedExchange)
            .doOnSuccess(aVoid -> {
                long duration = System.currentTimeMillis() - startTime;
                int status = exchange.getResponse().getStatusCode() != null 
                    ? exchange.getResponse().getStatusCode().value() 
                    : 0;
                log.info("[{}] {} {} - {} {}ms", reqId, request.getMethod(), request.getPath(), status, duration);
            })
            .doOnError(throwable -> {
                long duration = System.currentTimeMillis() - startTime;
                log.error("[{}] {} {} - Error {}ms: {}", reqId, request.getMethod(), request.getPath(), duration, throwable.getMessage());
            });
    }
    
    @Override
    public int getOrder() {
        return -1;
    }
}
