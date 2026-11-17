package dev.noahheller.pidoku.pidokuapi.player;

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

    private final PlayerRepository players;

    public PlayerService(PlayerRepository players) {
        this.players = players;
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
     * Sets the display name of the player a token belongs to, creating the player if needed.
     *
     * @throws InvalidDisplayNameException if the name isn't allowed (see {@link DisplayName})
     */
    @Transactional
    public Player rename(Jwt token, String displayName) {
        DisplayName name = DisplayName.of(displayName);
        Player player = forToken(token);
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
