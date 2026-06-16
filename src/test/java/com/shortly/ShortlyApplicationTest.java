package com.shortly;

import com.shortly.model.Link;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end integration test verifying URL shortening and redirect flow
 * against real PostgreSQL and Redis containers.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class ShortlyApplicationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("shortly_test")
            .withUsername("test")
            .withPassword("test");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        String pgHost = postgres.getHost();
        int pgPort = postgres.getMappedPort(5432);

        registry.add("spring.r2dbc.url", () ->
                "r2dbc:postgresql://" + pgHost + ":" + pgPort + "/shortly_test");
        registry.add("spring.r2dbc.username", postgres::getUsername);
        registry.add("spring.r2dbc.password", postgres::getPassword);
        registry.add("spring.flyway.url", () ->
                "jdbc:postgresql://" + pgHost + ":" + pgPort + "/shortly_test");
        registry.add("spring.flyway.user", postgres::getUsername);
        registry.add("spring.flyway.password", postgres::getPassword);

        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379).toString());
    }

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private R2dbcEntityTemplate template;

    @Test
    void shouldCreateShortLinkAndRedirect() {
        // Create a short link
        var response = webTestClient.post()
                .uri("/api/v1/links")
                .header("Content-Type", "application/json")
                .bodyValue("""
                        {"url":"https://example.com/hello","ttl":"1d","redirectType":"302"}
                        """)
                .exchange()
                .expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.shortCode").isNotEmpty()
                .jsonPath("$.shortUrl").isNotEmpty()
                .jsonPath("$.originalUrl").isEqualTo("https://example.com/hello")
                .jsonPath("$.redirectType").isEqualTo("302")
                .returnResult();

        // Extract short code from response body
        byte[] body = response.getResponseBody();
        String bodyStr = new String(body);
        String shortCode = bodyStr.replaceAll(".*\"shortCode\":\"([^\"]+)\".*", "$1");

        // Resolve it — should redirect
        webTestClient.get()
                .uri("/" + shortCode)
                .exchange()
                .expectStatus().isFound()
                .expectHeader().valueEquals("Location", "https://example.com/hello");
    }

    @Test
    void shouldReturn404ForUnknownCode() {
        webTestClient.get()
                .uri("/nonexistent123")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.error").isEqualTo("not_found");
    }

    @Test
    void shouldReturnHealth() {
        webTestClient.get()
                .uri("/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isNotEmpty();
    }

    @Test
    void shouldRejectInvalidUrl() {
        webTestClient.post()
                .uri("/api/v1/links")
                .header("Content-Type", "application/json")
                .bodyValue("""
                        {"url":"not-a-valid-url"}
                        """)
                .exchange()
                .expectStatus().isBadRequest();
    }

    @Test
    void shouldPersistLinkToDatabase() {
        // Verify we can directly persist via R2DBC
        Link link = Link.builder()
                .shortCode("dbtest1")
                .originalUrl("https://db-test.example.com")
                .redirectType("301")
                .expiresAt(Instant.now().plus(1, ChronoUnit.DAYS))
                .createdAt(Instant.now())
                .build();

        Link saved = template.insert(Link.class)
                .using(link)
                .block();

        assertThat(saved).isNotNull();
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getShortCode()).isEqualTo("dbtest1");
    }
}
