package io.rdfforge.auth.controller;

import io.rdfforge.auth.client.KeycloakReadOnlyClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for AdminController.
 * Tests the controller methods directly (not via MockMvc) to avoid Spring Security
 * complexity while still covering all business logic branches: Keycloak available,
 * Keycloak unavailable (fallback to demo data), role inference, and system endpoints.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AdminController Tests")
class AdminControllerTest {

    @Mock
    private KeycloakReadOnlyClient keycloakClient;

    private AdminController adminController;

    @BeforeEach
    void setUp() {
        adminController = new AdminController(keycloakClient);
    }

    // =========================================================================
    // listUsers Tests
    // =========================================================================

    @Nested
    @DisplayName("listUsers Tests")
    class ListUsersTests {

        @Test
        @DisplayName("Should return 200 with user list from Keycloak when available")
        void listUsers_KeycloakAvailable_ReturnsUsers() {
            KeycloakReadOnlyClient.KeycloakUser user = new KeycloakReadOnlyClient.KeycloakUser();
            user.id = "user-1";
            user.username = "alice";
            user.email = "alice@example.com";
            user.firstName = "Alice";
            user.lastName = "Smith";
            user.enabled = true;
            user.createdTimestamp = 1_700_000_000_000L;

            KeycloakReadOnlyClient.KeycloakRole role = new KeycloakReadOnlyClient.KeycloakRole();
            role.name = "editor";

            when(keycloakClient.getUsers()).thenReturn(List.of(user));
            when(keycloakClient.getUserRoles("user-1")).thenReturn(List.of(role));

            ResponseEntity<List<AdminController.UserInfo>> response = adminController.listUsers();

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals(1, response.getBody().size());

            AdminController.UserInfo userInfo = response.getBody().get(0);
            assertEquals("user-1", userInfo.id);
            assertEquals("alice", userInfo.username);
            assertEquals("Alice", userInfo.firstName);
            assertEquals("Smith", userInfo.lastName);
            assertEquals("alice@example.com", userInfo.email);
            assertTrue(userInfo.enabled);
            assertEquals(List.of("editor"), userInfo.roles);
        }

        @Test
        @DisplayName("Should fall back to demo users when Keycloak is unavailable")
        void listUsers_KeycloakUnavailable_ReturnsDemoUsers() {
            when(keycloakClient.getUsers())
                    .thenThrow(new KeycloakReadOnlyClient.KeycloakClientException(
                            "Keycloak unavailable", new RuntimeException("connection refused")));

            ResponseEntity<List<AdminController.UserInfo>> response = adminController.listUsers();

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());

            // Demo users should include admin, editor, viewer
            List<String> usernames = response.getBody().stream()
                    .map(u -> u.username)
                    .toList();

            assertTrue(usernames.contains("admin"), "Demo users should include admin");
            assertTrue(usernames.contains("editor"), "Demo users should include editor");
            assertTrue(usernames.contains("viewer"), "Demo users should include viewer");
        }

        @Test
        @DisplayName("Should map null firstName/lastName to empty string")
        void listUsers_NullNames_MapsToEmptyString() {
            KeycloakReadOnlyClient.KeycloakUser user = new KeycloakReadOnlyClient.KeycloakUser();
            user.id = "user-2";
            user.username = "bob";
            user.firstName = null;
            user.lastName = null;

            when(keycloakClient.getUsers()).thenReturn(List.of(user));
            when(keycloakClient.getUserRoles("user-2")).thenReturn(List.of());

            ResponseEntity<List<AdminController.UserInfo>> response = adminController.listUsers();

            AdminController.UserInfo userInfo = response.getBody().get(0);
            assertEquals("", userInfo.firstName);
            assertEquals("", userInfo.lastName);
        }

        @Test
        @DisplayName("Should convert createdTimestamp millis to ISO string")
        void listUsers_WithCreatedTimestamp_SetsCreatedAt() {
            KeycloakReadOnlyClient.KeycloakUser user = new KeycloakReadOnlyClient.KeycloakUser();
            user.id = "user-3";
            user.username = "carol";
            user.createdTimestamp = 1_704_067_200_000L; // 2024-01-01T00:00:00Z

            when(keycloakClient.getUsers()).thenReturn(List.of(user));
            when(keycloakClient.getUserRoles("user-3")).thenReturn(List.of());

            ResponseEntity<List<AdminController.UserInfo>> response = adminController.listUsers();

            AdminController.UserInfo userInfo = response.getBody().get(0);
            assertNotNull(userInfo.createdAt);
            assertTrue(userInfo.createdAt.startsWith("2024-01-01"),
                    "createdAt should begin with the date portion 2024-01-01");
        }

        @Test
        @DisplayName("Should populate lastLogin from user attributes when present")
        void listUsers_WithLastLoginAttribute_SetsLastLogin() {
            KeycloakReadOnlyClient.KeycloakUser user = new KeycloakReadOnlyClient.KeycloakUser();
            user.id = "user-4";
            user.username = "dave";
            user.attributes = Map.of("lastLogin", List.of("2024-06-01T10:00:00Z"));

            when(keycloakClient.getUsers()).thenReturn(List.of(user));
            when(keycloakClient.getUserRoles("user-4")).thenReturn(List.of());

            ResponseEntity<List<AdminController.UserInfo>> response = adminController.listUsers();

            assertEquals("2024-06-01T10:00:00Z", response.getBody().get(0).lastLogin);
        }
    }

    // =========================================================================
    // getUser Tests
    // =========================================================================

    @Nested
    @DisplayName("getUser Tests")
    class GetUserTests {

        @Test
        @DisplayName("Should return 200 with user info when user exists in Keycloak")
        void getUser_UserExists_Returns200() {
            String userId = "user-abc";
            KeycloakReadOnlyClient.KeycloakUser user = new KeycloakReadOnlyClient.KeycloakUser();
            user.id = userId;
            user.username = "eve";

            KeycloakReadOnlyClient.KeycloakRole role = new KeycloakReadOnlyClient.KeycloakRole();
            role.name = "viewer";

            when(keycloakClient.getUser(userId)).thenReturn(Optional.of(user));
            when(keycloakClient.getUserRoles(userId)).thenReturn(List.of(role));

            ResponseEntity<AdminController.UserInfo> response = adminController.getUser(userId);

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("eve", response.getBody().username);
            assertEquals(List.of("viewer"), response.getBody().roles);
        }

        @Test
        @DisplayName("Should return 404 when user does not exist in Keycloak")
        void getUser_UserNotFound_Returns404() {
            when(keycloakClient.getUser("nonexistent")).thenReturn(Optional.empty());

            ResponseEntity<AdminController.UserInfo> response = adminController.getUser("nonexistent");

            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        }

        @Test
        @DisplayName("Should return 404 when Keycloak throws an exception")
        void getUser_KeycloakException_Returns404() {
            when(keycloakClient.getUser(anyString()))
                    .thenThrow(new KeycloakReadOnlyClient.KeycloakClientException(
                            "Error", new RuntimeException("timeout")));

            ResponseEntity<AdminController.UserInfo> response = adminController.getUser("some-id");

            assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        }
    }

    // =========================================================================
    // listRoles Tests
    // =========================================================================

    @Nested
    @DisplayName("listRoles Tests")
    class ListRolesTests {

        @Test
        @DisplayName("Should return roles filtered to exclude uma_ and offline_access")
        void listRoles_FiltersSystemRoles() {
            KeycloakReadOnlyClient.KeycloakRole adminRole = new KeycloakReadOnlyClient.KeycloakRole();
            adminRole.name = "admin";
            adminRole.description = "Administrators";

            KeycloakReadOnlyClient.KeycloakRole umaRole = new KeycloakReadOnlyClient.KeycloakRole();
            umaRole.name = "uma_authorization";

            KeycloakReadOnlyClient.KeycloakRole offlineRole = new KeycloakReadOnlyClient.KeycloakRole();
            offlineRole.name = "offline_access";

            when(keycloakClient.getRoles()).thenReturn(List.of(adminRole, umaRole, offlineRole));
            when(keycloakClient.countUsersWithRole("admin")).thenReturn(2);

            ResponseEntity<List<AdminController.RoleInfo>> response = adminController.listRoles();

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals(1, response.getBody().size());
            assertEquals("admin", response.getBody().get(0).name);
        }

        @Test
        @DisplayName("Should fall back to demo roles when Keycloak is unavailable")
        void listRoles_KeycloakUnavailable_ReturnsDemoRoles() {
            when(keycloakClient.getRoles())
                    .thenThrow(new KeycloakReadOnlyClient.KeycloakClientException(
                            "unavailable", new RuntimeException("timeout")));

            ResponseEntity<List<AdminController.RoleInfo>> response = adminController.listRoles();

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());

            List<String> roleNames = response.getBody().stream()
                    .map(r -> r.name)
                    .toList();

            assertTrue(roleNames.contains("admin"));
            assertTrue(roleNames.contains("editor"));
            assertTrue(roleNames.contains("viewer"));
        }

        @Test
        @DisplayName("Should mark viewer role as default")
        void listRoles_ViewerRole_IsMarkedDefault() {
            KeycloakReadOnlyClient.KeycloakRole viewerRole = new KeycloakReadOnlyClient.KeycloakRole();
            viewerRole.name = "viewer";
            viewerRole.description = "Read-only access";

            when(keycloakClient.getRoles()).thenReturn(List.of(viewerRole));
            when(keycloakClient.countUsersWithRole("viewer")).thenReturn(5);

            ResponseEntity<List<AdminController.RoleInfo>> response = adminController.listRoles();

            assertTrue(response.getBody().get(0).isDefault,
                    "viewer role should be marked as the default role");
        }

        @Test
        @DisplayName("Should not mark admin role as default")
        void listRoles_AdminRole_IsNotDefault() {
            KeycloakReadOnlyClient.KeycloakRole adminRole = new KeycloakReadOnlyClient.KeycloakRole();
            adminRole.name = "admin";

            when(keycloakClient.getRoles()).thenReturn(List.of(adminRole));
            when(keycloakClient.countUsersWithRole("admin")).thenReturn(1);

            ResponseEntity<List<AdminController.RoleInfo>> response = adminController.listRoles();

            assertFalse(response.getBody().get(0).isDefault,
                    "admin role should not be marked as default");
        }

        @Test
        @DisplayName("Should use null description as empty string in role info")
        void listRoles_NullDescription_MapsToEmptyString() {
            KeycloakReadOnlyClient.KeycloakRole role = new KeycloakReadOnlyClient.KeycloakRole();
            role.name = "custom-role";
            role.description = null;

            when(keycloakClient.getRoles()).thenReturn(List.of(role));
            when(keycloakClient.countUsersWithRole("custom-role")).thenReturn(0);

            ResponseEntity<List<AdminController.RoleInfo>> response = adminController.listRoles();

            assertEquals("", response.getBody().get(0).description);
        }
    }

    // =========================================================================
    // Permission Inference Tests
    // =========================================================================

    @Nested
    @DisplayName("Permission Inference Tests")
    class PermissionInferenceTests {

        private List<String> getPermissionsForRole(String roleName) {
            KeycloakReadOnlyClient.KeycloakRole role = new KeycloakReadOnlyClient.KeycloakRole();
            role.name = roleName;

            when(keycloakClient.getRoles()).thenReturn(List.of(role));
            when(keycloakClient.countUsersWithRole(roleName)).thenReturn(0);

            ResponseEntity<List<AdminController.RoleInfo>> response = adminController.listRoles();
            return response.getBody().get(0).permissions;
        }

        @Test
        @DisplayName("admin role should have all permissions including manage_users")
        void inferPermissions_AdminRole_HasFullPermissions() {
            List<String> permissions = getPermissionsForRole("admin");

            assertTrue(permissions.contains("read"));
            assertTrue(permissions.contains("write"));
            assertTrue(permissions.contains("delete"));
            assertTrue(permissions.contains("admin"));
            assertTrue(permissions.contains("manage_users"));
        }

        @Test
        @DisplayName("editor role should have read, write, delete permissions")
        void inferPermissions_EditorRole_HasReadWriteDelete() {
            List<String> permissions = getPermissionsForRole("editor");

            assertTrue(permissions.contains("read"));
            assertTrue(permissions.contains("write"));
            assertTrue(permissions.contains("delete"));
            assertFalse(permissions.contains("admin"));
            assertFalse(permissions.contains("manage_users"));
        }

        @Test
        @DisplayName("viewer role should have only read permission")
        void inferPermissions_ViewerRole_HasReadOnly() {
            List<String> permissions = getPermissionsForRole("viewer");

            assertEquals(List.of("read"), permissions);
        }

        @Test
        @DisplayName("unknown role should default to read-only permission")
        void inferPermissions_UnknownRole_DefaultsToRead() {
            List<String> permissions = getPermissionsForRole("some-custom-role");

            assertEquals(List.of("read"), permissions);
        }
    }

    // =========================================================================
    // System Health / Info Tests
    // =========================================================================

    @Nested
    @DisplayName("getSystemHealth Tests")
    class SystemHealthTests {

        @Test
        @DisplayName("Should return 200 with status UP and keycloak UP when Keycloak is available")
        void getSystemHealth_KeycloakAvailable_ReturnsUp() {
            when(keycloakClient.isAvailable()).thenReturn(true);

            ResponseEntity<Map<String, Object>> response = adminController.getSystemHealth();

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals("UP", response.getBody().get("status"));
            assertEquals("UP", response.getBody().get("keycloak"));
            assertNotNull(response.getBody().get("timestamp"));
        }

        @Test
        @DisplayName("Should return keycloak DOWN when Keycloak is unavailable")
        void getSystemHealth_KeycloakUnavailable_ReturnsKeycloakDown() {
            when(keycloakClient.isAvailable()).thenReturn(false);

            ResponseEntity<Map<String, Object>> response = adminController.getSystemHealth();

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertEquals("DOWN", response.getBody().get("keycloak"));
            // Overall status is still UP (the service itself is running)
            assertEquals("UP", response.getBody().get("status"));
        }
    }

    @Nested
    @DisplayName("getSystemInfo Tests")
    class SystemInfoTests {

        @Test
        @DisplayName("Should return 200 with version and javaVersion fields")
        void getSystemInfo_ReturnsInfoMap() {
            ResponseEntity<Map<String, Object>> response = adminController.getSystemInfo();

            assertEquals(HttpStatus.OK, response.getStatusCode());
            assertNotNull(response.getBody());
            assertEquals("1.0.0", response.getBody().get("version"));
            assertNotNull(response.getBody().get("javaVersion"));
            assertNotNull(response.getBody().get("buildTime"));
            assertNotNull(response.getBody().get("environment"));
        }
    }
}
