package edu.monash.registration.error;

import org.springframework.http.HttpStatus;

/**
 * FR11: a waitlist is only meaningful for a course that is full. If a seat is free at the moment
 * of the join, the honest answer is "register directly", not "join a queue".
 */
public class SeatAvailableException extends ApiException {

    public SeatAvailableException(String courseCode) {
        super(HttpStatus.CONFLICT, "SEAT_AVAILABLE",
                courseCode + " has seats available. Register for it instead of joining the waitlist.");
    }
}
