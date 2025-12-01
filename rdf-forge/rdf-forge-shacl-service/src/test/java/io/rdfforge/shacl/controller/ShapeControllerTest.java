package io.rdfforge.shacl.controller;

import io.rdfforge.shacl.service.ShapeService;
import io.rdfforge.shacl.service.ShapeBuilderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ShapeController.class)
class ShapeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ShapeService shapeService;
    
    @MockBean
    private ShapeBuilderService shapeBuilderService;

    @Test
    void testListShapes() throws Exception {
        mockMvc.perform(get("/api/v1/shapes"))
                .andExpect(status().isOk());
    }
}
