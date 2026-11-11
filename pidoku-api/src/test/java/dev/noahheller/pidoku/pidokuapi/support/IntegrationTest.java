package dev.noahheller.pidoku.pidokuapi.support;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Base class for tests that boot the whole application against a real Postgres, so Flyway
 * migrations and Hibernate's schema validation run exactly as in production. Every test starts
 * with empty tables.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTest {

    @Autowired
    protected JdbcTemplate jdbc;

    @BeforeEach
    void emptyTables() {
        jdbc.execute("TRUNCATE daily_puzzles, puzzles RESTART IDENTITY CASCADE");
    }
}
