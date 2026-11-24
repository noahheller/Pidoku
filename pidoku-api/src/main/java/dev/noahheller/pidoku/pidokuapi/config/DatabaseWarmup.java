package dev.noahheller.pidoku.pidokuapi.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * Opens the first database connection on a background thread while the rest of the application
 * starts, so a cold start wakes Neon (about a second when it's asleep) in parallel instead of on
 * the first request that needs it.
 *
 * <p>Only in the Cloud Run service (the {@code api} profile, see application-api.properties),
 * where nothing else touches the database during startup. Neon wakes on a cold start either way, as
 * it did when Flyway ran at startup; this only takes it off the critical path. A failure is logged
 * and otherwise ignored: the first real request will try again.
 */
@Slf4j
@Component
@Profile("api")
public class DatabaseWarmup {

    public DatabaseWarmup(DataSource dataSource) {
        Thread.ofVirtual().name("database-warmup").start(() -> {
            long start = System.nanoTime();
            try (Connection ignored = dataSource.getConnection()) {
                log.info("Database connection ready in {} ms", (System.nanoTime() - start) / 1_000_000);
            } catch (Exception e) {
                log.warn("Couldn't open a database connection during startup", e);
            }
        });
    }
}
