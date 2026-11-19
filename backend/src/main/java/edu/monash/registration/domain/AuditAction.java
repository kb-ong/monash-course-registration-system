package edu.monash.registration.domain;

/**
 * Audit action vocabulary. Failed registrations use REGISTER_REJECTED (A-02); attempts rejected
 * before they reach the service (malformed payload/path) use REGISTER_REJECTED or DROP_REJECTED with
 * detail VALIDATION_ERROR.
 *
 * <p>FR11 adds the waitlist vocabulary (A-24): joins use WAITLIST_JOIN on success and
 * WAITLIST_REJECTED on failure, removals use WAITLIST_LEAVE for both outcomes, and an automatic
 * enrolment from the queue uses WAITLIST_PROMOTED. Values must stay within the 20-character
 * {@code audit_log.action} column.
 */
public enum AuditAction {
    REGISTER,
    DROP,
    REGISTER_REJECTED,
    DROP_REJECTED,
    WAITLIST_JOIN,
    WAITLIST_LEAVE,
    WAITLIST_REJECTED,
    WAITLIST_PROMOTED
}
