package io.rdfforge.pipeline;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"io.rdfforge.pipeline", "io.rdfforge.engine"})
public class PipelineServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(PipelineServiceApplication.class, args);
    }
}
