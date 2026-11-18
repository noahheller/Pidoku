package dev.noahheller.pidoku.pidokuapi.friend;

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
class FriendControllerTest extends IntegrationTest {

    @Autowired
    MockMvcTester mvc;

    /** The player's friend code, assigned by {@code GET /api/players/me}. */
    private String codeOf(String uid) {
        assertThat(mvc.get().uri("/api/players/me").with(player(uid))).hasStatusOk();
        return jdbc.queryForObject("SELECT friend_code FROM players WHERE firebase_uid = ?", String.class, uid);
    }

    private void name(String uid, String name) {
        mvc.patch().uri("/api/players/me").with(player(uid))
                .contentType(MediaType.APPLICATION_JSON).content("{\"displayName\": \"%s\"}".formatted(name)).exchange();
    }

    private MvcTestResult add(String uid, String code) {
        return mvc.post().uri("/api/friends").with(player(uid))
                .contentType(MediaType.APPLICATION_JSON).content("{\"friendCode\": \"%s\"}".formatted(code)).exchange();
    }

    private MvcTestResult friends(String uid) {
        return mvc.get().uri("/api/friends").with(player(uid)).exchange();
    }

    private long idOf(String uid) {
        return jdbc.queryForObject("SELECT id FROM players WHERE firebase_uid = ?", Long.class, uid);
    }

    @Test
    void addingByCodeMakesBothPlayersFriends() {
        name("bob", "Bob");
        name("alice", "Alice");

        assertThat(add("alice", codeOf("bob"))).hasStatusOk().bodyJson().isStrictlyEqualTo("""
                {"id": %d, "displayName": "Bob"}
                """.formatted(idOf("bob")));

        assertThat(friends("alice")).hasStatusOk().bodyJson().isStrictlyEqualTo("""
                [{"id": %d, "displayName": "Bob"}]
                """.formatted(idOf("bob")));
        assertThat(friends("bob")).hasStatusOk().bodyJson().isStrictlyEqualTo("""
                [{"id": %d, "displayName": "Alice"}]
                """.formatted(idOf("alice")));
    }

    @Test
    void codesIgnoreCaseAndSurroundingSpaces() {
        assertThat(add("alice", "  " + codeOf("bob").toLowerCase() + " ")).hasStatusOk();
    }

    @Test
    void addingAFriendTwiceChangesNothing() {
        String bob = codeOf("bob");
        add("alice", bob);

        assertThat(add("alice", bob)).hasStatusOk();
        assertThat(add("bob", codeOf("alice"))).hasStatusOk();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM friendships", Integer.class)).isEqualTo(2);
    }

    @Test
    void friendsAreListedByName() {
        name("bob", "bob");
        name("carol", "Carol");
        name("dave", "Ann");
        codeOf("nameless");
        for (String friend : new String[]{"carol", "nameless", "bob", "dave"}) {
            add("alice", codeOf(friend));
        }

        assertThat(friends("alice")).bodyJson().extractingPath("$[*].displayName")
                .isEqualTo(java.util.Arrays.asList("Ann", "bob", "Carol", null));
    }

    @Test
    void removingAFriendRemovesItForBoth() {
        add("alice", codeOf("bob"));

        assertThat(mvc.delete().uri("/api/friends/{id}", idOf("bob")).with(player("alice")))
                .hasStatus(HttpStatus.NO_CONTENT);

        assertThat(friends("alice")).bodyJson().isStrictlyEqualTo("[]");
        assertThat(friends("bob")).bodyJson().isStrictlyEqualTo("[]");
    }

    @Test
    void removingSomeoneWhoIsntAFriendChangesNothing() {
        add("alice", codeOf("bob"));
        codeOf("carol");

        assertThat(mvc.delete().uri("/api/friends/{id}", idOf("carol")).with(player("bob")))
                .hasStatus(HttpStatus.NO_CONTENT);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM friendships", Integer.class)).isEqualTo(2);
    }

    @Test
    void anUnknownCodeIsNotFound() {
        assertThat(add("alice", "ZZZZZZZZ"))
                .hasStatus(HttpStatus.NOT_FOUND)
                .bodyJson().extractingPath("$.detail").asString().contains("ZZZZZZZZ");
    }

    @Test
    void yourOwnCodeIsABadRequest() {
        assertThat(add("alice", codeOf("alice")))
                .hasStatus(HttpStatus.BAD_REQUEST)
                .bodyJson().extractingPath("$.detail").asString().contains("your own");
    }

    @Test
    void needsAToken() {
        assertThat(mvc.get().uri("/api/friends")).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mvc.post().uri("/api/friends").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }
}
