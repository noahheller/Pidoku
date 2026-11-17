package dev.noahheller.pidoku.pidokuapi.player;

import java.util.regex.Pattern;

/**
 * A name a player picked for friends and leaderboards to show: surrounding spaces trimmed, runs of
 * whitespace inside it collapsed to one space, then 1 to {@value #MAX_LENGTH} characters with no
 * control characters. Characters are code points, as Postgres counts them for
 * {@code VARCHAR(40)}, so an emoji counts as one.
 */
public record DisplayName(String value) {

    public static final int MAX_LENGTH = 40;

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    // Control and format characters (\p{C} also covers unassigned and private-use code points), but
    // the zero-width joiner stays: emoji like 👩‍💻 are built with it.
    private static final Pattern INVISIBLE = Pattern.compile("[\\p{C}&&[^\\u200D]]");

    /**
     * Cleans up and checks a name the player typed.
     *
     * @throws InvalidDisplayNameException if it's missing, empty, too long or has control characters
     */
    public static DisplayName of(String raw) {
        if (raw == null) {
            throw new InvalidDisplayNameException("Pick a display name.");
        }
        String name = WHITESPACE.matcher(raw.strip()).replaceAll(" ");
        if (name.isEmpty()) {
            throw new InvalidDisplayNameException("Pick a display name.");
        }
        if (name.codePointCount(0, name.length()) > MAX_LENGTH) {
            throw new InvalidDisplayNameException("Display names can be at most " + MAX_LENGTH + " characters.");
        }
        if (INVISIBLE.matcher(name).find()) {
            throw new InvalidDisplayNameException("Display names can't contain invisible or control characters.");
        }
        return new DisplayName(name);
    }
}
