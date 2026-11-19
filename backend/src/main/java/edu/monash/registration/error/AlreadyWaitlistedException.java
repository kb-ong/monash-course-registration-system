package edu.monash.registration.error;

import org.springframework.http.HttpStatus;

/** FR11: one queue place per student per course, enforced by the unique key and this check. */
public class AlreadyWaitlistedException extends ApiException {

    public AlreadyWaitlistedException(String courseCode) {
        super(HttpStatus.CONFLICT, "ALREADY_WAITLISTED",
                "You are already on the waitlist for " + courseCode + ".");
    }
}
