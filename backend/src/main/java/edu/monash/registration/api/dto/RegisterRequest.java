package edu.monash.registration.api.dto;

import jakarta.validation.constraints.NotBlank;

/** E8 payload. Any other fields (including studentId) are ignored (FR3-B2). */
public record RegisterRequest(
        @NotBlank(message = "courseId is required.")
        String courseId) {
}
