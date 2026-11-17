package dev.noahheller.pidoku.pidokuapi.generation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * The nightly generator job. Runs only with the {@code generator} Spring profile, which also turns
 * off the web server (see application-generator.properties), so the application does its work and
 * exits. An exception fails startup and gives a non-zero exit code, which marks the Cloud Run Job
 * execution as failed.
 *
 * <p>Dailies come first: they're what players are waiting for, so if the job runs out of time
 * they're the part that's done. Then the random pool is topped up. Dailies are always generated
 * fresh, never taken from the pool (see {@link DailyPuzzleScheduler}).
 */
@Slf4j
@Component
@Profile(GeneratorJob.PROFILE)
public class GeneratorJob implements ApplicationRunner {

    public static final String PROFILE = "generator";

    private final PoolRefiller poolRefiller;
    private final DailyPuzzleScheduler dailyPuzzleScheduler;

    public GeneratorJob(PoolRefiller poolRefiller, DailyPuzzleScheduler dailyPuzzleScheduler) {
        this.poolRefiller = poolRefiller;
        this.dailyPuzzleScheduler = dailyPuzzleScheduler;
    }

    @Override
    public void run(ApplicationArguments args) {
        long start = System.nanoTime();
        int dailies = dailyPuzzleScheduler.scheduleUpcoming();
        int pooled = poolRefiller.refill();
        log.info("Generator job finished in {} s: {} puzzles added to the pool, {} daily slots filled",
                (System.nanoTime() - start) / 1_000_000_000, pooled, dailies);
    }
}
