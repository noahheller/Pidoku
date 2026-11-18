package dev.noahheller.pidoku.pidokuapi.friend;

/**
 * One of the signed-in player's friends.
 *
 * @param id            the friend's player id, for removing them
 * @param displayName   null if they haven't picked one
 * @param currentStreak their current daily streak as of the viewer's date; null when the request
 *                      didn't say what the date is
 */
public record Friend(long id, String displayName, Integer currentStreak) {

    Friend withStreak(Integer streak) {
        return new Friend(id, displayName, streak);
    }
}
