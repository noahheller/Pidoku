package dev.noahheller.pidoku.pidokuapi.friend;

/**
 * One of the signed-in player's friends.
 *
 * @param id          the friend's player id, for removing them
 * @param displayName null if they haven't picked one
 */
public record Friend(long id, String displayName) {
}
