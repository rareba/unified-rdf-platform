package io.rdfforge.gateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import java.security.Principal;

@Component
public class AuthenticationFilter extends AbstractGatewayFilterFactory<AuthenticationFilter.Config> {
    
    public AuthenticationFilter() {
        super(Config.class);
    }
    
    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            return exchange.getPrincipal()
                .map(principal -> addUserHeaders(exchange, principal))
                .defaultIfEmpty(exchange)
                .flatMap(chain::filter);
        };
    }
    
    private ServerWebExchange addUserHeaders(ServerWebExchange exchange, Principal principal) {
        ServerHttpRequest request = exchange.getRequest().mutate()
            .header("X-User-Id", principal.getName())
            .build();
        
        return exchange.mutate().request(request).build();
    }
    
    public static class Config {
        private boolean addUserHeaders = true;
        
        public boolean isAddUserHeaders() {
            return addUserHeaders;
        }
        
        public void setAddUserHeaders(boolean addUserHeaders) {
            this.addUserHeaders = addUserHeaders;
        }
    }
}
