package edu.monash.registration.service;

import edu.monash.registration.domain.AuditAction;
import java.util.UUID;

/**
 * A rejected register/drop attempt. Published inside the business transaction and persisted by
 * {@link AuditFailureListener} after that transaction completes, through the dedicated audit
 * connection pool (NFR4, B-2).
 *
 * <p>{@code attemptId} identifies this one attempt for the lifetime of the event: it makes the
 * audit insert idempotent without needing the business transaction's session.
 */
public record AuditFailureEvent(
        UUID attemptId,
        String studentId,
        String courseCode,
        AuditAction action,
        String detail) {
}
