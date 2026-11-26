package dev.noahheller.pidoku.pidokuapi.supporter;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings under {@code pidoku.kofi}.
 *
 * @param verificationToken the token Ko-fi sends with every webhook call, from Ko-fi's webhook
 *                          settings ({@code KOFI_VERIFICATION_TOKEN}, a Secret Manager secret on the
 *                          Cloud Run service). Blank turns the webhook off.
 */
@ConfigurationProperties("pidoku.kofi")
public record KofiProperties(String verificationToken) {

    public boolean webhookEnabled() {
        return verificationToken != null && !verificationToken.isBlank();
    }

    /** Hides the token, so logging the properties can't leak it. */
    @Override
    public String toString() {
        return "KofiProperties[verificationToken=" + (webhookEnabled() ? "***" : "") + "]";
    }
}
