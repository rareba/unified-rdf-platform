package io.rdfforge.shacl.controller;

import io.rdfforge.engine.shacl.ShaclValidator;
import io.rdfforge.shacl.config.TestSecurityConfig;
import io.rdfforge.shacl.service.ShapeBuilderService;
import io.rdfforge.shacl.service.ShapeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ShapeController.class)
@Import(TestSecurityConfig.class)
class ShapeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ShapeService shapeService;

    @MockBean
    private ShapeBuilderService shapeBuilderService;

    // ShapeController also injects ShaclValidator for on-demand constraint
    // checks; @WebMvcTest does not scan engine beans, so provide a mock.
    @MockBean
    private ShaclValidator shaclValidator;

    @MockBean
    private io.rdfforge.shacl.service.ProfileValidationService profileValidationService;

    @Test
    void testListShapes() throws Exception {
        mockMvc.perform(get("/api/v1/shapes")
                        .header("X-User-Id", UUID.randomUUID().toString()))
                .andExpect(status().isOk());
    }
}
