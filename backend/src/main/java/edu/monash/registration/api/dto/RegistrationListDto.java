package edu.monash.registration.api.dto;

import java.util.List;

/**
 * FR5/FR10: the student's active-semester registrations with the configured credit limit and the
 * credits still available. {@code remainingCredits} is floored at zero because a registration that
 * would cross the limit is rejected (FR10-B3).
 */
public record RegistrationListDto(String semester, int totalCredits, int maxCredits,
        int remainingCredits, List<RegistrationItemDto> items) {
}
