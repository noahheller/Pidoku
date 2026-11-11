package dev.noahheller.pidoku.pidokuapi.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    /** UTC, because the daily puzzle changes at 00:00 UTC for everyone (see docs/ROADMAP.md). */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
