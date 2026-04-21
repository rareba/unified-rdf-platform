package io.rdfforge.pipeline;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Use scanBasePackages (instead of @ComponentScan) so Spring Boot's
 * test-slice TypeExcludeFilter is still applied — @WebMvcTest(Foo.class)
 * would otherwise create every @RestController in io.rdfforge.pipeline
 * and fail the slice context with missing JPA repositories
 * (commentRepository, mappingRepository, …).
 */
@SpringBootApplication(scanBasePackages = {"io.rdfforge.pipeline", "io.rdfforge.engine"})
public class PipelineServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(PipelineServiceApplication.class, args);
    }
}
