package edu.monash.registration.api.dto;

import java.time.Instant;
import java.util.UUID;

public record RegistrationItemDto(UUID registrationId, Instant registeredAt, CourseDto course) {
}
