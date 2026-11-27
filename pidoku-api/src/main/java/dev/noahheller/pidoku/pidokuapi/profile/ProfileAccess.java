package dev.noahheller.pidoku.pidokuapi.profile;

import dev.noahheller.pidoku.pidokuapi.player.Player;
import dev.noahheller.pidoku.pidokuapi.player.ProfileVisibility;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Who may see whose profile, and the history on it: the one check behind both
 * {@link ProfileService} and {@link HistoryService}. Runs inside their transactions.
 */
@Component
class ProfileAccess {

    private final JdbcClient jdbc;

    ProfileAccess(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * A player whose profile the viewer may see, and how the viewer relates to them.
     *
     * @param friendsSince when the viewer and this player became friends; null if they aren't
     */
    record Target(long id, String displayName, boolean you, ProfileVisibility visibility,
                  ProfileVisibility historyVisibility, Instant friendsSince) {

        /**
         * Whether the viewer may see their puzzle history too. It's on the profile, which the viewer
         * can already see, so it's down to the history's own setting.
         */
        boolean historyVisible() {
            return historyVisibility.visibleTo(you, friendsSince != null);
        }
    }

    /**
     * The player with this id, if the viewer may see their profile: their own, a friend's that
     * isn't private, or a public one. Someone else's needs a Google sign-in.
     *
     * @throws GoogleSignInRequiredException if it's someone else's and the viewer is anonymous
     * @throws ProfileNotFoundException      if there's no such player, or the viewer may not see them
     */
    Target target(Player viewer, boolean viewerUsesGoogle, long playerId) {
        boolean you = viewer.getId() == playerId;
        if (!you && !viewerUsesGoogle) {
            throw new GoogleSignInRequiredException();
        }
        record Row(String displayName, String profileVisibility, String historyVisibility, Instant friendsSince) {
        }
        Row row = jdbc.sql("""
                        SELECT p.display_name, p.profile_visibility, p.history_visibility, f.created_at AS friends_since
                        FROM players p
                        LEFT JOIN friendships f ON f.player_id = :viewerId AND f.friend_id = p.id
                        WHERE p.id = :playerId
                        """)
                .param("viewerId", viewer.getId())
                .param("playerId", playerId)
                .query(Row.class)
                .optional()
                .orElseThrow(() -> new ProfileNotFoundException(playerId));
        ProfileVisibility visibility = ProfileVisibility.fromId(row.profileVisibility());
        if (!visibility.visibleTo(you, row.friendsSince() != null)) {
            throw new ProfileNotFoundException(playerId);
        }
        return new Target(playerId, row.displayName(), you, visibility,
                ProfileVisibility.fromId(row.historyVisibility()), row.friendsSince());
    }
}
