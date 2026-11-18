package dev.noahheller.pidoku.pidokuapi.friend;

import dev.noahheller.pidoku.pidokuapi.player.Player;
import dev.noahheller.pidoku.pidokuapi.player.PlayerRepository;
import dev.noahheller.pidoku.pidokuapi.puzzle.ShareCodeGenerator;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Friendships between players. Adding a friend by their code makes the two of them friends both
 * ways at once, with nothing to accept: friend codes are only shared with people you know. Each
 * friendship is two rows in {@code friendships}, one per direction (see V6), always written and
 * removed together here.
 */
@Service
public class FriendService {

    private final JdbcClient jdbc;
    private final PlayerRepository players;

    public FriendService(JdbcClient jdbc, PlayerRepository players) {
        this.jdbc = jdbc;
        this.players = players;
    }

    /** The player's friends, by name (players without one last), then by when they were added. */
    @Transactional(readOnly = true)
    public List<Friend> friendsOf(Player player) {
        return jdbc.sql("""
                        SELECT p.id, p.display_name
                        FROM friendships f
                        JOIN players p ON p.id = f.friend_id
                        WHERE f.player_id = :playerId
                        ORDER BY lower(p.display_name) NULLS LAST, f.created_at, p.id
                        """)
                .param("playerId", player.getId())
                .query(Friend.class)
                .list();
    }

    /**
     * Makes the player and the owner of {@code friendCode} friends. Adding someone who's already a
     * friend changes nothing.
     *
     * @param friendCode as typed: surrounding spaces and case don't matter
     * @return the new (or existing) friend
     * @throws FriendCodeNotFoundException if no player has the code
     * @throws OwnFriendCodeException      if it's the player's own code
     */
    @Transactional
    public Friend add(Player player, String friendCode) {
        String code = ShareCodeGenerator.normalize(friendCode == null ? "" : friendCode);
        Player friend = players.findByFriendCode(code).orElseThrow(() -> new FriendCodeNotFoundException(code));
        if (friend.getId().equals(player.getId())) {
            throw new OwnFriendCodeException();
        }
        jdbc.sql("""
                        INSERT INTO friendships (player_id, friend_id)
                        VALUES (:a, :b), (:b, :a)
                        ON CONFLICT DO NOTHING
                        """)
                .param("a", player.getId())
                .param("b", friend.getId())
                .update();
        return new Friend(friend.getId(), friend.getDisplayName());
    }

    /** Ends a friendship, for both players. Removing someone who isn't a friend changes nothing. */
    @Transactional
    public void remove(Player player, long friendId) {
        jdbc.sql("""
                        DELETE FROM friendships
                        WHERE (player_id = :a AND friend_id = :b) OR (player_id = :b AND friend_id = :a)
                        """)
                .param("a", player.getId())
                .param("b", friendId)
                .update();
    }
}
