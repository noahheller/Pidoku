package dev.noahheller.pidoku.pidokuapi;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Boots the full application against a throwaway Postgres (requires Docker),
 * so Flyway migrations and JPA schema validation run exactly as in production.
 */
@Testcontainers
@SpringBootTest(properties = {
        "DATABASE_URL=unused-overridden-by-service-connection",
        "DATABASE_USER=unused",
        "DATABASE_PASSWORD=unused"
})
class PidokuApiApplicationTests {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @Test
    void contextLoads() {
    }

}
