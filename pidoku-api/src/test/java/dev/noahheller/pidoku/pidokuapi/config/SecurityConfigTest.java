package dev.noahheller.pidoku.pidokuapi.config;

import dev.noahheller.pidoku.pidokuapi.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

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
    void writesToPuzzlesAreDenied() {
        assertThat(mvc.post().uri("/api/puzzles/random?difficulty=easy")).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void otherActuatorEndpointsAreDenied() {
        assertThat(mvc.get().uri("/actuator/env")).hasStatus(HttpStatus.FORBIDDEN);
    }

    @Test
    void unknownPathsAreDenied() {
        assertThat(mvc.get().uri("/api/admin")).hasStatus(HttpStatus.FORBIDDEN);
    }
}
