package dev.noahheller.pidoku.pidokuapi.supporter;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SupporterServiceTest {

    @Test
    void findsCodesInAMessage() {
        assertThat(SupporterService.codesIn("Thanks! PI-7K3M9QAB")).containsExactly("PI-7K3M9QAB");
        assertThat(SupporterService.codesIn("pi-7k3m9qab")).containsExactly("PI-7K3M9QAB");
        assertThat(SupporterService.codesIn("PI 7K3M9QAB and PI—7K3M9QAC")).containsExactly("PI-7K3M9QAB", "PI-7K3M9QAC");
    }

    @Test
    void ignoresWhatIsntACode() {
        assertThat(SupporterService.codesIn(null)).isEmpty();
        assertThat(SupporterService.codesIn("Love it")).isEmpty();
        // Too short, too long, letters not in the alphabet, or inside another word.
        assertThat(SupporterService.codesIn("PI-7K3M9QA")).isEmpty();
        assertThat(SupporterService.codesIn("PI-7K3M9QABC")).isEmpty();
        assertThat(SupporterService.codesIn("PI-7K3M9QA0")).isEmpty();
        assertThat(SupporterService.codesIn("SPI-7K3M9QAB")).isEmpty();
    }
}
