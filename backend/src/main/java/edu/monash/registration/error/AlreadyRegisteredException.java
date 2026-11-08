package edu.monash.registration.error;

import org.springframework.http.HttpStatus;

/** Duplicate (student, course) pair (409 ALREADY_REGISTERED). */
public class AlreadyRegisteredException extends ApiException {

    public AlreadyRegisteredException() {
        super(HttpStatus.CONFLICT, "ALREADY_REGISTERED", "You are already registered in this course.");
    }
}
