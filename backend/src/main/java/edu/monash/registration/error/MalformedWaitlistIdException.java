package edu.monash.registration.error;

/**
 * FR11: a non-numeric waitlist path id. Path ids are opaque, so the student sees the same
 * WAITLIST_NOT_FOUND as for an unknown id, while the attempt is still audited as
 * WAITLIST_LEAVE / FAILURE / VALIDATION_ERROR (mirrors A-19 for registrations).
 */
public class MalformedWaitlistIdException extends WaitlistNotFoundException {
}
