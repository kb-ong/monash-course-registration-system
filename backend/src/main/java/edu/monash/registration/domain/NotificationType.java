package edu.monash.registration.domain;

/**
 * FR11 notification vocabulary. Phase 2 has no email/SMS transport (NG10): notifications are
 * persisted in-app events surfaced by the bell in the application header.
 */
public enum NotificationType {
    /** Confirmation that a place in the queue was taken, carrying the position at that moment. */
    WAITLIST_JOINED,
    /** The student was automatically enrolled because they were first in line when a seat freed. */
    WAITLIST_PROMOTED
}
