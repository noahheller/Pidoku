package dev.noahheller.pidoku.pidokuapi.web;

import java.util.Arrays;
import java.util.Locale;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Enums shown in the API by their names in lower case ({@code hard}, {@code share_arrival}), and
 * read back ignoring case.
 */
public final class ApiIds {

    private ApiIds() {
    }

    /** {@code value}'s id: its name in lower case. */
    public static String of(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }

    /**
     * The constant of {@code type} with this id, ignoring case and surrounding spaces.
     *
     * @param what what the ids are, for the error message, e.g. "difficulty"
     * @throws IllegalArgumentException naming the valid ids, if {@code id} isn't one
     */
    public static <E extends Enum<E>> E parse(Class<E> type, String id, String what) {
        return parse(type, id, what, IllegalArgumentException::new);
    }

    /**
     * Like {@link #parse(Class, String, String)}, throwing the exception {@code error} makes from the
     * message instead.
     */
    public static <E extends Enum<E>> E parse(Class<E> type, String id, String what,
                                              Function<String, ? extends RuntimeException> error) {
        E[] values = type.getEnumConstants();
        if (id != null) {
            String wanted = id.strip();
            for (E value : values) {
                if (of(value).equalsIgnoreCase(wanted)) {
                    return value;
                }
            }
        }
        throw error.apply("Unknown " + what + " '" + id + "'. Valid values: "
                + Arrays.stream(values).map(ApiIds::of).collect(Collectors.joining(", ")));
    }
}
