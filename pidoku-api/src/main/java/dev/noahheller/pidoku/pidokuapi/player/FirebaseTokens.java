package dev.noahheller.pidoku.pidokuapi.player;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Map;
import java.util.Optional;

/** What a verified Firebase ID token says about how the player signed in. */
public final class FirebaseTokens {

    private FirebaseTokens() {
    }

    /** Whether the token is from an account signed in with Google, rather than an anonymous one. */
    public static boolean isGoogle(Jwt token) {
        return token != null && "google.com".equals(signInProvider(token));
    }

    /** Whether the token is from an anonymous account, one not linked to Google. */
    public static boolean isAnonymous(Jwt token) {
        return token != null && "anonymous".equals(signInProvider(token));
    }

    /** The Google account's name ({@code name} claim), for a Google sign-in that has one. */
    public static Optional<String> googleName(Jwt token) {
        return isGoogle(token) ? Optional.ofNullable(token.getClaimAsString("name")).filter(n -> !n.isBlank()) : Optional.empty();
    }

    // Firebase puts it in {"firebase": {"sign_in_provider": "google.com", ...}}.
    private static String signInProvider(Jwt token) {
        Object firebase = token.getClaims().get("firebase");
        return firebase instanceof Map<?, ?> map && map.get("sign_in_provider") instanceof String provider ? provider : null;
    }
}
