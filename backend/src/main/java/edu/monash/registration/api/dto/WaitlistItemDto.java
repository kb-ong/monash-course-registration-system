package edu.monash.registration.api.dto;

import java.time.Instant;

/** One course the authenticated student is queued for, with their live 1-based position. */
public record WaitlistItemDto(Long waitlistId, Instant joinedAt, int position, CourseDto course) {
}
