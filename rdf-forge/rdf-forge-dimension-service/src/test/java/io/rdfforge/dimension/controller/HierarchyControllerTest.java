package io.rdfforge.dimension.controller;

import io.rdfforge.common.exception.GlobalExceptionHandler;
import io.rdfforge.dimension.config.TestSecurityConfig;
import io.rdfforge.dimension.service.HierarchyService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HierarchyController.class)
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class})
class HierarchyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private HierarchyService hierarchyService;

    @Test
    void testListHierarchies() throws Exception {
        // Expecting 400 because dimensionId param is required
        mockMvc.perform(get("/api/v1/hierarchies")
                        .header("X-User-Id", UUID.randomUUID().toString()))
                .andExpect(status().isBadRequest());
    }
}
