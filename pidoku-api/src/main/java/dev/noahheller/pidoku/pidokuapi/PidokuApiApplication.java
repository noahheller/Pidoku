package dev.noahheller.pidoku.pidokuapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

/**
 * The Pidoku API. Packages are split by feature: {@code puzzle} (stored puzzles and the read
 * endpoints), {@code daily} (daily puzzles), {@code generation} (the engine and the generator job),
 * plus {@code config} (beans and security) and {@code web} (error responses). Feature packages
 * depend one way: generation → daily → puzzle.
 */
// No username/password logins (players will sign in with Firebase), so skip the default in-memory
// user Spring Boot would otherwise create with a generated password.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class PidokuApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(PidokuApiApplication.class, args);
    }

}
