package dev.noahheller.pidoku.pidokuapi;

import dev.noahheller.pidoku.pidokuapi.generation.GeneratorJob;
import dev.noahheller.pidoku.pidokuapi.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/** Flyway migrates, Hibernate validates the entities against the schema, and the context starts. */
class PidokuApiApplicationTests extends IntegrationTest {

    @Autowired
    ApplicationContext context;

    @Test
    void contextLoads() {
    }

    @Test
    void generatorJobOnlyRunsWithItsProfile() {
        assertThat(context.getBeanNamesForType(GeneratorJob.class)).isEmpty();
    }
}
