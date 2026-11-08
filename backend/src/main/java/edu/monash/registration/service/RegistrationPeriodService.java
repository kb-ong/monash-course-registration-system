package edu.monash.registration.service;

import edu.monash.registration.api.dto.RegistrationPeriodDto;
import edu.monash.registration.config.ClockConfig;
import edu.monash.registration.config.RegistrationProperties;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import org.springframework.stereotype.Service;

/**
 * Single source of truth for "is registration open right now?" (FR7). Comparisons are on
 * OffsetDateTime values, never on formatted strings; both ends are inclusive (A-07).
 */
@Service
public class RegistrationPeriodService {

    private static final DateTimeFormatter HUMAN =
            DateTimeFormatter.ofPattern("d MMMM yyyy, h:mm a", Locale.ENGLISH);

    private final RegistrationProperties properties;
    private final Clock clock;

    public RegistrationPeriodService(RegistrationProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public RegistrationPeriodDto current() {
        OffsetDateTime now = OffsetDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        OffsetDateTime startAt = toOffset(properties.getPeriodStart());
        OffsetDateTime endAt = toOffset(properties.getPeriodEnd());
        boolean open = !now.isBefore(startAt) && !now.isAfter(endAt);
        return new RegistrationPeriodDto(properties.getActiveSemester(), startAt, endAt, open, now);
    }

    public boolean isOpen() {
        return current().open();
    }

    /**
     * Human readable reason used in the 403 body and the audit trail, e.g.
     * "Registration closed on 20 December 2026, 11:59 pm."
     */
    public String closedMessage() {
        RegistrationPeriodDto period = current();
        if (period.serverTime().isBefore(period.startAt())) {
            return "Registration opens on " + humanize(period.startAt()) + ".";
        }
        return "Registration closed on " + humanize(period.endAt()) + ".";
    }

    public String activeSemester() {
        return properties.getActiveSemester();
    }

    private OffsetDateTime toOffset(LocalDateTime localDateTime) {
        return localDateTime.atZone(ClockConfig.UNIVERSITY_ZONE).toOffsetDateTime();
    }

    private String humanize(OffsetDateTime instant) {
        return HUMAN.format(instant.atZoneSameInstant(ClockConfig.UNIVERSITY_ZONE))
                .replace("AM", "am")
                .replace("PM", "pm");
    }
}
