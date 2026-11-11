package dev.noahheller.pidoku.pidokuapi.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Who may call what. Spring Security denies everything by default, so every endpoint has to be
 * opened here.
 *
 * <p>For now there are no accounts: the puzzle read endpoints and the health check are public and
 * everything else is denied. Milestone 3 (anonymous Firebase sign-in) adds token checks for the
 * endpoints that need a player.
 *
 * <p>Only loaded in a web application: the generator job runs without a web server, where there is
 * nothing to secure (and no {@link HttpSecurity} to build a filter chain from).
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) {
        return http
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.GET, "/api/puzzles/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
                        // Where Spring Boot renders errors, so a failed public request gets its real
                        // status instead of a 403 from the error page.
                        .requestMatchers("/error").permitAll()
                        .anyRequest().denyAll())
                // A stateless JSON API with no cookies or sessions, so there's nothing for CSRF to steal.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sessions -> sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .build();
    }
}
