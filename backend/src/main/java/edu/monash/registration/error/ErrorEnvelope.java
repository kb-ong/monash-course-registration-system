package edu.monash.registration.error;

import java.time.OffsetDateTime;

/**
 * The frozen error body: {@code {"error":{"code","message","timestamp"}}}.
 */
public record ErrorEnvelope(ErrorDetail error) {

    public record ErrorDetail(String code, String message, OffsetDateTime timestamp) {
    }

    public static ErrorEnvelope of(String code, String message, OffsetDateTime timestamp) {
        return new ErrorEnvelope(new ErrorDetail(code, message, timestamp));
    }
}
