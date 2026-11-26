package dev.noahheller.pidoku.pidokuapi.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Who may call what. Spring Security denies everything by default, so every endpoint has to be
 * opened here.
 *
 * <p>Players are Firebase accounts (the frontend signs every visitor in anonymously) and send
 * their Firebase ID token as {@code Authorization: Bearer <token>}. The token is a JWT signed by
 * Google, verified here as an OAuth2 resource server; what makes a token valid is configured under
 * {@code spring.security.oauth2.resourceserver.jwt} in application.properties. Controllers get the
 * verified token with {@code @AuthenticationPrincipal Jwt} and turn it into a player with
 * PlayerService.
 *
 * <p>The puzzle read endpoints (leaderboards included), the health check and Ko-fi's webhook are
 * public (the webhook checks Ko-fi's own verification token instead, see KofiWebhookController);
 * attempts, players, friends and analytics events need a token, and everything else is denied. A request with a bad token is a 401 even on a public
 * endpoint.
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
                        .requestMatchers(HttpMethod.POST, "/api/kofi/webhook").permitAll()
                        // Where Spring Boot renders errors, so a failed public request gets its real
                        // status instead of a 403 from the error page.
                        .requestMatchers("/error").permitAll()
                        .requestMatchers("/api/attempts/**", "/api/players/**", "/api/friends", "/api/friends/**", "/api/events").authenticated()
                        .anyRequest().denyAll())
                // A stateless JSON API with no cookies or sessions, so there's nothing for CSRF to steal.
                .oauth2ResourceServer(resourceServer -> resourceServer.jwt(Customizer.withDefaults()))
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(sessions -> sessions.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .build();
    }
}
