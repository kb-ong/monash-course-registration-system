package edu.monash.registration.error;

import org.springframework.http.HttpStatus;

/**
 * Unknown student id and wrong password produce the identical response (NFR2-S7).
 */
public class InvalidCredentialsException extends ApiException {

    public InvalidCredentialsException() {
        super(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Invalid student ID or password.");
    }
}
