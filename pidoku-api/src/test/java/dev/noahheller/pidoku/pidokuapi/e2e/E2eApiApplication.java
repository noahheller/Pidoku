package dev.noahheller.pidoku.pidokuapi.e2e;

import dev.noahheller.pidoku.pidokuapi.PidokuApiApplication;
import dev.noahheller.pidoku.pidokuapi.support.TestcontainersConfiguration;
import org.springframework.boot.SpringApplication;

/**
 * The API for the frontend's Playwright tests ({@code npm run e2e} in pidoku-frontend): a
 * throwaway Testcontainers Postgres, today's dailies generated on startup, and test tokens instead
 * of Firebase (see {@link E2eConfiguration}). Started with {@code ./gradlew :pidoku-api:bootTestRun},
 * which only exists on the test classpath, so none of this can reach the production image.
 */
public class E2eApiApplication {

    public static void main(String[] args) {
        SpringApplication.from(PidokuApiApplication::main)
                .with(TestcontainersConfiguration.class, E2eConfiguration.class)
                .withAdditionalProfiles(E2eConfiguration.PROFILE)
                .run(args);
    }
}
