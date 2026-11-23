package dev.noahheller.pidoku.pidokuapi.config;

import dev.noahheller.pidoku.pidokuapi.support.TestcontainersConfiguration;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Starts the application with the Cloud Run service's {@code api} profile (which the service gets
 * through Spring AOT, see application-api.properties): it has to start without migrating or
 * checking the schema, so Hibernate must pick its dialect without asking the database.
 */
@SpringBootTest
@ActiveProfiles("api")
@Import(TestcontainersConfiguration.class)
class ApiProfileTest {

    @Autowired
    ApplicationContext context;

    @Test
    void startsWithoutMigratingAndWarmsUpTheDatabase() {
        assertThat(context.getBeanNamesForType(Flyway.class)).isEmpty();
        assertThat(context.getBean(DatabaseWarmup.class)).isNotNull();
    }
}
