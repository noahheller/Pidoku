package dev.noahheller.pidoku.pidokuapi.player;

import java.time.Instant;

/**
 * The signed-in player. {@code displayName} is null until they pick one.
 *
 * @param friendCode        what friends enter to add this player
 * @param profileVisibility who can see their profile
 */
public record PlayerResponse(long id, String displayName, String friendCode, ProfileVisibility profileVisibility,
                             Instant createdAt) {

    public static PlayerResponse from(Player player) {
        return new PlayerResponse(player.getId(), player.getDisplayName(), player.getFriendCode(),
                player.getProfileVisibility(), player.getCreatedAt());
    }
}
