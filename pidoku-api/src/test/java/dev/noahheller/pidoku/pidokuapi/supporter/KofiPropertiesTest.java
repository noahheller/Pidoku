package dev.noahheller.pidoku.pidokuapi.supporter;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KofiPropertiesTest {

    @Test
    void aBlankTokenTurnsTheWebhookOff() {
        assertThat(new KofiProperties(null).webhookEnabled()).isFalse();
        assertThat(new KofiProperties(" ").webhookEnabled()).isFalse();
        assertThat(new KofiProperties("t").webhookEnabled()).isTrue();
    }

    @Test
    void neverPrintsTheToken() {
        assertThat(new KofiProperties("secret-token").toString()).doesNotContain("secret-token");
    }
}
