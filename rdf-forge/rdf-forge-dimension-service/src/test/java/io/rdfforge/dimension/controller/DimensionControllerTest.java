package io.rdfforge.dimension.controller;

import io.rdfforge.dimension.service.DimensionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DimensionController.class)
class DimensionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private DimensionService dimensionService;

    @Test
    void testListDimensions() throws Exception {
        mockMvc.perform(get("/api/v1/dimensions"))
                .andExpect(status().isOk());
    }
}
