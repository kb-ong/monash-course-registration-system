package edu.monash.registration.error;

import org.springframework.http.HttpStatus;

/**
 * A structurally invalid registration id in the path. The frozen contract calls this 404
 * REGISTRATION_NOT_FOUND, never 400 (T-FR4-10); it is a distinct type so the error boundary can
 * record the attempt as a validation rejection without guessing.
 */
public class MalformedRegistrationIdException extends ApiException {

    public MalformedRegistrationIdException() {
        super(HttpStatus.NOT_FOUND, "REGISTRATION_NOT_FOUND", "Registration not found.");
    }
}
