package edu.monash.registration.api.dto;

import java.util.UUID;

/**
 * FR8: the minimal prerequisite identity shown in a course payload and in the
 * {@code PREREQUISITE_NOT_MET} message. No seat or schedule data: prerequisites are history, not
 * registration options.
 */
public record PrerequisiteDto(UUID id, String code, String name) {
}
