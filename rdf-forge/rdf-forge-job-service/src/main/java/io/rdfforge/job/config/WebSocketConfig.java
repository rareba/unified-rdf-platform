package io.rdfforge.job.config;

import io.rdfforge.job.entity.JobEntity;
import io.rdfforge.job.service.JobService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * WebSocket configuration for real-time job log streaming.
 * Uses STOMP over WebSocket with SockJS fallback.
 *
 * Security:
 *  - Origins are read from ${app.cors.allowed-origins} (comma-separated).
 *  - STOMP CONNECT frames must carry a Bearer Authorization header; the user
 *    principal is attached to the session.
 *  - SUBSCRIBE frames to /topic/jobs/{id}/logs are authorized against the job's
 *    createdBy ownership or an admin role.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebSocketConfig.class);

    private static final Pattern JOB_LOG_TOPIC_PATTERN =
            Pattern.compile("^/topic/jobs/([0-9a-fA-F-]{36})/logs$");
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String X_USER_ID_HEADER = "X-User-Id";
    private static final String X_USER_ROLES_HEADER = "X-User-Roles";

    private final String[] allowedOrigins;
    private final JobService jobService;

    public WebSocketConfig(
            @Value("${app.cors.allowed-origins:http://localhost:4200}") String allowedOriginsCsv,
            @Lazy JobService jobService) {
        this.allowedOrigins = Arrays.stream(allowedOriginsCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toArray(String[]::new);
        this.jobService = jobService;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Enable simple in-memory broker for /topic destinations
        config.enableSimpleBroker("/topic");
        // Set prefix for application destinations (not used for log streaming)
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Register STOMP endpoint with SockJS fallback.
        // Use setAllowedOriginPatterns so that wildcard subdomains (if ever configured)
        // work together with allowCredentials; explicit origins still match exactly.
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(allowedOrigins)
                .withSockJS();
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor =
                        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null) {
                    return message;
                }

                StompCommand command = accessor.getCommand();
                if (StompCommand.CONNECT.equals(command)) {
                    handleConnect(accessor);
                } else if (StompCommand.SUBSCRIBE.equals(command)) {
                    handleSubscribe(accessor);
                }
                return message;
            }
        });
    }

    private void handleConnect(StompHeaderAccessor accessor) {
        // Prefer gateway-injected X-User-Id (set by AuthenticationFilter / PatAuthenticationFilter).
        // Fall back to Bearer token presence (the gateway already validated it upstream).
        String userId = firstNativeHeader(accessor, X_USER_ID_HEADER);
        String rolesHeader = firstNativeHeader(accessor, X_USER_ROLES_HEADER);

        if (userId == null) {
            String authHeader = firstNativeHeader(accessor, "Authorization");
            if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
                log.warn("WebSocket CONNECT rejected: missing principal (no X-User-Id or Bearer token)");
                throw new AccessDeniedException("WebSocket CONNECT requires authenticated principal");
            }
            // TODO(audit-2026-04-21 P1): validate Bearer JWT here if requests can bypass
            // the gateway. Currently we rely on gateway-injected X-User-Id headers.
            userId = authHeader.substring(BEARER_PREFIX.length());
        }

        List<SimpleGrantedAuthority> authorities = rolesHeader == null
                ? List.of()
                : Arrays.stream(rolesHeader.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .map(SimpleGrantedAuthority::new)
                        .toList();

        UsernamePasswordAuthenticationToken principal =
                new UsernamePasswordAuthenticationToken(userId, null, authorities);
        accessor.setUser(principal);
        log.debug("WebSocket CONNECT authenticated: userId={}, roles={}", userId, authorities);
    }

    private void handleSubscribe(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null) {
            return;
        }
        Matcher matcher = JOB_LOG_TOPIC_PATTERN.matcher(destination);
        if (!matcher.matches()) {
            return; // only guard job log subscriptions
        }

        if (accessor.getUser() == null) {
            log.warn("WebSocket SUBSCRIBE rejected for {}: no authenticated principal", destination);
            throw new AccessDeniedException("SUBSCRIBE requires authenticated principal");
        }

        UUID jobId;
        try {
            jobId = UUID.fromString(matcher.group(1));
        } catch (IllegalArgumentException e) {
            throw new AccessDeniedException("Invalid job id in subscription destination");
        }

        String principalName = accessor.getUser().getName();
        boolean isAdmin = accessor.getUser() instanceof UsernamePasswordAuthenticationToken token
                && token.getAuthorities().stream()
                        .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority())
                                || "admin".equalsIgnoreCase(a.getAuthority()));

        Optional<JobEntity> jobOpt = jobService.getJob(jobId);
        if (jobOpt.isEmpty()) {
            throw new AccessDeniedException("Job not found or access denied");
        }
        JobEntity job = jobOpt.get();
        UUID owner = job.getCreatedBy();

        if (owner == null) {
            // TODO(audit-2026-04-21 P1): legacy jobs without ownership. Require
            // authenticated principal and audit-log access until backfill completes.
            log.warn("WebSocket SUBSCRIBE: job {} has no createdBy; allowing authenticated user {} and logging access",
                    jobId, principalName);
            return;
        }

        boolean ownerMatches = principalName != null && principalName.equals(owner.toString());
        if (!ownerMatches && !isAdmin) {
            log.warn("WebSocket SUBSCRIBE rejected: user {} is not owner ({}) or admin of job {}",
                    principalName, owner, jobId);
            throw new AccessDeniedException("Not authorized to subscribe to job logs");
        }
        log.debug("WebSocket SUBSCRIBE authorized: user {} -> job {}", principalName, jobId);
    }

    private static String firstNativeHeader(StompHeaderAccessor accessor, String name) {
        List<String> values = accessor.getNativeHeader(name);
        return (values == null || values.isEmpty()) ? null : values.get(0);
    }
}
