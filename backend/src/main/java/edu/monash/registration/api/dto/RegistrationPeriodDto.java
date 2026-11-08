package edu.monash.registration.api.dto;

import java.time.OffsetDateTime;

/** E6 payload; instants carry the university offset (+08:00). */
public record RegistrationPeriodDto(
        String semester,
        OffsetDateTime startAt,
        OffsetDateTime endAt,
        boolean open,
        OffsetDateTime serverTime) {
}
