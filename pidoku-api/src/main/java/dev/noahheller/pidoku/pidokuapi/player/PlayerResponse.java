package dev.noahheller.pidoku.pidokuapi.player;

import java.time.Instant;

/**
 * The signed-in player. {@code displayName} is null until they pick one.
 *
 * @param friendCode        what friends enter to add this player
 * @param profileVisibility who can see their profile
 * @param historyVisibility who can see their puzzle history on it
 * @param supporterSince    when they first supported Pidoku on Ko-fi; null if they don't have a
 *                          supporter badge
 */
public record PlayerResponse(long id, String displayName, String friendCode, ProfileVisibility profileVisibility,
                             ProfileVisibility historyVisibility, Instant createdAt, Instant supporterSince) {

    /** @param now for whether their supporter badge has run out */
    public static PlayerResponse from(Player player, Instant now) {
        return new PlayerResponse(player.getId(), player.getDisplayName(), player.getFriendCode(),
                player.getProfileVisibility(), player.getHistoryVisibility(), player.getCreatedAt(),
                player.isSupporter(now) ? player.getSupporterSince() : null);
    }
}
