package edu.monash.registration.domain;

/**
 * Audit action vocabulary. Failed registrations use REGISTER_REJECTED (A-02); attempts rejected
 * before they reach the service (malformed payload/path) use REGISTER_REJECTED or DROP_REJECTED with
 * detail VALIDATION_ERROR.
 */
public enum AuditAction {
    REGISTER,
    DROP,
    REGISTER_REJECTED,
    DROP_REJECTED
}
