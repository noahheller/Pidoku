package dev.noahheller.pidoku.pidokuapi.supporter;

import dev.noahheller.pidoku.pidokuapi.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import java.time.Duration;
import java.time.Instant;

import static dev.noahheller.pidoku.pidokuapi.support.TestTokens.player;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@AutoConfigureMockMvc
class SupporterControllerTest extends IntegrationTest {

    @Autowired
    MockMvcTester mvc;

    @Test
    void givesACodeThatLastsThirtyDays() {
        assertThat(mvc.post().uri("/api/players/me/supporter-code").with(player("alice")))
                .hasStatusOk()
                .bodyJson().satisfies(json -> {
                    assertThat(json).extractingPath("$.code").asString().matches("PI-[2-9A-HJKMNP-Z]{8}");
                    assertThat(json).extractingPath("$.expiresAt").asString()
                            .satisfies(at -> assertThat(Instant.parse(at))
                                    .isCloseTo(Instant.now().plus(Duration.ofDays(30)), within(Duration.ofMinutes(1))));
                });
    }

    @Test
    void aNewCodeReplacesTheOld() {
        mvc.post().uri("/api/players/me/supporter-code").with(player("alice")).exchange();
        mvc.post().uri("/api/players/me/supporter-code").with(player("alice")).exchange();
        mvc.post().uri("/api/players/me/supporter-code").with(player("bob")).exchange();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM supporter_codes", Integer.class)).isEqualTo(2);
    }

    @Test
    void newPlayersArentSupporters() {
        assertThat(mvc.get().uri("/api/players/me").with(player("alice")))
                .bodyJson().extractingPath("$.supporterSince").isNull();
    }

    @Test
    void needsSignIn() {
        assertThat(mvc.post().uri("/api/players/me/supporter-code")).hasStatus(HttpStatus.UNAUTHORIZED);
    }
}
