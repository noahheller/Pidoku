package dev.noahheller.pidoku.pidokuapi.player;

import dev.noahheller.pidoku.pidokuapi.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static dev.noahheller.pidoku.pidokuapi.support.TestTokens.player;
import static org.assertj.core.api.Assertions.assertThat;

@AutoConfigureMockMvc
class PlayerControllerTest extends IntegrationTest {

    @Autowired
    MockMvcTester mvc;

    @Autowired
    PlayerRepository players;

    @Test
    void createsThePlayerOnTheirFirstRequest() {
        assertThat(mvc.get().uri("/api/players/me").with(player("alice")))
                .hasStatusOk()
                .bodyJson()
                .extractingPath("$.id").asNumber().isEqualTo(players.findByFirebaseUid("alice").orElseThrow().getId().intValue());
    }

    @Test
    void theSameTokenIsAlwaysTheSamePlayer() {
        assertThat(mvc.get().uri("/api/players/me").with(player("alice"))).hasStatusOk();
        assertThat(mvc.get().uri("/api/players/me").with(player("alice"))).hasStatusOk();
        assertThat(mvc.get().uri("/api/players/me").with(player("bob"))).hasStatusOk();

        assertThat(players.count()).isEqualTo(2);
    }

    @Test
    void newPlayersHaveNoDisplayName() {
        assertThat(mvc.get().uri("/api/players/me").with(player("alice")))
                .bodyJson()
                .extractingPath("$.displayName").isNull();
    }

    @Test
    void needsAToken() {
        assertThat(mvc.get().uri("/api/players/me")).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(players.count()).isZero();
    }
}
