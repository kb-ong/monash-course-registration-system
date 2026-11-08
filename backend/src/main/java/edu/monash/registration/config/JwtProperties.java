package edu.monash.registration.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** {@code app.jwt.*} — HS256 secret and token lifetime (NFR2-S2). */
@Validated
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    /** Optional: when blank, {@code JwtService} generates an ephemeral 256-bit key. */
    private String secret;

    @Min(0)
    @Max(1440)
    private long expirationMinutes = 120;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getExpirationMinutes() {
        return expirationMinutes;
    }

    public void setExpirationMinutes(long expirationMinutes) {
        this.expirationMinutes = expirationMinutes;
    }
}
