package edu.monash.registration.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * A persisted in-app notification. The embedded course is reduced to the three fields the UI needs
 * to link back to the course page; a full course payload would duplicate the catalogue.
 */
public record NotificationItemDto(Long id, String type, String message, Instant createdAt,
        boolean read, UUID courseId, String courseCode, String courseName) {
}
