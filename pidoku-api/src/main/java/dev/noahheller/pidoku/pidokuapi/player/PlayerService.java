package dev.noahheller.pidoku.pidokuapi.player;

import dev.noahheller.pidoku.pidokuapi.puzzle.ShareCodeGenerator;
import org.springframework.jdbc.core.simple.JdbcClient;
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
    private final JdbcClient jdbc;

    public PlayerService(PlayerRepository players, ShareCodeGenerator codes, JdbcClient jdbc) {
        this.players = players;
        this.codes = codes;
        this.jdbc = jdbc;
    }

    /**
     * The player a token belongs to, created on their first request. A player signed in with
     * Google who hasn't picked a display name gets their Google account's name (see
     * {@link #nameFromGoogle}).
     */
    @Transactional
    public Player forToken(Jwt token) {
        String uid = token.getSubject();
        Player player = players.findByFirebaseUid(uid).orElseGet(() -> {
            players.insertIfAbsent(uid);
            return players.findByFirebaseUid(uid).orElseThrow();
        });
        if (player.getDisplayName() == null) {
            nameFromGoogle(token).ifPresent(player::rename);
        }
        return player;
    }

    /**
     * The Google account's name as a display name: cut to {@value DisplayName#MAX_LENGTH}
     * characters if longer, and none if it still isn't allowed. Empty for anonymous accounts.
     */
    static Optional<DisplayName> nameFromGoogle(Jwt token) {
        return FirebaseTokens.googleName(token).flatMap(name -> {
            String stripped = name.strip();
            int max = Math.min(stripped.codePointCount(0, stripped.length()), DisplayName.MAX_LENGTH);
            try {
                return Optional.of(DisplayName.of(stripped.substring(0, stripped.offsetByCodePoints(0, max))));
            } catch (InvalidDisplayNameException e) {
                return Optional.empty();
            }
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
     * Changes the display name, profile visibility and/or history visibility of the player a token
     * belongs to, creating the player if needed. Null leaves that one as it is; with all null, the
     * missing name is refused. All are checked before any changes.
     *
     * @throws InvalidDisplayNameException       if the name isn't allowed (see {@link DisplayName})
     * @throws InvalidProfileVisibilityException if a visibility isn't a {@link ProfileVisibility} id
     */
    @Transactional
    public Player update(Jwt token, String displayName, String profileVisibility, String historyVisibility) {
        boolean nothingElse = profileVisibility == null && historyVisibility == null;
        DisplayName name = displayName != null || nothingElse ? DisplayName.of(displayName) : null;
        ProfileVisibility profile = profileVisibility == null ? null : ProfileVisibility.fromId(profileVisibility);
        ProfileVisibility history = historyVisibility == null ? null : ProfileVisibility.fromId(historyVisibility);
        Player player = me(token);
        if (name != null) {
            player.rename(name);
        }
        if (profile != null) {
            player.setProfileVisibility(profile);
        }
        if (history != null) {
            player.setHistoryVisibility(history);
        }
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

    /**
     * Moves an anonymous player into the Google player a token belongs to, then deletes the
     * anonymous one. This is for signing in with Google in a browser that was already playing
     * anonymously, when that Google account already has a player from another device: the browser
     * switches to it, and what it played anonymously comes along.
     *
     * <ul>
     *     <li>Attempts and analytics events move. Leaderboards and streaks are computed from attempts,
     *     so the anonymous solves rank under the Google player from then on. Where both have an
     *     unfinished attempt at the same puzzle, the Google player's is kept.</li>
     *     <li>Friendships move, except one between the two players and ones the Google player
     *     already has.</li>
     *     <li>A supporter badge carries over (the longer of the two), as do Ko-fi payments and
     *     an unused supporter code if the Google player has none.</li>
     *     <li>The anonymous player's display name is taken only if the Google player has none.</li>
     * </ul>
     *
     * Does nothing if the anonymous player doesn't exist (never played, or already merged), so a
     * retried merge is harmless.
     *
     * @param token     the signed-in player's token, which must be a Google sign-in
     * @param anonymous the anonymous account's verified token
     * @throws InvalidMergeException if {@code token} isn't a Google sign-in or {@code anonymous}
     *                               isn't an anonymous one
     */
    @Transactional
    public void mergeAnonymous(Jwt token, Jwt anonymous) {
        if (!FirebaseTokens.isGoogle(token)) {
            throw new InvalidMergeException("Sign in with Google to take over another player's games.");
        }
        if (!FirebaseTokens.isAnonymous(anonymous)) {
            throw new InvalidMergeException("Only an anonymous player can be moved into your account.");
        }
        if (anonymous.getSubject().equals(token.getSubject())) {
            return;
        }
        Optional<Player> source = players.findByFirebaseUid(anonymous.getSubject());
        if (source.isEmpty()) {
            return;
        }
        Player into = forToken(token);
        long from = source.get().getId();
        long to = into.getId();
        // Lock both, in id order, so two merges (or a merge and a delete) can't interleave.
        jdbc.sql("SELECT id FROM players WHERE id IN (:from, :to) ORDER BY id FOR UPDATE")
                .param("from", from).param("to", to).query(Long.class).list();

        if (into.getDisplayName() == null && source.get().getDisplayName() != null) {
            try {
                into.rename(DisplayName.of(source.get().getDisplayName()));
            } catch (InvalidDisplayNameException e) {
                // Allowed when it was picked but not under today's rules: the Google player stays unnamed.
            }
        }
        jdbc.sql("""
                        UPDATE players t SET
                            supporter_since = LEAST(t.supporter_since, f.supporter_since),
                            supporter_until = CASE
                                WHEN t.supporter_since IS NULL THEN f.supporter_until
                                WHEN t.supporter_until IS NULL OR f.supporter_until IS NULL THEN NULL
                                ELSE GREATEST(t.supporter_until, f.supporter_until) END
                        FROM players f
                        WHERE t.id = :to AND f.id = :from AND f.supporter_since IS NOT NULL
                        """)
                .param("from", from).param("to", to).update();

        // At most one unfinished attempt per puzzle (attempts_open_key): keep the Google player's.
        jdbc.sql("""
                        DELETE FROM attempts a
                        WHERE a.player_id = :from AND a.completed_at IS NULL
                          AND EXISTS (SELECT 1 FROM attempts b
                                      WHERE b.player_id = :to AND b.puzzle_id = a.puzzle_id AND b.completed_at IS NULL)
                        """)
                .param("from", from).param("to", to).update();
        jdbc.sql("UPDATE attempts SET player_id = :to WHERE player_id = :from").param("from", from).param("to", to).update();
        jdbc.sql("UPDATE events SET player_id = :to WHERE player_id = :from").param("from", from).param("to", to).update();
        jdbc.sql("UPDATE kofi_payments SET player_id = :to WHERE player_id = :from").param("from", from).param("to", to).update();
        jdbc.sql("""
                        UPDATE supporter_codes SET player_id = :to
                        WHERE player_id = :from AND NOT EXISTS (SELECT 1 FROM supporter_codes WHERE player_id = :to)
                        """)
                .param("from", from).param("to", to).update();
        jdbc.sql("DELETE FROM supporter_codes WHERE player_id = :from").param("from", from).update();

        // Both directions of each of the anonymous player's friendships, as FriendService keeps them.
        jdbc.sql("""
                        INSERT INTO friendships (player_id, friend_id, created_at)
                        SELECT :to, friend_id, created_at FROM friendships WHERE player_id = :from AND friend_id <> :to
                        UNION ALL
                        SELECT player_id, :to, created_at FROM friendships WHERE friend_id = :from AND player_id <> :to
                        ON CONFLICT (player_id, friend_id) DO NOTHING
                        """)
                .param("from", from).param("to", to).update();
        jdbc.sql("DELETE FROM friendships WHERE player_id = :from OR friend_id = :from").param("from", from).update();

        jdbc.sql("DELETE FROM players WHERE id = :from").param("from", from).update();
    }

    /**
     * Deletes the player a token belongs to and everything kept about them: their attempts (so
     * their times leave every leaderboard), their analytics events, their friendships in both directions, their
     * supporter code and Ko-fi payment records, and the player itself. Does nothing if the player doesn't exist, so deleting twice is harmless. The Firebase
     * account is deleted by the app, which holds the sign-in.
     */
    @Transactional
    public void delete(Jwt token) {
        players.findByFirebaseUid(token.getSubject()).ifPresent(player -> {
            long id = player.getId();
            jdbc.sql("DELETE FROM friendships WHERE player_id = :id OR friend_id = :id").param("id", id).update();
            jdbc.sql("DELETE FROM events WHERE player_id = :id").param("id", id).update();
            jdbc.sql("DELETE FROM supporter_codes WHERE player_id = :id").param("id", id).update();
            jdbc.sql("DELETE FROM kofi_payments WHERE player_id = :id").param("id", id).update();
            jdbc.sql("DELETE FROM attempts WHERE player_id = :id").param("id", id).update();
            jdbc.sql("DELETE FROM players WHERE id = :id").param("id", id).update();
        });
    }
}
