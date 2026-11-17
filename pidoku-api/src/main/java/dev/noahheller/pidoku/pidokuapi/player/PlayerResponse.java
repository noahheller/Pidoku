package dev.noahheller.pidoku.pidokuapi.player;

import java.time.Instant;

/** The signed-in player. {@code displayName} is null until they pick one. */
public record PlayerResponse(long id, String displayName, Instant createdAt) {

    public static PlayerResponse from(Player player) {
        return new PlayerResponse(player.getId(), player.getDisplayName(), player.getCreatedAt());
    }
}
