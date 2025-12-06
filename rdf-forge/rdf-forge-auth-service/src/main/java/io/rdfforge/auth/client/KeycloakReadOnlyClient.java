package io.rdfforge.auth.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;

import java.util.*;

/**
 * Read-only client for Keycloak Admin REST API.
 * Used to fetch users and roles from Keycloak without modification capabilities.
 */
@Component
public class KeycloakReadOnlyClient {

    private final RestTemplate restTemplate;

    @Value("${keycloak.admin.url:http://localhost:8080}")
    private String keycloakUrl;

    @Value("${keycloak.admin.realm:rdfforge}")
    private String realm;

    @Value("${keycloak.admin.client-id:admin-cli}")
    private String clientId;

    @Value("${keycloak.admin.client-secret:}")
    private String clientSecret;

    @Value("${keycloak.admin.username:admin}")
    private String adminUsername;

    @Value("${keycloak.admin.password:admin}")
    private String adminPassword;

    private String accessToken;
    private long tokenExpiresAt;

    public KeycloakReadOnlyClient() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * Fetch all users from Keycloak realm
     */
    public List<KeycloakUser> getUsers() {
        try {
            String url = String.format("%s/admin/realms/%s/users", keycloakUrl, realm);
            HttpHeaders headers = createAuthHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<KeycloakUser[]> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                KeycloakUser[].class
            );

            return response.getBody() != null
                ? Arrays.asList(response.getBody())
                : Collections.emptyList();
        } catch (HttpClientErrorException e) {
            throw new KeycloakClientException("Failed to fetch users from Keycloak", e);
        }
    }

    /**
     * Fetch a single user by ID
     */
    public Optional<KeycloakUser> getUser(String userId) {
        try {
            String url = String.format("%s/admin/realms/%s/users/%s", keycloakUrl, realm, userId);
            HttpHeaders headers = createAuthHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<KeycloakUser> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                KeycloakUser.class
            );

            return Optional.ofNullable(response.getBody());
        } catch (HttpClientErrorException.NotFound e) {
            return Optional.empty();
        } catch (HttpClientErrorException e) {
            throw new KeycloakClientException("Failed to fetch user from Keycloak", e);
        }
    }

    /**
     * Fetch all realm roles
     */
    public List<KeycloakRole> getRoles() {
        try {
            String url = String.format("%s/admin/realms/%s/roles", keycloakUrl, realm);
            HttpHeaders headers = createAuthHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<KeycloakRole[]> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                KeycloakRole[].class
            );

            return response.getBody() != null
                ? Arrays.asList(response.getBody())
                : Collections.emptyList();
        } catch (HttpClientErrorException e) {
            throw new KeycloakClientException("Failed to fetch roles from Keycloak", e);
        }
    }

    /**
     * Get roles assigned to a user
     */
    public List<KeycloakRole> getUserRoles(String userId) {
        try {
            String url = String.format("%s/admin/realms/%s/users/%s/role-mappings/realm",
                keycloakUrl, realm, userId);
            HttpHeaders headers = createAuthHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<KeycloakRole[]> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                KeycloakRole[].class
            );

            return response.getBody() != null
                ? Arrays.asList(response.getBody())
                : Collections.emptyList();
        } catch (HttpClientErrorException e) {
            throw new KeycloakClientException("Failed to fetch user roles from Keycloak", e);
        }
    }

    /**
     * Count users with a specific role
     */
    public int countUsersWithRole(String roleName) {
        try {
            String url = String.format("%s/admin/realms/%s/roles/%s/users",
                keycloakUrl, realm, roleName);
            HttpHeaders headers = createAuthHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<KeycloakUser[]> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                KeycloakUser[].class
            );

            return response.getBody() != null ? response.getBody().length : 0;
        } catch (HttpClientErrorException e) {
            return 0;
        }
    }

    /**
     * Check if Keycloak is available
     */
    public boolean isAvailable() {
        try {
            String url = String.format("%s/admin/realms/%s", keycloakUrl, realm);
            HttpHeaders headers = createAuthHeaders();
            HttpEntity<Void> entity = new HttpEntity<>(headers);

            ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                entity,
                String.class
            );

            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            return false;
        }
    }

    private HttpHeaders createAuthHeaders() {
        ensureValidToken();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(accessToken);
        return headers;
    }

    private synchronized void ensureValidToken() {
        if (accessToken == null || System.currentTimeMillis() >= tokenExpiresAt) {
            refreshToken();
        }
    }

    private void refreshToken() {
        try {
            String tokenUrl = String.format("%s/realms/master/protocol/openid-connect/token", keycloakUrl);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

            String body;
            if (clientSecret != null && !clientSecret.isEmpty()) {
                // Client credentials grant
                body = String.format(
                    "grant_type=client_credentials&client_id=%s&client_secret=%s",
                    clientId, clientSecret
                );
            } else {
                // Password grant
                body = String.format(
                    "grant_type=password&client_id=%s&username=%s&password=%s",
                    clientId, adminUsername, adminPassword
                );
            }

            HttpEntity<String> entity = new HttpEntity<>(body, headers);
            ResponseEntity<TokenResponse> response = restTemplate.exchange(
                tokenUrl,
                HttpMethod.POST,
                entity,
                TokenResponse.class
            );

            TokenResponse tokenResponse = response.getBody();
            if (tokenResponse != null) {
                this.accessToken = tokenResponse.accessToken;
                // Set expiry 30 seconds before actual expiry for safety
                this.tokenExpiresAt = System.currentTimeMillis() +
                    (tokenResponse.expiresIn - 30) * 1000L;
            }
        } catch (Exception e) {
            throw new KeycloakClientException("Failed to obtain Keycloak admin token", e);
        }
    }

    // Inner classes for API responses

    public static class KeycloakUser {
        public String id;
        public String username;
        public String email;
        public String firstName;
        public String lastName;
        public boolean enabled;
        public Long createdTimestamp;
        public Map<String, List<String>> attributes;
    }

    public static class KeycloakRole {
        public String id;
        public String name;
        public String description;
        public boolean composite;
        public boolean clientRole;
        public String containerId;
    }

    private static class TokenResponse {
        public String accessToken;
        public int expiresIn;
        public String tokenType;

        @com.fasterxml.jackson.annotation.JsonProperty("access_token")
        public void setAccessToken(String accessToken) {
            this.accessToken = accessToken;
        }

        @com.fasterxml.jackson.annotation.JsonProperty("expires_in")
        public void setExpiresIn(int expiresIn) {
            this.expiresIn = expiresIn;
        }

        @com.fasterxml.jackson.annotation.JsonProperty("token_type")
        public void setTokenType(String tokenType) {
            this.tokenType = tokenType;
        }
    }

    public static class KeycloakClientException extends RuntimeException {
        public KeycloakClientException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
