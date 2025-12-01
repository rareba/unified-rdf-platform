package io.rdfforge.gateway.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class GatewayController {
    
    @GetMapping("/health")
    public Mono<ResponseEntity<Map<String, Object>>> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", Instant.now().toString());
        health.put("service", "rdf-forge-gateway");
        return Mono.just(ResponseEntity.ok(health));
    }
    
    @GetMapping("/public/info")
    public Mono<ResponseEntity<Map<String, Object>>> info() {
        Map<String, Object> info = new HashMap<>();
        info.put("name", "RDF Forge API Gateway");
        info.put("version", "1.0.0");
        info.put("description", "Unified RDF Data Platform - API Gateway");
        
        Map<String, String> services = new HashMap<>();
        services.put("pipeline", "/api/v1/pipelines");
        services.put("shacl", "/api/v1/shapes");
        services.put("job", "/api/v1/jobs");
        services.put("data", "/api/v1/data");
        services.put("dimension", "/api/v1/dimensions");
        services.put("triplestore", "/api/v1/triplestores");
        info.put("endpoints", services);
        
        return Mono.just(ResponseEntity.ok(info));
    }
    
    @GetMapping("/fallback")
    public Mono<ResponseEntity<Map<String, Object>>> fallback() {
        Map<String, Object> response = new HashMap<>();
        response.put("error", "Service Unavailable");
        response.put("message", "The requested service is temporarily unavailable. Please try again later.");
        response.put("timestamp", Instant.now().toString());
        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(response));
    }
}
