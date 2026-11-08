package edu.monash.registration.service;

import edu.monash.registration.domain.AuditAction;
import edu.monash.registration.domain.AuditOutcome;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Writes failure audit rows through a dedicated connection pool (B-2), with plain JDBC so the write
 * depends on nothing from the JPA session that has just committed or rolled back.
 *
 * <p>Guarantees:
 *
 * <ul>
 *   <li>One row per attempt. The row id is derived deterministically from the attempt id, so a
 *       retried insert of an attempt that was already recorded collides on the primary key instead
 *       of duplicating the row.
 *   <li>An audit failure never escapes: the caller's 201/409 status is already decided, and turning
 *       a business outcome into a 500 because the audit trail hiccuped would be worse than a lost
 *       row. Losses are logged at ERROR with the attempt id (NFR4-1, NFR4-6).
 * </ul>
 */
@Component
public class AuditWriter {

    private static final Logger log = LoggerFactory.getLogger(AuditWriter.class);

    private static final String INSERT_AUDIT_ROW = """
            insert into audit_log (id, student_id, course_code, action, outcome, detail, created_at)
            values (?, ?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate auditJdbcTemplate;
    private final Clock clock;

    public AuditWriter(@Qualifier("auditDataSource") DataSource auditDataSource, Clock clock) {
        this.auditJdbcTemplate = new JdbcTemplate(auditDataSource);
        this.clock = clock;
    }

    /**
     * Appends one FAILURE row for an attempt. Returns normally in every case; a lost row is reported
     * at ERROR, never thrown.
     */
    public void recordFailure(UUID attemptId, String studentId, String courseCode,
            AuditAction action, String detail) {
        Object[] args = {
                deterministicId(attemptId),
                studentId,
                courseCode,
                action.name(),
                AuditOutcome.FAILURE.name(),
                detail,
                OffsetDateTime.ofInstant(
                        Instant.now(clock).truncatedTo(ChronoUnit.SECONDS), ZoneOffset.UTC)
        };

        if (insert(attemptId, args)) {
            log.debug("audit.record attemptId={} studentId={} course={} action={} detail={}",
                    attemptId, studentId, courseCode, action, detail);
            return;
        }

        // One retry: the first attempt failed at connection level, before the row was written.
        log.error("audit.write.retrying attemptId={} studentId={} action={} detail={}",
                attemptId, studentId, action, detail);
        if (!insert(attemptId, args)) {
            log.error("audit.write.lost attemptId={} studentId={} course={} action={} detail={}",
                    attemptId, studentId, courseCode, action, detail);
        }
    }

    private boolean insert(UUID attemptId, Object[] args) {
        try {
            auditJdbcTemplate.update(INSERT_AUDIT_ROW, args);
            return true;
        } catch (DuplicateKeyException ex) {
            // The deterministic id already exists: a previous attempt did commit the row.
            log.debug("audit.write.already_recorded attemptId={}", attemptId);
            return true;
        } catch (DataAccessException ex) {
            log.error("audit.write.failed attemptId={} reason={}", attemptId, ex.getMessage(), ex);
            return false;
        }
    }

    /**
     * A negative id in the identity column. Identity-generated ids are always positive, so the two
     * ranges cannot collide, and the same attempt always maps to the same id.
     */
    private static long deterministicId(UUID attemptId) {
        long mixed = attemptId.getMostSignificantBits()
                ^ Long.rotateLeft(attemptId.getLeastSignificantBits(), 17);
        return -(mixed & Long.MAX_VALUE) - 1L;
    }
}
