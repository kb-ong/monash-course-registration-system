package edu.monash.registration.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Login payload. studentId is trimmed by the service; password never is (FR1-B1).
 */
public record LoginRequest(
        @NotBlank(message = "studentId is required.")
        @Pattern(regexp = "^\\s*[0-9]{8}\\s*$", message = "studentId must be an 8-digit student number.")
        String studentId,

        @NotBlank(message = "password is required.")
        @Size(max = 72, message = "password must be at most 72 characters.")
        String password) {
}
