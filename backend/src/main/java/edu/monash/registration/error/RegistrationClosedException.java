package edu.monash.registration.error;

import org.springframework.http.HttpStatus;

/** Attempt to mutate registrations outside the configured window (403 REGISTRATION_CLOSED). */
public class RegistrationClosedException extends ApiException {

    public RegistrationClosedException(String message) {
        super(HttpStatus.FORBIDDEN, "REGISTRATION_CLOSED", message);
    }
}
