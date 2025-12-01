package io.rdfforge.shacl;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"io.rdfforge.shacl", "io.rdfforge.engine"})
public class ShaclServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ShaclServiceApplication.class, args);
    }
}
