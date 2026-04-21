package io.rdfforge.triplestore.controller;

import io.rdfforge.triplestore.config.TestSecurityConfig;
import io.rdfforge.triplestore.service.TriplestoreService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TriplestoreController.class)
@Import(TestSecurityConfig.class)
class TriplestoreControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TriplestoreService triplestoreService;

    @Test
    void testListConnections() throws Exception {
        mockMvc.perform(get("/api/v1/triplestores")
                        .header("X-User-Id", UUID.randomUUID().toString()))
                .andExpect(status().isOk());
    }
}
