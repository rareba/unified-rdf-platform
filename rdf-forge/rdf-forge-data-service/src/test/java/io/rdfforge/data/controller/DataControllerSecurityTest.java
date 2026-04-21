package io.rdfforge.data.controller;

import io.rdfforge.common.exception.GlobalExceptionHandler;
import io.rdfforge.data.config.TestSecurityConfig;
import io.rdfforge.data.entity.DataSourceEntity;
import io.rdfforge.data.entity.DataSourceEntity.DataFormat;
import io.rdfforge.data.format.DataFormatRegistry;
import io.rdfforge.data.service.DataService;
import io.rdfforge.data.service.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Object-level authorization tests for DataController.
 *
 * <p>Exercises the ownership check added 2026-04-21:
 * <ul>
 *   <li>unauthenticated requests to gated endpoints return 401</li>
 *   <li>User A cannot read/delete a DataSource owned by User B (403)</li>
 *   <li>User A can read/delete a DataSource they own (200/204)</li>
 *   <li>Admin can read/delete regardless of ownership</li>
 * </ul>
 */
@WebMvcTest(DataController.class)
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class})
@DisplayName("DataController Security Tests")
class DataControllerSecurityTest {

    private static final UUID OWNER_ID = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID OTHER_USER_ID = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID ADMIN_ID = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DataService dataService;

    @MockBean
    private DataFormatRegistry formatRegistry;

    @MockBean
    private FileStorageService fileStorageService;

    private UUID dataSourceId;
    private DataSourceEntity entity;

    @BeforeEach
    void setUp() {
        dataSourceId = UUID.randomUUID();
        entity = new DataSourceEntity();
        entity.setId(dataSourceId);
        entity.setName("Owner's data");
        entity.setOriginalFilename("o.csv");
        entity.setFormat(DataFormat.CSV);
        entity.setSizeBytes(100L);
        entity.setStoragePath("/x");
        entity.setUploadedBy(OWNER_ID);
        when(dataService.getDataSource(dataSourceId)).thenReturn(Optional.of(entity));
    }

    @Test
    @DisplayName("GET /{id} without X-User-Id → 401")
    void getDataSource_Unauthenticated_Returns401() throws Exception {
        mockMvc.perform(get("/api/v1/data/{id}", dataSourceId))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /{id} as non-owner → 403")
    void getDataSource_NonOwner_Returns403() throws Exception {
        mockMvc.perform(get("/api/v1/data/{id}", dataSourceId)
                .header("X-User-Id", OTHER_USER_ID.toString()))
            .andExpect(status().isForbidden());
        verify(dataService, never()).previewDataSource(any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("GET /{id} as owner → 200")
    void getDataSource_Owner_Returns200() throws Exception {
        mockMvc.perform(get("/api/v1/data/{id}", dataSourceId)
                .header("X-User-Id", OWNER_ID.toString()))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /{id} as admin (non-owner) → 200")
    void getDataSource_Admin_Returns200() throws Exception {
        mockMvc.perform(get("/api/v1/data/{id}", dataSourceId)
                .header("X-User-Id", ADMIN_ID.toString())
                .header("X-User-Roles", "USER,ADMIN"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("DELETE /{id} as non-owner → 403 and service.delete not called")
    void deleteDataSource_NonOwner_Returns403() throws Exception {
        mockMvc.perform(delete("/api/v1/data/{id}", dataSourceId)
                .header("X-User-Id", OTHER_USER_ID.toString()))
            .andExpect(status().isForbidden());
        verify(dataService, never()).deleteDataSource(any());
    }

    @Test
    @DisplayName("DELETE /{id} as owner → 204")
    void deleteDataSource_Owner_Returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/data/{id}", dataSourceId)
                .header("X-User-Id", OWNER_ID.toString()))
            .andExpect(status().isNoContent());
        verify(dataService).deleteDataSource(dataSourceId);
    }

    @Test
    @DisplayName("DELETE /{id} as admin → 204")
    void deleteDataSource_Admin_Returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/data/{id}", dataSourceId)
                .header("X-User-Id", ADMIN_ID.toString())
                .header("X-User-Roles", "ADMIN"))
            .andExpect(status().isNoContent());
        verify(dataService).deleteDataSource(dataSourceId);
    }

    @Test
    @DisplayName("Legacy record with null uploadedBy is only touchable by admin")
    void legacyUnownedRecord_OnlyAdminCanRead() throws Exception {
        entity.setUploadedBy(null);
        mockMvc.perform(get("/api/v1/data/{id}", dataSourceId)
                .header("X-User-Id", OWNER_ID.toString()))
            .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/data/{id}", dataSourceId)
                .header("X-User-Id", ADMIN_ID.toString())
                .header("X-User-Roles", "ADMIN"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Public formats endpoint remains accessible without X-User-Id")
    void formatsEndpoint_Unauthenticated_Returns200() throws Exception {
        // /api/v1/data/formats is an intentionally-public catalog endpoint
        // (no user data leaked). Authz was not applied.
        when(formatRegistry.getAvailableFormats()).thenReturn(java.util.List.of());
        mockMvc.perform(get("/api/v1/data/formats"))
            .andExpect(status().isOk());
    }
}
