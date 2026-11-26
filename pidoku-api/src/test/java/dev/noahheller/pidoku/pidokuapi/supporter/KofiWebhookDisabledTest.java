package dev.noahheller.pidoku.pidokuapi.supporter;

import dev.noahheller.pidoku.pidokuapi.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;

/** Without a verification token (e.g. not yet set up on Cloud Run), the webhook accepts nothing. */
@AutoConfigureMockMvc
@TestPropertySource(properties = "pidoku.kofi.verification-token=")
class KofiWebhookDisabledTest extends IntegrationTest {

    @Autowired
    MockMvcTester mvc;

    @Test
    void answersNotFound() {
        // Even a blank token in the call: blank must never match a blank setting.
        assertThat(mvc.post().uri("/api/kofi/webhook").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("data", KofiWebhookControllerTest.payment("", "tx-1", null)))
                .hasStatus(HttpStatus.NOT_FOUND);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM kofi_payments", Integer.class)).isZero();
    }
}
