package edu.monash.registration.error;

import org.springframework.http.HttpStatus;

/**
 * FR11: unknown, already-removed or another student's waitlist entry. Path ids are opaque, so all
 * three cases are reported identically (no ownership oracle).
 */
public class WaitlistNotFoundException extends ApiException {

    public WaitlistNotFoundException() {
        super(HttpStatus.NOT_FOUND, "WAITLIST_NOT_FOUND", "Waitlist entry not found.");
    }
}
