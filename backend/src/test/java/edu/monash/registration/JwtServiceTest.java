package edu.monash.registration;

import edu.monash.registration.config.JwtProperties;
import edu.monash.registration.security.JwtService;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** FR1 — JWT claim and signature semantics (T-FR1-10, T-FR1-11 at the service level). */
@DisplayName("FR1 — JWT service")
class JwtServiceTest {

    private static final String SECRET =
            "unit-test-secret-for-monash-course-registration-0123456789";

    private JwtProperties properties;
    private JwtService jwtService;
    private SecretKey key;

    @BeforeEach
    void setUp() {
        properties = new JwtProperties();
        properties.setSecret(SECRET);
        properties.setExpirationMinutes(120);
        jwtService = new JwtService(properties,
                Clock.fixed(Instant.now(), ZoneId.of("Asia/Kuala_Lumpur")));
        key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("T-FR1-01a the token subject is the student id and the token has three segments")
    void issueAndParse() {
        String token = jwtService.issueToken("32012345");
        assertThat(token.split("\\.")).hasSize(3);
        assertThat(jwtService.parseSubject(token)).isEqualTo("32012345");
    }

    @Test
    @DisplayName("T-FR1-10 a token signed with another secret is rejected")
    void wrongSignature() {
        SecretKey other = Keys.hmacShaKeyFor(
                "another-secret-key-for-the-unit-test-0123456789abcdef"
                        .getBytes(StandardCharsets.UTF_8));
        String token = Jwts.builder()
                .subject("32012345")
                .issuedAt(new Date())
                .expiration(Date.from(Instant.now().plus(1, ChronoUnit.HOURS)))
                .signWith(other, Jwts.SIG.HS256)
                .compact();

        assertThatThrownBy(() -> jwtService.parseSubject(token))
                .isInstanceOf(SignatureException.class);
    }

    @Test
    @DisplayName("T-FR1-11 an expired token is rejected")
    void expired() {
        Instant past = Instant.now().minus(2, ChronoUnit.HOURS);
        String token = Jwts.builder()
                .subject("32012345")
                .issuedAt(Date.from(past.minus(120, ChronoUnit.MINUTES)))
                .expiration(Date.from(past))
                .signWith(key, Jwts.SIG.HS256)
                .compact();

        assertThatThrownBy(() -> jwtService.parseSubject(token))
                .isInstanceOf(ExpiredJwtException.class);
    }
}
