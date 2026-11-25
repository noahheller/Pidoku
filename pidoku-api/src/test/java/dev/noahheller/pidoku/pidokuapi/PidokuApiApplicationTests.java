package dev.noahheller.pidoku.pidokuapi;

import dev.noahheller.pidoku.pidokuapi.generation.GeneratorJob;
import dev.noahheller.pidoku.pidokuapi.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

class PidokuApiApplicationTests extends IntegrationTest {

    @Autowired
    ApplicationContext context;

    @Test
    void generatorJobOnlyRunsWithItsProfile() {
        assertThat(context.getBeanNamesForType(GeneratorJob.class)).isEmpty();
    }
}
