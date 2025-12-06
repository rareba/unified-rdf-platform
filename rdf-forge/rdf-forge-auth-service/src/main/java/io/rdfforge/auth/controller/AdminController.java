package io.rdfforge.auth.controller;

import io.rdfforge.auth.client.KeycloakReadOnlyClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Admin controller for user and role management.
 * Provides read-only access to Keycloak users and roles.
 * Full user/role management should be done through Keycloak Admin Console.
 */
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasRole('admin')")
public class AdminController {

    private final KeycloakReadOnlyClient keycloakClient;

    @Autowired
    public AdminController(KeycloakReadOnlyClient keycloakClient) {
        this.keycloakClient = keycloakClient;
    }

    /**
     * List all users from Keycloak
     */
    @GetMapping("/users")
    public ResponseEntity<List<UserInfo>> listUsers() {
        try {
            List<KeycloakReadOnlyClient.KeycloakUser> keycloakUsers = keycloakClient.getUsers();

            List<UserInfo> users = keycloakUsers.stream()
                .map(this::toUserInfo)
                .collect(Collectors.toList());

            // Enrich with roles
            for (UserInfo user : users) {
                List<KeycloakReadOnlyClient.KeycloakRole> roles = keycloakClient.getUserRoles(user.id);
                user.roles = roles.stream()
                    .map(r -> r.name)
                    .collect(Collectors.toList());
            }

            return ResponseEntity.ok(users);
        } catch (KeycloakReadOnlyClient.KeycloakClientException e) {
            // Return demo users if Keycloak is not available
            return ResponseEntity.ok(getDemoUsers());
        }
    }

    /**
     * Get a single user by ID
     */
    @GetMapping("/users/{id}")
    public ResponseEntity<UserInfo> getUser(@PathVariable String id) {
        try {
            return keycloakClient.getUser(id)
                .map(user -> {
                    UserInfo userInfo = toUserInfo(user);
                    List<KeycloakReadOnlyClient.KeycloakRole> roles = keycloakClient.getUserRoles(id);
                    userInfo.roles = roles.stream()
                        .map(r -> r.name)
                        .collect(Collectors.toList());
                    return ResponseEntity.ok(userInfo);
                })
                .orElse(ResponseEntity.notFound().build());
        } catch (KeycloakReadOnlyClient.KeycloakClientException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * List all roles from Keycloak
     */
    @GetMapping("/roles")
    public ResponseEntity<List<RoleInfo>> listRoles() {
        try {
            List<KeycloakReadOnlyClient.KeycloakRole> keycloakRoles = keycloakClient.getRoles();

            List<RoleInfo> roles = keycloakRoles.stream()
                .filter(r -> !r.name.startsWith("uma_") && !r.name.equals("offline_access"))
                .map(role -> {
                    RoleInfo roleInfo = new RoleInfo();
                    roleInfo.name = role.name;
                    roleInfo.description = role.description != null ? role.description : "";
                    roleInfo.permissions = inferPermissions(role.name);
                    roleInfo.userCount = keycloakClient.countUsersWithRole(role.name);
                    roleInfo.isDefault = role.name.equals("viewer");
                    return roleInfo;
                })
                .collect(Collectors.toList());

            return ResponseEntity.ok(roles);
        } catch (KeycloakReadOnlyClient.KeycloakClientException e) {
            // Return demo roles if Keycloak is not available
            return ResponseEntity.ok(getDemoRoles());
        }
    }

    /**
     * Check system health
     */
    @GetMapping("/system/health")
    public ResponseEntity<Map<String, Object>> getSystemHealth() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", Instant.now().toString());
        health.put("keycloak", keycloakClient.isAvailable() ? "UP" : "DOWN");
        return ResponseEntity.ok(health);
    }

    /**
     * Get system information
     */
    @GetMapping("/system/info")
    public ResponseEntity<Map<String, Object>> getSystemInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("version", "1.0.0");
        info.put("buildTime", Instant.now().toString());
        info.put("javaVersion", System.getProperty("java.version"));
        info.put("environment", System.getProperty("spring.profiles.active", "development"));
        return ResponseEntity.ok(info);
    }

    private UserInfo toUserInfo(KeycloakReadOnlyClient.KeycloakUser user) {
        UserInfo info = new UserInfo();
        info.id = user.id;
        info.username = user.username;
        info.email = user.email;
        info.firstName = user.firstName != null ? user.firstName : "";
        info.lastName = user.lastName != null ? user.lastName : "";
        info.enabled = user.enabled;
        info.roles = new ArrayList<>();
        if (user.createdTimestamp != null) {
            info.createdAt = Instant.ofEpochMilli(user.createdTimestamp).toString();
        }
        // Get last login from attributes if available
        if (user.attributes != null && user.attributes.containsKey("lastLogin")) {
            List<String> lastLogin = user.attributes.get("lastLogin");
            if (!lastLogin.isEmpty()) {
                info.lastLogin = lastLogin.get(0);
            }
        }
        return info;
    }

    private List<String> inferPermissions(String roleName) {
        switch (roleName.toLowerCase()) {
            case "admin":
                return Arrays.asList("read", "write", "delete", "admin", "manage_users");
            case "editor":
                return Arrays.asList("read", "write", "delete");
            case "viewer":
                return Collections.singletonList("read");
            default:
                return Collections.singletonList("read");
        }
    }

    private List<UserInfo> getDemoUsers() {
        UserInfo admin = new UserInfo();
        admin.id = "1";
        admin.username = "admin";
        admin.email = "admin@example.org";
        admin.firstName = "Admin";
        admin.lastName = "User";
        admin.enabled = true;
        admin.roles = Collections.singletonList("admin");
        admin.createdAt = "2024-01-01T00:00:00Z";
        admin.lastLogin = Instant.now().toString();

        UserInfo editor = new UserInfo();
        editor.id = "2";
        editor.username = "editor";
        editor.email = "editor@example.org";
        editor.firstName = "Editor";
        editor.lastName = "User";
        editor.enabled = true;
        editor.roles = Collections.singletonList("editor");
        editor.createdAt = "2024-01-15T00:00:00Z";

        UserInfo viewer = new UserInfo();
        viewer.id = "3";
        viewer.username = "viewer";
        viewer.email = "viewer@example.org";
        viewer.firstName = "Viewer";
        viewer.lastName = "User";
        viewer.enabled = true;
        viewer.roles = Collections.singletonList("viewer");
        viewer.createdAt = "2024-02-01T00:00:00Z";

        return Arrays.asList(admin, editor, viewer);
    }

    private List<RoleInfo> getDemoRoles() {
        RoleInfo admin = new RoleInfo();
        admin.name = "admin";
        admin.description = "Full system administrator with all permissions";
        admin.permissions = Arrays.asList("read", "write", "delete", "admin", "manage_users");
        admin.userCount = 1;
        admin.isDefault = false;

        RoleInfo editor = new RoleInfo();
        editor.name = "editor";
        editor.description = "Can create and edit pipelines, shapes, and data";
        editor.permissions = Arrays.asList("read", "write", "delete");
        editor.userCount = 1;
        editor.isDefault = false;

        RoleInfo viewer = new RoleInfo();
        viewer.name = "viewer";
        viewer.description = "Read-only access to all resources";
        viewer.permissions = Collections.singletonList("read");
        viewer.userCount = 1;
        viewer.isDefault = true;

        return Arrays.asList(admin, editor, viewer);
    }

    // DTOs

    public static class UserInfo {
        public String id;
        public String username;
        public String email;
        public String firstName;
        public String lastName;
        public boolean enabled;
        public List<String> roles;
        public String createdAt;
        public String lastLogin;
    }

    public static class RoleInfo {
        public String name;
        public String description;
        public List<String> permissions;
        public int userCount;
        public boolean isDefault;
    }
}
