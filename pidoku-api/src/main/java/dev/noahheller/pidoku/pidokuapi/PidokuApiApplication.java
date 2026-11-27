package dev.noahheller.pidoku.pidokuapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

/**
 * The Pidoku API. Packages are split by feature:
 * <ul>
 *   <li>{@code puzzle}: stored puzzles, share codes and the puzzle endpoints</li>
 *   <li>{@code daily}: each date's daily puzzles</li>
 *   <li>{@code generation}: the engine, the generator job and the random pool</li>
 *   <li>{@code player}: players, their names and settings, signing in and merging accounts</li>
 *   <li>{@code attempt}: timed attempts, saved progress, hints and penalties, recent puzzles</li>
 *   <li>{@code leaderboard}, {@code streak}, {@code stats}: computed from attempts on each request</li>
 *   <li>{@code friend}, {@code profile}: friends, and what players can see of each other</li>
 *   <li>{@code supporter}: Ko-fi payments and supporter codes</li>
 *   <li>{@code event}: analytics events</li>
 *   <li>{@code config} (clock, security, database warm-up) and {@code web} (error responses)</li>
 * </ul>
 */
// Players sign in with Firebase, never with a username and password, so skip the in-memory user
// Spring Boot would otherwise create with a generated password.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class PidokuApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(PidokuApiApplication.class, args);
    }
}
