package io.rdfforge.shacl;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// scanBasePackages keeps the @WebMvcTest TypeExcludeFilter active.
@SpringBootApplication(scanBasePackages = {"io.rdfforge.shacl", "io.rdfforge.engine"})
public class ShaclServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(ShaclServiceApplication.class, args);
    }
}
