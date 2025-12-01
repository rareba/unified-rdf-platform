package io.rdfforge.gateway;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class GatewayRoutingIT {

    @Autowired
    private WebTestClient webClient;

    @Test
    void testPublicEndpoint() {
        webClient.get()
                .uri("/api/v1/public/info")
                .exchange()
                .expectStatus().isOk();
    }
}
