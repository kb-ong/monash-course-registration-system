package edu.monash.registration.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Persists failure audits after the business transaction has committed or rolled back.
 *
 * <p>The write goes through {@link AuditWriter} and its dedicated pool, never through the JPA
 * {@code EntityManager}: at {@code AFTER_COMPLETION} Spring has already invoked the listener but not
 * yet released the request thread's connection, so a JPA/`REQUIRES_NEW` write would ask the busy
 * business pool for a second connection (that is the B-2 defect).
 */
@Component
public class AuditFailureListener {

    private final AuditWriter auditWriter;

    public AuditFailureListener(AuditWriter auditWriter) {
        this.auditWriter = auditWriter;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMPLETION)
    public void onFailure(AuditFailureEvent event) {
        auditWriter.recordFailure(event.attemptId(), event.studentId(), event.courseCode(),
                event.action(), event.detail());
    }
}
