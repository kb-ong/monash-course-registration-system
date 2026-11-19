package edu.monash.registration.api.dto;

/**
 * 201 payload for joining a waitlist. {@code position} is 1-based; {@code waitlistSize} is the
 * number of students queued for the course immediately after the join.
 */
public record WaitlistJoinResponse(Long waitlistId, int position, int waitlistSize, String message,
        CourseDto course) {
}
