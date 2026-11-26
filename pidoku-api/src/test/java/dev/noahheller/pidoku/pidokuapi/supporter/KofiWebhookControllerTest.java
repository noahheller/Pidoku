package dev.noahheller.pidoku.pidokuapi.supporter;

import dev.noahheller.pidoku.pidokuapi.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.util.Map;

import static dev.noahheller.pidoku.pidokuapi.support.TestTokens.player;
import static org.assertj.core.api.Assertions.assertThat;

@AutoConfigureMockMvc
class KofiWebhookControllerTest extends IntegrationTest {

    /** The token in the test properties. */
    static final String TOKEN = "test-kofi-token";

    @Autowired
    MockMvcTester mvc;

    private String newCode(String uid) {
        assertThat(mvc.post().uri("/api/players/me/supporter-code").with(player(uid))).hasStatusOk();
        return jdbc.queryForObject("""
                SELECT c.code FROM supporter_codes c JOIN players p ON p.id = c.player_id WHERE p.firebase_uid = ?
                """, String.class, uid);
    }

    /** A webhook call shaped like Ko-fi's, with the payer details it always carries. */
    static String payment(String token, String transactionId, String message) {
        return """
                {"verification_token": "%s", "message_id": "3a1fac0c-f960-4506-a60e-824979a74e74",
                 "timestamp": "2026-11-26T08:21:37Z", "type": "Donation", "is_public": true,
                 "from_name": "Jo Example", "message": %s, "amount": "3.00", "url": "https://ko-fi.com/Home/CoffeeShop?txid=0",
                 "email": "jo@example.com", "currency": "USD", "is_subscription_payment": false,
                 "is_first_subscription_payment": false, "kofi_transaction_id": "%s", "shop_items": null,
                 "tier_name": null, "shipping": null}
                """.formatted(token, message == null ? "null" : "\"" + message + "\"", transactionId);
    }

    private MvcTestResult webhook(String data) {
        return mvc.post().uri("/api/kofi/webhook")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("data", data)
                .exchange();
    }

    private boolean isSupporter(String uid) {
        return jdbc.queryForObject("SELECT supporter_since IS NOT NULL FROM players WHERE firebase_uid = ?", Boolean.class, uid);
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    @Test
    void aTipWithALiveCodeMakesItsPlayerASupporterForGood() {
        String code = newCode("alice");

        assertThat(webhook(payment(TOKEN, "tx-1", "Love the game! " + code))).hasStatusOk();

        assertThat(isSupporter("alice")).isTrue();
        assertThat(jdbc.queryForObject("SELECT supporter_until FROM players WHERE firebase_uid = 'alice'", Object.class)).isNull();
        assertThat(mvc.get().uri("/api/players/me").with(player("alice")))
                .bodyJson().extractingPath("$.supporterSince").isNotNull();
        assertThat(count("""
                SELECT count(*) FROM kofi_payments k JOIN players p ON p.id = k.player_id
                WHERE k.transaction_id = 'tx-1' AND p.firebase_uid = 'alice'
                """)).isOne();
    }

    @Test
    void theCodeIsUsedUp() {
        String code = newCode("alice");
        webhook(payment(TOKEN, "tx-1", code));

        assertThat(count("SELECT count(*) FROM supporter_codes")).isZero();
        assertThat(webhook(payment(TOKEN, "tx-2", code))).hasStatusOk();
        assertThat(count("SELECT count(*) FROM kofi_payments WHERE transaction_id = 'tx-2' AND player_id IS NULL")).isOne();
    }

    @Test
    void findsTheCodeWhateverItsCaseOrDash() {
        String code = newCode("alice");
        String typed = "pi–" + code.substring(3).toLowerCase();

        webhook(payment(TOKEN, "tx-1", "my code is " + typed + ", thanks"));

        assertThat(isSupporter("alice")).isTrue();
    }

    @Test
    void storesNothingAboutThePayer() {
        String code = newCode("alice");
        webhook(payment(TOKEN, "tx-1", code));

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM kofi_payments");
        assertThat(row).containsOnlyKeys("transaction_id", "received_at", "player_id");
        assertThat(row.values()).noneMatch(v -> String.valueOf(v).contains("example"));
    }

    @Test
    void aWrongTokenChangesNothing() {
        String code = newCode("alice");

        assertThat(webhook(payment("guess", "tx-1", code))).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(webhook(payment("", "tx-1", code))).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(webhook(payment(TOKEN + "x", "tx-1", code))).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(webhook("{\"kofi_transaction_id\": \"tx-1\", \"message\": \"" + code + "\"}")).hasStatus(HttpStatus.FORBIDDEN);

        assertThat(isSupporter("alice")).isFalse();
        assertThat(count("SELECT count(*) FROM kofi_payments")).isZero();
        assertThat(count("SELECT count(*) FROM supporter_codes")).isOne();
    }

    @Test
    void theSamePaymentTwiceCountsOnce() {
        String first = newCode("alice");
        webhook(payment(TOKEN, "tx-1", first));
        String second = newCode("bob");

        // Ko-fi retrying, or a replay of a call someone saw, with a different message.
        assertThat(webhook(payment(TOKEN, "tx-1", second))).hasStatusOk();

        assertThat(isSupporter("bob")).isFalse();
        assertThat(count("SELECT count(*) FROM kofi_payments")).isOne();
        assertThat(count("SELECT count(*) FROM supporter_codes")).isOne();
    }

    @Test
    void aTipWithoutACodeIsKeptToLinkByHand() {
        newCode("alice");

        assertThat(webhook(payment(TOKEN, "tx-1", null))).hasStatusOk();
        assertThat(webhook(payment(TOKEN, "tx-2", "PI-AAAAAAAA"))).hasStatusOk();

        assertThat(isSupporter("alice")).isFalse();
        assertThat(count("SELECT count(*) FROM kofi_payments WHERE player_id IS NULL")).isEqualTo(2);
    }

    @Test
    void anExpiredCodeDoesNothingAndIsDeleted() {
        String code = newCode("alice");
        jdbc.update("UPDATE supporter_codes SET expires_at = now() - interval '1 minute'");

        assertThat(webhook(payment(TOKEN, "tx-1", code))).hasStatusOk();

        assertThat(isSupporter("alice")).isFalse();
        assertThat(count("SELECT count(*) FROM supporter_codes")).isZero();
    }

    @Test
    void aSecondTipKeepsTheFirstDate() {
        webhook(payment(TOKEN, "tx-1", newCode("alice")));
        jdbc.update("UPDATE players SET supporter_since = '2026-01-01T00:00:00Z'");

        webhook(payment(TOKEN, "tx-2", newCode("alice")));

        assertThat(mvc.get().uri("/api/players/me").with(player("alice")))
                .bodyJson().extractingPath("$.supporterSince").isEqualTo("2026-01-01T00:00:00Z");
    }

    @Test
    void refusesCallsThatArentPayments() {
        assertThat(webhook("not json")).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(webhook(payment(TOKEN, "", "hi"))).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(webhook(payment(TOKEN, "x".repeat(65), "hi"))).hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(mvc.post().uri("/api/kofi/webhook").contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .hasStatus(HttpStatus.BAD_REQUEST);
        assertThat(count("SELECT count(*) FROM kofi_payments")).isZero();
    }

    @Test
    void onlyPostIsOpen() {
        // Ko-fi signs nothing in, so the POST is public (every test above); nothing else is.
        assertThat(mvc.get().uri("/api/kofi/webhook")).hasStatus(HttpStatus.UNAUTHORIZED);
    }
}
