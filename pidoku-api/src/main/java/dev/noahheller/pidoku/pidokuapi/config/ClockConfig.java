package dev.noahheller.pidoku.pidokuapi.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    /**
     * UTC. Players get the daily puzzles of their own local date (the browser sends it), so the
     * server's zone doesn't decide when a day starts; the clock only limits which dates can be
     * asked for (see DailyPuzzleService) and where the generator job starts filling.
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
