package dev.noahheller.pidoku.pidokuapi.supporter;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.noahheller.pidoku.pidokuapi.web.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Ko-fi's webhook: Ko-fi calls it for every payment, as {@code application/x-www-form-urlencoded}
 * with one field, {@code data}, holding the payment as JSON. Public (Ko-fi sends no sign-in); what
 * proves a call comes from Ko-fi is the verification token inside the JSON, a secret shared with
 * Ko-fi's webhook settings ({@link KofiProperties}). Ko-fi doesn't sign its calls, so the token is
 * checked before anything touches the database, and a resent call changes nothing
 * ({@link SupporterService#recordPayment}).
 *
 * <p>Only the token, the transaction id and the message are read from the JSON; the payer's name,
 * email, the amount and the rest are never bound, stored or logged.
 */
@Slf4j
@RestController
public class KofiWebhookController {

    /** Longer than any Ko-fi transaction id (a UUID); the column's size. */
    static final int MAX_TRANSACTION_ID_LENGTH = 64;

    private final SupporterService supporterService;
    private final KofiProperties properties;
    private final JsonMapper json;

    public KofiWebhookController(SupporterService supporterService, KofiProperties properties, JsonMapper json) {
        this.supporterService = supporterService;
        this.properties = properties;
        this.json = json;
    }

    /** The parts of Ko-fi's payment JSON that are used. Everything else is ignored. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record KofiPayment(@JsonProperty("verification_token") String verificationToken,
                       @JsonProperty("kofi_transaction_id") String transactionId,
                       @JsonProperty("message") String message) {

        /** Leaves out the token and the message, so logging a payment can't leak either. */
        @Override
        public String toString() {
            return "KofiPayment[transactionId=" + transactionId + "]";
        }
    }

    /**
     * 200 once the payment is recorded (or was already), which is all Ko-fi needs. 404 while no
     * verification token is configured, 403 for a wrong token, 400 for a call that isn't a payment.
     */
    @PostMapping(path = "/api/kofi/webhook", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public void payment(@RequestParam("data") String data) {
        if (!properties.webhookEnabled()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Not found.");
        }
        KofiPayment payment;
        try {
            payment = json.readValue(data, KofiPayment.class);
        } catch (JacksonException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "data isn't a Ko-fi payment.");
        }
        if (payment == null || !tokenMatches(payment.verificationToken())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Wrong verification token.");
        }
        String transactionId = payment.transactionId();
        if (transactionId == null || transactionId.isBlank() || transactionId.length() > MAX_TRANSACTION_ID_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "kofi_transaction_id is missing or too long.");
        }
        SupporterService.Outcome outcome = supporterService.recordPayment(transactionId, payment.message());
        log.info("Ko-fi payment {}: {}", transactionId, outcome);
    }

    /** In constant time, so the response time says nothing about how much of a guess was right. */
    private boolean tokenMatches(String token) {
        if (token == null) {
            return false;
        }
        return MessageDigest.isEqual(token.getBytes(StandardCharsets.UTF_8),
                properties.verificationToken().getBytes(StandardCharsets.UTF_8));
    }
}
