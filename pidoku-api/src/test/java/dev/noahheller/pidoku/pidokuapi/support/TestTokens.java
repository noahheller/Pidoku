package dev.noahheller.pidoku.pidokuapi.support;

import org.springframework.test.web.servlet.request.RequestPostProcessor;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

/** Signs test requests in as a player, as if they carried a verified Firebase ID token. */
public final class TestTokens {

    private TestTokens() {
    }

    /** Use as {@code mvc.get().uri(...).with(player("alice"))}. */
    public static RequestPostProcessor player(String firebaseUid) {
        return jwt().jwt(token -> token.subject(firebaseUid));
    }
}
