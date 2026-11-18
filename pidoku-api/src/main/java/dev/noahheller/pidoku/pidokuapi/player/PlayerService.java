package dev.noahheller.pidoku.pidokuapi.player;

import dev.noahheller.pidoku.pidokuapi.puzzle.ShareCodeGenerator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Maps Firebase ID tokens to players. The token has already been verified by Spring Security (see
 * SecurityConfig); its subject is the Firebase uid.
 */
@Service
public class PlayerService {

    /** Tries at drawing an unused friend code; see PuzzleService's share codes for the odds. */
    private static final int MAX_FRIEND_CODE_ATTEMPTS = 5;

    private final PlayerRepository players;
    private final ShareCodeGenerator codes;

    public PlayerService(PlayerRepository players, ShareCodeGenerator codes) {
        this.players = players;
        this.codes = codes;
    }

    /** The player a token belongs to, created on their first request. */
    @Transactional
    public Player forToken(Jwt token) {
        String uid = token.getSubject();
        return players.findByFirebaseUid(uid).orElseGet(() -> {
            players.insertIfAbsent(uid);
            return players.findByFirebaseUid(uid).orElseThrow();
        });
    }

    /**
     * The player a token belongs to, as they see themselves: created if needed, and given a friend
     * code if they don't have one yet.
     */
    @Transactional
    public Player me(Jwt token) {
        Player player = forToken(token);
        if (player.getFriendCode() != null) {
            return player;
        }
        players.assignFriendCode(player.getId(), unusedFriendCode());
        // Assigned now or by a request that got there first; either way it's set.
        return players.findById(player.getId()).orElseThrow();
    }

    // Like share codes: checking first keeps the common case free of constraint violations, and
    // the unique constraint catches two players drawing the same code at once.
    private String unusedFriendCode() {
        for (int attempt = 0; attempt < MAX_FRIEND_CODE_ATTEMPTS; attempt++) {
            String code = codes.next();
            if (!players.existsByFriendCode(code)) {
                return code;
            }
        }
        throw new IllegalStateException("No unused friend code found in " + MAX_FRIEND_CODE_ATTEMPTS + " attempts");
    }

    /**
     * Sets the display name of the player a token belongs to, creating the player if needed.
     *
     * @throws InvalidDisplayNameException if the name isn't allowed (see {@link DisplayName})
     */
    @Transactional
    public Player rename(Jwt token, String displayName) {
        DisplayName name = DisplayName.of(displayName);
        Player player = me(token);
        player.rename(name);
        return player;
    }

    /**
     * The player a token belongs to, if there is a token and its player exists. For endpoints
     * that work without sign-in and shouldn't create players.
     */
    @Transactional(readOnly = true)
    public Optional<Player> find(Jwt tokenOrNull) {
        return Optional.ofNullable(tokenOrNull).flatMap(token -> players.findByFirebaseUid(token.getSubject()));
    }
}
