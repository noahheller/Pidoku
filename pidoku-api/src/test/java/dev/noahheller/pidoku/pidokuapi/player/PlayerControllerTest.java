package dev.noahheller.pidoku.pidokuapi.player;

import dev.noahheller.pidoku.pidokuapi.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

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
    void everyPlayerGetsAFriendCodeThatNeverChanges() {
        assertThat(mvc.get().uri("/api/players/me").with(player("alice")))
                .bodyJson().extractingPath("$.friendCode").asString().matches("[2-9A-HJKMNP-Z]{8}");
        String code = players.findByFirebaseUid("alice").orElseThrow().getFriendCode();

        assertThat(mvc.get().uri("/api/players/me").with(player("alice")))
                .bodyJson().extractingPath("$.friendCode").isEqualTo(code);
        assertThat(mvc.get().uri("/api/players/me").with(player("bob")))
                .bodyJson().extractingPath("$.friendCode").isNotEqualTo(code);
    }

    private MvcTestResult rename(String uid, String json) {
        return mvc.patch().uri("/api/players/me").with(player(uid))
                .contentType(MediaType.APPLICATION_JSON).content(json).exchange();
    }

    @Test
    void setsTheDisplayNameTrimmed() {
        assertThat(rename("alice", "{\"displayName\": \"  Alice   in  Wonderland \"}"))
                .hasStatusOk()
                .bodyJson().extractingPath("$.displayName").isEqualTo("Alice in Wonderland");

        assertThat(players.findByFirebaseUid("alice").orElseThrow().getDisplayName()).isEqualTo("Alice in Wonderland");
        assertThat(mvc.get().uri("/api/players/me").with(player("alice")))
                .bodyJson().extractingPath("$.displayName").isEqualTo("Alice in Wonderland");
    }

    @Test
    void theDisplayNameCanBeChanged() {
        rename("alice", "{\"displayName\": \"Alice\"}");

        assertThat(rename("alice", "{\"displayName\": \"Al\"}")).hasStatusOk();
        assertThat(players.findByFirebaseUid("alice").orElseThrow().getDisplayName()).isEqualTo("Al");
    }

    @Test
    void aDisplayNameCanBeFortyCharactersEvenWithEmoji() {
        String name = "🥧".repeat(40);

        assertThat(rename("alice", "{\"displayName\": \"%s\"}".formatted(name))).hasStatusOk();
        assertThat(players.findByFirebaseUid("alice").orElseThrow().getDisplayName()).isEqualTo(name);
    }

    @Test
    void refusesNamesThatAreEmptyTooLongOrMissing() {
        assertThat(rename("alice", "{\"displayName\": \"   \"}")).hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.detail").asString().contains("Pick a display name");
        assertThat(rename("alice", "{\"displayName\": \"%s\"}".formatted("x".repeat(41))))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.detail").asString().contains("40 characters");
        assertThat(rename("alice", "{}")).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(rename("alice", "{\"displayName\": \"a\\u0000b\"}")).hasStatus(HttpStatus.BAD_REQUEST);

        assertThat(players.findByFirebaseUid("alice").map(Player::getDisplayName)).isEmpty();
    }

    @Test
    void renamingNeedsAToken() {
        assertThat(mvc.patch().uri("/api/players/me")
                .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\": \"Alice\"}"))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void needsAToken() {
        assertThat(mvc.get().uri("/api/players/me")).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(players.count()).isZero();
    }
}
