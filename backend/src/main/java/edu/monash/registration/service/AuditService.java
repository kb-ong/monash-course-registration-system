package edu.monash.registration.service;

import edu.monash.registration.domain.AuditAction;
import edu.monash.registration.domain.AuditLog;
import edu.monash.registration.domain.AuditOutcome;
import edu.monash.registration.repository.AuditLogRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the append-only audit trail (NFR4).
 *
 * <p>Success rows join the caller's transaction, so they commit or roll back with the registration
 * itself. Failure rows are published as an event and written after the transaction completes, by
 * {@link AuditWriter} on its own connection pool ({@link AuditFailureListener}); they must survive a
 * rollback (NFR4-1) and must not need a connection from the busy business pool (B-2).
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository auditLogRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    public AuditService(AuditLogRepository auditLogRepository, ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.auditLogRepository = auditLogRepository;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void recordSuccess(String studentId, String courseCode, AuditAction action) {
        auditLogRepository.save(new AuditLog(studentId, courseCode, action, AuditOutcome.SUCCESS, null,
                Instant.now(clock).truncatedTo(ChronoUnit.SECONDS)));
        log.debug("audit.record studentId={} course={} action={} outcome={}", studentId, courseCode,
                action, AuditOutcome.SUCCESS);
    }

    /**
     * Defers a failure row until the caller's transaction has completed (commit or rollback).
     * Never opens a nested transaction while the business transaction still holds a row lock.
     */
    public void publishFailure(String studentId, String courseCode, AuditAction action, String detail) {
        eventPublisher.publishEvent(
                new AuditFailureEvent(UUID.randomUUID(), studentId, courseCode, action, detail));
    }
}
