package io.rdfforge.triplestore.controller;

import io.rdfforge.triplestore.service.TriplestoreService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TriplestoreController.class)
class TriplestoreControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TriplestoreService triplestoreService;

    @Test
    void testListConnections() throws Exception {
        mockMvc.perform(get("/api/v1/triplestores"))
                .andExpect(status().isOk());
    }
}
