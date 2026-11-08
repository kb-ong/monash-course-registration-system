package edu.monash.registration;

import com.zaxxer.hikari.HikariDataSource;
import edu.monash.registration.domain.AuditAction;
import edu.monash.registration.domain.AuditLog;
import edu.monash.registration.domain.AuditOutcome;
import edu.monash.registration.domain.Course;
import edu.monash.registration.domain.Student;
import edu.monash.registration.support.BaseIntegrationTest;
import edu.monash.registration.support.BurstSupport;
import edu.monash.registration.support.BurstSupport.Response;
import edu.monash.registration.support.BurstSupport.TimedResponse;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression for review finding B-2: the failure-path audit is now published as an application
 * event and persisted by an {@code AFTER_COMPLETION} listener, so it never opens a nested
 * transaction while the business transaction still holds a course row lock and a pooled
 * connection.
 *
 * <p>These tests deliberately run at the <em>shipped</em> pool size (20). The old suite hid the
 * defect behind a test-only pool of 40; a green result here is evidence about the artifact that
 * ships.
 */
@DisplayName("REGRESSION B-2 — audit survivability at the shipped pool size")
class RegressionAuditPoolTest extends BaseIntegrationTest {

    private static final int SHIPPED_POOL_SIZE = 20;
    private static final int SHIPPED_AUDIT_POOL_SIZE = 5;

    @Autowired
    private DataSource dataSource;

    @Autowired
    @Qualifier("auditDataSource")
    private DataSource auditDataSource;

    @Test
    @DisplayName("T-B2-01 25 concurrent registrations on 10 seats at pool 20: 10x201, 15x409, zero 5xx, 25 audit rows")
    void tB2_01_auditAndPoolUnderLoad() throws Exception {
        assertThat(dataSource).isInstanceOf(HikariDataSource.class);
        assertThat(((HikariDataSource) dataSource).getMaximumPoolSize())
                .as("regression must run at the shipped pool size, not a test-only 40")
                .isEqualTo(SHIPPED_POOL_SIZE);

        Course course = fixtures.singleSlotCourse("TST2500", 6, 10, 10);
        List<Student> students = fixtures.students("77", 25);
        List<String> tokens = students.stream()
                .map(s -> fixtures.token(s.getStudentId()))
                .toList();

        List<Response> responses = BurstSupport.registerInParallel(mockMvc, tokens, course.getId());

        assertThat(BurstSupport.countStatus(responses, 201)).isEqualTo(10);
        assertThat(BurstSupport.countStatus(responses, 409)).isEqualTo(15);
        assertThat(BurstSupport.countErrorCode(responses, "COURSE_FULL")).isEqualTo(15);
        assertThat(responses.stream().map(Response::status)
                .filter(status -> status != 201 && status != 409))
                .as("zero 5xx (or any other status) - pool starvation used to produce 500s")
                .isEmpty();

        Course after = courseRepository.findById(course.getId()).orElseThrow();
        assertThat(after.getAvailableSeats()).isZero();
        assertThat(registrationRepository.countByCourseId(course.getId())).isEqualTo(10);
        assertThat(after.getAvailableSeats()
                + registrationRepository.countByCourseId(course.getId()))
                .as("availableSeats + registrations == capacity")
                .isEqualTo(10);

        List<AuditLog> rows = auditLogRepository.findAll();
        assertThat(rows).as("one audit row per attempt, no losses and no duplicates").hasSize(25);
        assertThat(rows).filteredOn(row -> row.getAction() == AuditAction.REGISTER
                && row.getOutcome() == AuditOutcome.SUCCESS).hasSize(10);
        assertThat(rows).filteredOn(row -> row.getAction() == AuditAction.REGISTER_REJECTED
                && row.getOutcome() == AuditOutcome.FAILURE
                && "COURSE_FULL".equals(row.getDetail())).hasSize(15);
        assertThat(rows.stream().map(AuditLog::getStudentId).distinct().count())
                .as("exactly one row per distinct student")
                .isEqualTo(25);
    }

    @Test
    @DisplayName("T-B2-03 all-failure burst on a genuinely full course: 25x409, zero 5xx, 25 audit rows, no slow request")
    void tB2_03_allFailureBurstOnFullCourse() throws Exception {
        assertThat(dataSource).isInstanceOf(HikariDataSource.class);
        assertThat(((HikariDataSource) dataSource).getMaximumPoolSize())
                .as("the probe must run on the shipped business pool, not a test-only inflation")
                .isEqualTo(SHIPPED_POOL_SIZE);
        assertThat(auditDataSource).isInstanceOf(HikariDataSource.class);
        assertThat(((HikariDataSource) auditDataSource).getMaximumPoolSize())
                .as("the probe must run on the shipped audit pool")
                .isEqualTo(SHIPPED_AUDIT_POOL_SIZE);

        Student holder = fixtures.daniel();
        Student contender = fixtures.aisha();
        // The real full-course shape: FIT3182 is capacity 1, seats 0, and the only seat is held by
        // an actual registration, so every contender is rejected on capacity, never on a duplicate.
        Course full = fixtures.seededFullCourse("FIT3182", holder);

        long auditsBefore = auditLogRepository.count();
        assertThat(auditsBefore).as("the probe starts from a clean audit table").isZero();

        // One student, 25 simultaneous attempts. No request can succeed, so the burst is a pure
        // all-failure load: every request keeps its business connection until afterCompletion and
        // then needs an audit connection. This is the exact shape that produced 500s before B-2.
        List<String> tokens = java.util.Collections.nCopies(25, fixtures.token(contender.getStudentId()));

        List<TimedResponse> responses = BurstSupport.registerInParallelTimed(mockMvc, tokens,
                full.getId());

        // --- status distribution: prove the burst was genuinely all-failure and never vacuous.
        assertThat(responses).hasSize(25);
        assertThat(BurstSupport.countStatus(responses, 409)).isEqualTo(25);
        assertThat(BurstSupport.countStatus(responses, 201)).isZero();
        assertThat(responses.stream().map(TimedResponse::status).distinct().toList())
                .as("every single response is a 409 - no 5xx, no 200, no other status")
                .containsExactly(409);
        assertThat(responses.stream().filter(r -> "COURSE_FULL".equals(r.errorCode())).count())
                .isEqualTo(25);

        // --- latency: queuing behind the row lock is fine, being starved of a connection is not.
        long slowestMillis = responses.stream().mapToLong(TimedResponse::durationMillis).max()
                .orElseThrow();
        List<Long> sortedDurations = responses.stream().map(TimedResponse::durationMillis)
                .sorted().toList();
        System.out.printf("[T-B2-03] requests=%d slowest=%dms median=%dms%n", responses.size(),
                slowestMillis, sortedDurations.get(sortedDurations.size() / 2));
        assertThat(slowestMillis)
                .as("no request may take longer than 2s (slowest was %s ms)", slowestMillis)
                .isLessThanOrEqualTo(2_000L);

        // --- database truth: the rejected burst changed nothing.
        Course after = courseRepository.findById(full.getId()).orElseThrow();
        assertThat(after.getCapacity()).isEqualTo(1);
        assertThat(after.getAvailableSeats()).isZero();
        assertThat(registrationRepository.countByCourseId(full.getId()))
                .as("only the holder's registration exists")
                .isEqualTo(1);
        assertThat(registrationRepository.existsByStudentIdAndCourseId(
                holder.getId(), full.getId())).isTrue();
        assertThat(registrationRepository.existsByStudentIdAndCourseId(
                contender.getId(), full.getId())).isFalse();
        assertThat(after.getAvailableSeats()
                + registrationRepository.countByCourseId(full.getId()))
                .as("availableSeats + registrations == capacity")
                .isEqualTo(1);

        // --- audit truth: exactly one row per attempt, all the same rejection, no duplicates.
        List<AuditLog> rows = auditLogRepository.findAll();
        assertThat(rows)
                .as("exactly 25 new audit rows - none lost, none duplicated")
                .hasSize(25);
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.getStudentId()).isEqualTo("32012345");
            assertThat(row.getCourseCode()).isEqualTo("FIT3182");
            assertThat(row.getAction()).isEqualTo(AuditAction.REGISTER_REJECTED);
            assertThat(row.getOutcome()).isEqualTo(AuditOutcome.FAILURE);
            assertThat(row.getDetail()).isEqualTo("COURSE_FULL");
        });
        assertThat(rows.stream().map(AuditLog::getId).distinct().count())
                .as("25 distinct audit row ids")
                .isEqualTo(25);
        assertThat(auditLogRepository.count() - auditsBefore).isEqualTo(25);
    }

    @Test
    @DisplayName("T-B2-02 a rejected attempt survives rollback: no registration, exactly one audit row")
    void tB2_02_failureAuditSurvivesRollback() throws Exception {
        Student aisha = fixtures.aisha();
        Student daniel = fixtures.daniel();
        Course full = fixtures.seededFullCourse("FIT3182", daniel);

        postRegisterAuthed(bearer(aisha), full.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("COURSE_FULL"));

        // The rejected attempt leaves no registration behind: only the holder's row exists.
        assertThat(registrationRepository.countByCourseId(full.getId())).isEqualTo(1);
        assertThat(registrationRepository.existsByStudentIdAndCourseId(
                aisha.getId(), full.getId())).isFalse();
        assertThat(courseRepository.findById(full.getId()).orElseThrow().getAvailableSeats())
                .isZero();

        List<AuditLog> rows = auditLogRepository.findAll();
        assertThat(rows).hasSize(1);
        AuditLog row = rows.get(0);
        assertThat(row.getStudentId()).isEqualTo("32012345");
        assertThat(row.getAction()).isEqualTo(AuditAction.REGISTER_REJECTED);
        assertThat(row.getOutcome()).isEqualTo(AuditOutcome.FAILURE);
        assertThat(row.getDetail()).isEqualTo("COURSE_FULL");
        assertThat(row.getCourseCode()).isEqualTo("FIT3182");
    }
}
