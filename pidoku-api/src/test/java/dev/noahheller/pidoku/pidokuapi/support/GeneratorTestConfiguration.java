package dev.noahheller.pidoku.pidokuapi.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * For generator job tests: a {@link FakePuzzleGenerator} instead of the engine, and a clock fixed
 * at {@link #TODAY} so the dates of daily slots are predictable.
 */
@TestConfiguration(proxyBeanMethods = false)
public class GeneratorTestConfiguration {

    public static final LocalDate TODAY = LocalDate.of(2026, 11, 9);

    @Bean
    @Primary
    FakePuzzleGenerator fakePuzzleGenerator() {
        return new FakePuzzleGenerator();
    }

    @Bean
    @Primary
    Clock fixedClock() {
        Instant noon = TODAY.atTime(12, 0).toInstant(ZoneOffset.UTC);
        return Clock.fixed(noon, ZoneOffset.UTC);
    }
}
