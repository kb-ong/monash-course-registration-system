package edu.monash.registration.api.dto;

import java.util.UUID;

public record RegisterResponse(UUID registrationId, String message, CourseDto course,
        int availableSeats) {
}
