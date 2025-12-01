package io.rdfforge.dimension;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class DimensionServiceApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(DimensionServiceApplication.class, args);
    }
}
