package dev.noahheller.pidoku.pidokuapi.supporter;

import dev.noahheller.pidoku.pidokuapi.player.Player;
import dev.noahheller.pidoku.pidokuapi.puzzle.ShareCodeGenerator;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Supporter badges for Ko-fi tips, linked without storing anything about the payer.
 *
 * <p>A player asks for a single-use code ({@link #newCode}) and pastes it into the message of their
 * Ko-fi tip. Ko-fi's webhook ({@link KofiWebhookController}) passes the payment here, and a live
 * code in its message makes that player a supporter for good. The minimum tip is set on Ko-fi
 * itself, so any amount that arrives counts. Only Ko-fi's transaction id is kept
 * (so a resent webhook changes nothing, and a payment without a code can be linked by hand); the
 * payer's name, email, message and the amount are dropped. The badge never affects ranking.
 */
@Service
public class SupporterService {

    /** How long a code can be used: long enough to come back and tip some days later. */
    static final Duration CODE_LIFETIME = Duration.ofDays(30);

    /** What every code starts with, so one can be picked out of a free-text message. */
    static final String PREFIX = "PI-";

    /**
     * A code anywhere in a message, whatever its case, also with a space or a dash that a phone
     * turned into an en or em dash in place of the hyphen.
     */
    private static final Pattern CODE_IN_MESSAGE = Pattern.compile(
            "\\bPI[-\u2010\u2013\u2014 ]?([" + ShareCodeGenerator.ALPHABET + "]{" + ShareCodeGenerator.LENGTH + "})\\b",
            Pattern.CASE_INSENSITIVE);

    /** What became of one webhook call. */
    public enum Outcome {
        /** The message held a live code: its player is now a supporter. */
        LINKED,
        /** No live code in the message: kept by transaction id only, to link by hand. */
        UNMATCHED,
        /** A transaction already recorded, e.g. Ko-fi retrying: nothing changed. */
        DUPLICATE
    }

    /**
     * @param code      what the player pastes into their Ko-fi message
     * @param expiresAt when it stops working
     */
    public record SupporterCode(String code, Instant expiresAt) {
    }

    private final JdbcClient jdbc;
    private final ShareCodeGenerator codes;
    private final Clock clock;

    public SupporterService(JdbcClient jdbc, ShareCodeGenerator codes, Clock clock) {
        this.jdbc = jdbc;
        this.codes = codes;
        this.clock = clock;
    }

    /** A new code for the player, replacing any code they had. */
    @Transactional
    public SupporterCode newCode(Player player) {
        Instant expiresAt = clock.instant().plus(CODE_LIFETIME);
        String code = PREFIX + codes.nextUnused("supporter code", c -> exists(PREFIX + c));
        jdbc.sql("""
                        INSERT INTO supporter_codes (player_id, code, expires_at) VALUES (:playerId, :code, :expiresAt)
                        ON CONFLICT (player_id) DO UPDATE SET code = EXCLUDED.code, expires_at = EXCLUDED.expires_at
                        """)
                .param("playerId", player.getId())
                .param("code", code)
                .param("expiresAt", utc(expiresAt))
                .update();
        return new SupporterCode(code, expiresAt);
    }

    private boolean exists(String code) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM supporter_codes WHERE code = :code)")
                .param("code", code).query(Boolean.class).single();
    }

    /**
     * Records one Ko-fi payment (already verified as coming from Ko-fi) and, if its message holds a
     * live code, makes the code's player a supporter and uses the code up. A one-off tip makes the
     * badge permanent. Safe when Ko-fi sends the same payment twice, even at once.
     *
     * @param transactionId Ko-fi's id for the payment
     * @param message       the message the payer wrote; may be null
     */
    @Transactional
    public Outcome recordPayment(String transactionId, String message) {
        int inserted = jdbc.sql("INSERT INTO kofi_payments (transaction_id) VALUES (:id) ON CONFLICT DO NOTHING")
                .param("id", transactionId)
                .update();
        if (inserted == 0) {
            return Outcome.DUPLICATE;
        }
        OffsetDateTime now = utc(clock.instant());
        // Expired codes are of no use to anyone; don't keep them.
        jdbc.sql("DELETE FROM supporter_codes WHERE expires_at <= :now").param("now", now).update();

        for (String code : codesIn(message)) {
            Optional<Long> playerId = jdbc.sql("DELETE FROM supporter_codes WHERE code = :code RETURNING player_id")
                    .param("code", code)
                    .query(Long.class)
                    .optional();
            if (playerId.isPresent()) {
                jdbc.sql("UPDATE players SET supporter_since = COALESCE(supporter_since, :now), supporter_until = NULL WHERE id = :id")
                        .param("now", now)
                        .param("id", playerId.get())
                        .update();
                jdbc.sql("UPDATE kofi_payments SET player_id = :playerId WHERE transaction_id = :id")
                        .param("playerId", playerId.get())
                        .param("id", transactionId)
                        .update();
                return Outcome.LINKED;
            }
        }
        return Outcome.UNMATCHED;
    }

    /** The codes in a message, in their stored form, first first. */
    static List<String> codesIn(String message) {
        List<String> found = new ArrayList<>();
        if (message == null) {
            return found;
        }
        Matcher matcher = CODE_IN_MESSAGE.matcher(message);
        while (matcher.find()) {
            found.add(PREFIX + ShareCodeGenerator.normalize(matcher.group(1)));
        }
        return found;
    }

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
