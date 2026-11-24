package dev.noahheller.pidoku.pidokuapi.support;

import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/** Signs test requests in as a player, as if they carried a verified Firebase ID token. */
public final class TestTokens {

    private TestTokens() {
    }

    /** An anonymous player. Use as {@code mvc.get().uri(...).with(player("alice"))}. */
    public static RequestPostProcessor player(String firebaseUid) {
        return jwt().jwt(token -> token.subject(firebaseUid)
                .claim("firebase", Map.of("sign_in_provider", "anonymous")));
    }

    /** A player signed in with Google, whose Google account is named {@code name} (null for none). */
    public static RequestPostProcessor googlePlayer(String firebaseUid, String name) {
        return jwt().jwt(token -> {
            token.subject(firebaseUid).claim("firebase", Map.of("sign_in_provider", "google.com"));
            if (name != null) {
                token.claim("name", name);
            }
        });
    }
}
