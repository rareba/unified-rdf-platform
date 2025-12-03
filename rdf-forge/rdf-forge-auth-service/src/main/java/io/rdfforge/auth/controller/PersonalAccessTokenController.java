package io.rdfforge.auth.controller;

import io.rdfforge.auth.service.PersonalAccessTokenService;
import io.rdfforge.common.model.PersonalAccessToken;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/auth/tokens")
@RequiredArgsConstructor
@Tag(name = "Personal Access Tokens", description = "Manage Personal Access Tokens for API authentication")
public class PersonalAccessTokenController {

    private final PersonalAccessTokenService tokenService;

    @Operation(summary = "List all tokens", description = "Get all Personal Access Tokens for the current user")
    @GetMapping
    public ResponseEntity<List<PersonalAccessToken>> listTokens(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @RequestParam(defaultValue = "false") boolean includeRevoked) {

        // In standalone mode, use a default user ID
        if (userId == null) {
            userId = getDefaultUserId();
        }

        List<PersonalAccessToken> tokens = tokenService.listUserTokens(userId, includeRevoked);
        return ResponseEntity.ok(tokens);
    }

    @Operation(summary = "Create a new token", description = "Generate a new Personal Access Token. The token value is only shown once.")
    @PostMapping
    public ResponseEntity<CreateTokenResponse> createToken(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @Valid @RequestBody CreateTokenRequest request) {

        if (userId == null) {
            userId = getDefaultUserId();
        }

        try {
            PersonalAccessTokenService.CreateTokenResult result = tokenService.createToken(
                    userId,
                    request.getName(),
                    request.getDescription(),
                    request.getExpiration(),
                    request.getScopes()
            );

            CreateTokenResponse response = new CreateTokenResponse();
            response.setToken(result.token());
            response.setPlainToken(result.plainToken());

            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @Operation(summary = "Get token details", description = "Get details of a specific token (without the token value)")
    @GetMapping("/{tokenId}")
    public ResponseEntity<PersonalAccessToken> getToken(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @PathVariable UUID tokenId) {

        if (userId == null) {
            userId = getDefaultUserId();
        }

        return tokenService.getToken(tokenId, userId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(summary = "Revoke a token", description = "Revoke a specific Personal Access Token")
    @DeleteMapping("/{tokenId}")
    public ResponseEntity<Void> revokeToken(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId,
            @PathVariable UUID tokenId) {

        if (userId == null) {
            userId = getDefaultUserId();
        }

        boolean revoked = tokenService.revokeToken(tokenId, userId);
        return revoked ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    @Operation(summary = "Revoke all tokens", description = "Revoke all Personal Access Tokens for the current user")
    @DeleteMapping
    public ResponseEntity<RevokeAllResponse> revokeAllTokens(
            @RequestHeader(value = "X-User-Id", required = false) UUID userId) {

        if (userId == null) {
            userId = getDefaultUserId();
        }

        int revokedCount = tokenService.revokeAllTokens(userId);
        RevokeAllResponse response = new RevokeAllResponse();
        response.setRevokedCount(revokedCount);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Validate a token", description = "Validate a token and return its metadata (for internal use)")
    @PostMapping("/validate")
    public ResponseEntity<PersonalAccessToken> validateToken(
            @RequestBody ValidateTokenRequest request,
            @RequestHeader(value = "X-Forwarded-For", required = false) String forwardedFor,
            @RequestHeader(value = "X-Real-IP", required = false) String realIp) {

        String clientIp = forwardedFor != null ? forwardedFor.split(",")[0].trim()
                : (realIp != null ? realIp : "unknown");

        return tokenService.validateToken(request.getToken(), clientIp)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
    }

    private UUID getDefaultUserId() {
        // Default user ID for standalone mode
        return UUID.fromString("00000000-0000-0000-0000-000000000001");
    }

    @Data
    public static class CreateTokenRequest {
        @NotBlank
        @Size(min = 1, max = 100)
        private String name;

        @Size(max = 500)
        private String description;

        private PersonalAccessToken.Expiration expiration;

        private Set<String> scopes;
    }

    @Data
    public static class CreateTokenResponse {
        private PersonalAccessToken token;
        private String plainToken;
    }

    @Data
    public static class ValidateTokenRequest {
        @NotBlank
        private String token;
    }

    @Data
    public static class RevokeAllResponse {
        private int revokedCount;
    }
}
