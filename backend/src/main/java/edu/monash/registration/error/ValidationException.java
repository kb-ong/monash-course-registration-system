package edu.monash.registration.error;

import org.springframework.http.HttpStatus;

/** Malformed or missing request data (400 VALIDATION_ERROR). */
public class ValidationException extends ApiException {

    public ValidationException(String message) {
        super(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }
}
