package io.rdfforge.job;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.batch.BatchAutoConfiguration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

// scanBasePackages keeps the @WebMvcTest TypeExcludeFilter active.
// @ComponentScan would bypass the slice filter and force every
// @RestController to be instantiated in the test context.
// The RestTemplate bean lives in config.HttpClientConfig so @WebMvcTest
// slices don't try to instantiate it without RestTemplateAutoConfiguration.
@SpringBootApplication(
    exclude = {BatchAutoConfiguration.class},
    scanBasePackages = {"io.rdfforge.job", "io.rdfforge.engine"}
)
@EnableAsync
@EnableScheduling
public class JobServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(JobServiceApplication.class, args);
    }
}
