package dev.noahheller.pidoku.pidokuapi.config;

import dev.noahheller.pidoku.pidokuapi.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static dev.noahheller.pidoku.pidokuapi.support.TestTokens.player;
import static org.assertj.core.api.Assertions.assertThat;

@AutoConfigureMockMvc
class SecurityConfigTest extends IntegrationTest {

    @Autowired
    MockMvcTester mvc;

    @Test
    void healthCheckIsPublic() {
        assertThat(mvc.get().uri("/actuator/health")).hasStatusOk();
    }

    @Test
    void puzzleReadsArePublic() {
        // 503 because the pool is empty, but it got past security.
        assertThat(mvc.get().uri("/api/puzzles/random?difficulty=easy")).hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void puzzleReadsAlsoWorkSignedIn() {
        assertThat(mvc.get().uri("/api/puzzles/random?difficulty=easy").with(player("alice")))
                .hasStatus(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void aBadTokenIsRejectedEvenOnPublicEndpoints() {
        // Runs the real token check; a malformed token fails before Google's keys are needed.
        assertThat(mvc.get().uri("/api/puzzles/random?difficulty=easy").header("Authorization", "Bearer not-a-token"))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void playerEndpointsNeedAToken() {
        assertThat(mvc.get().uri("/api/players/me")).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mvc.post().uri("/api/attempts")).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mvc.get().uri("/api/players/me").with(player("alice"))).hasStatusOk();
    }

    // Everything not opened in SecurityConfig is denied: 401 without a token, 403 with one.

    @Test
    void writesToPuzzlesAreDenied() {
        assertThat(mvc.post().uri("/api/puzzles/random?difficulty=easy")).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mvc.post().uri("/api/puzzles/random?difficulty=easy").with(player("alice")))
                .hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void otherActuatorEndpointsAreDenied() {
        assertThat(mvc.get().uri("/actuator/env")).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mvc.get().uri("/actuator/env").with(player("alice"))).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void unknownPathsAreDenied() {
        assertThat(mvc.get().uri("/api/admin")).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mvc.get().uri("/api/admin").with(player("alice"))).hasStatus(HttpStatus.FORBIDDEN);
    }
}
