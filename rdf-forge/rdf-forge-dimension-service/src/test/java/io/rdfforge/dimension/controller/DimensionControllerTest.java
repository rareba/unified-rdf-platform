package io.rdfforge.dimension.controller;

import io.rdfforge.dimension.config.TestSecurityConfig;
import io.rdfforge.dimension.service.DimensionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DimensionController.class)
@Import(TestSecurityConfig.class)
class DimensionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DimensionService dimensionService;

    @Test
    void testListDimensions() throws Exception {
        mockMvc.perform(get("/api/v1/dimensions")
                        .param("projectId", UUID.randomUUID().toString())
                        .header("X-User-Id", UUID.randomUUID().toString()))
                .andExpect(status().isOk());
    }
}
