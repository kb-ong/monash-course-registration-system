package edu.monash.registration.error;

import org.springframework.http.HttpStatus;

/** Unknown or already dropped registration id (404 REGISTRATION_NOT_FOUND). */
public class RegistrationNotFoundException extends ApiException {

    public RegistrationNotFoundException() {
        super(HttpStatus.NOT_FOUND, "REGISTRATION_NOT_FOUND", "Registration not found.");
    }
}
