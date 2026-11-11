package dev.noahheller.pidoku.pidokuapi.puzzle;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.random.RandomGenerator;

/**
 * Makes the short codes in share links like {@code /p/7KQ4M2XD}.
 *
 * <p>Codes are random rather than derived from the puzzle id, so nobody can find other puzzles by
 * counting through ids. The alphabet leaves out characters that are easy to misread or mistype
 * (0/O, 1/I/L), and since it has no lower-case letters, codes are case-insensitive: see
 * {@link #normalize}. 31 characters over 8 places gives about 8.5 × 10¹¹ codes.
 */
@Component
public class ShareCodeGenerator {

    static final String ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    public static final int LENGTH = 8;

    private final RandomGenerator random;

    public ShareCodeGenerator() {
        this(new SecureRandom());
    }

    ShareCodeGenerator(RandomGenerator random) {
        this.random = random;
    }

    /** A new random code. It may already be taken; the caller checks. */
    public String next() {
        char[] code = new char[LENGTH];
        for (int i = 0; i < LENGTH; i++) {
            code[i] = ALPHABET.charAt(random.nextInt(ALPHABET.length()));
        }
        return new String(code);
    }

    /** The stored form of a code typed or pasted by a player. */
    public static String normalize(String code) {
        return code.strip().toUpperCase(Locale.ROOT);
    }
}
