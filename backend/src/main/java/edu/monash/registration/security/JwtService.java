package edu.monash.registration.security;

import edu.monash.registration.config.JwtProperties;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import javax.crypto.SecretKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Issues and verifies HS256 tokens. Subject is the studentId; there is no roles claim (NFR2-S2).
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private final SecretKey key;
    private final long expirationMinutes;
    private final Clock clock;

    public JwtService(JwtProperties properties, Clock clock) {
        this.key = resolveKey(properties.getSecret());
        this.expirationMinutes = properties.getExpirationMinutes();
        this.clock = clock;
    }

    private static SecretKey resolveKey(String configuredSecret) {
        if (configuredSecret == null || configuredSecret.isBlank()) {
            log.warn("app.jwt.secret is not configured; generated an ephemeral 256-bit HS256 key. "
                    + "Tokens will not survive a restart.");
            return Jwts.SIG.HS256.key().build();
        }
        byte[] bytes = configuredSecret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException(
                    "app.jwt.secret must be at least 32 bytes (256 bits) for HS256");
        }
        return Keys.hmacShaKeyFor(bytes);
    }

    public String issueToken(String studentId) {
        Instant issuedAt = Instant.now(clock).truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = issuedAt.plus(expirationMinutes, ChronoUnit.MINUTES);
        return Jwts.builder()
                .subject(studentId)
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * @throws JwtException when the token is malformed, expired or not signed by us
     */
    public String parseSubject(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
    }
}
