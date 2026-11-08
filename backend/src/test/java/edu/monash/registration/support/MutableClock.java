package edu.monash.registration.support;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * A process-wide clock whose instant the tests can move. All application beans that inject
 * {@link Clock} receive this instance through {@link TestSupportConfig}, so changing it freezes or
 * shifts application time (FR7-B3). Tokens used by time-travel tests are signed independently with
 * a far-future expiry so they stay valid while business time moves — expiry semantics are covered
 * separately in {@code JwtServiceTest}.
 */
public final class MutableClock extends Clock {

    public static final ZoneId UNIVERSITY_ZONE = ZoneId.of("Asia/Kuala_Lumpur");

    /** A deterministic instant inside the configured window: 2026-11-08 12:00 +08:00. */
    public static final Instant DEFAULT_INSTANT = Instant.parse("2026-11-08T04:00:00Z");

    private static volatile Instant instant = DEFAULT_INSTANT;

    public static void set(Instant value) {
        instant = value;
    }

    public static void setOffset(String offsetDateTime) {
        instant = OffsetDateTime.parse(offsetDateTime).toInstant();
    }

    public static void reset() {
        instant = DEFAULT_INSTANT;
    }

    public static Instant currentInstant() {
        return instant;
    }

    @Override
    public ZoneId getZone() {
        return UNIVERSITY_ZONE;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return Clock.fixed(instant, zone);
    }

    @Override
    public Instant instant() {
        return instant;
    }
}
