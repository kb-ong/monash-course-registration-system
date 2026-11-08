package edu.monash.registration;

import edu.monash.registration.domain.AuditAction;
import edu.monash.registration.domain.AuditLog;
import edu.monash.registration.domain.AuditOutcome;
import edu.monash.registration.domain.Course;
import edu.monash.registration.domain.Student;
import edu.monash.registration.support.BaseIntegrationTest;
import edu.monash.registration.support.TestFixtures;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression for review finding B-1: the write path must be scoped to the active semester, exactly
 * like the catalogue read path. A course that exists in a prior semester must be invisible to
 * registration — no row, no seat change, one audited rejection.
 */
@DisplayName("REGRESSION B-1 — registration is scoped to the active semester")
class RegressionSemesterScopeTest extends BaseIntegrationTest {

    private Student aisha;
    private Course priorSemesterCourse;

    @BeforeEach
    void seed() {
        aisha = fixtures.aisha();
        priorSemesterCourse = fixtures.course("FIT1008", "Introduction to Computer Science", 6,
                "Dr Alan Ng", 80, 80, TestFixtures.S1, "Information Technology",
                List.of(TestFixtures.Slot.of("TUE", "10:00", "12:00",
                        "Clayton, Building 25, Rm 120")));
    }

    @Test
    @DisplayName("T-B1-01 POST a 2026-S1 course id while 2026-S2 is active -> 404, no row, no seat, SEMESTER_NOT_ACTIVE audit")
    void tB1_01_crossSemesterRejected() throws Exception {
        // Precondition: the course genuinely exists (otherwise this would prove nothing but 404).
        assertThat(courseRepository.findById(priorSemesterCourse.getId())).isPresent();
        assertThat(courseRepository.findById(priorSemesterCourse.getId()).orElseThrow().getSemester())
                .isEqualTo("2026-S1");

        postRegisterAuthed(bearer(aisha), priorSemesterCourse.getId())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("COURSE_NOT_FOUND"));

        Course after = courseRepository.findById(priorSemesterCourse.getId()).orElseThrow();
        assertThat(after.getAvailableSeats())
                .as("a prior-semester seat is never consumed")
                .isEqualTo(80);
        assertThat(registrationRepository.count()).as("no registration row is created").isZero();
        assertThat(registrationRepository.countByCourseId(priorSemesterCourse.getId())).isZero();
        assertThat(registrationRepository.existsByStudentIdAndCourseId(
                aisha.getId(), priorSemesterCourse.getId())).isFalse();

        List<AuditLog> rows = auditLogRepository.findByStudentIdOrderByIdAsc("32012345");
        assertThat(rows).hasSize(1);
        AuditLog row = rows.get(0);
        assertThat(row.getAction()).isEqualTo(AuditAction.REGISTER_REJECTED);
        assertThat(row.getOutcome()).isEqualTo(AuditOutcome.FAILURE);
        assertThat(row.getDetail()).isEqualTo("SEMESTER_NOT_ACTIVE");
        assertThat(row.getCourseCode()).isEqualTo("FIT1008");
    }

    @Test
    @DisplayName("T-B1-01b the same POST is still 404 on a retry and still creates no second audit row per attempt")
    void tB1_01b_crossSemesterRetryLeavesOneRowPerAttempt() throws Exception {
        postRegisterAuthed(bearer(aisha), priorSemesterCourse.getId())
                .andExpect(status().isNotFound());
        postRegisterAuthed(bearer(aisha), priorSemesterCourse.getId())
                .andExpect(status().isNotFound());

        assertThat(registrationRepository.count()).isZero();
        assertThat(courseRepository.findById(priorSemesterCourse.getId()).orElseThrow()
                .getAvailableSeats()).isEqualTo(80);
        assertThat(auditLogRepository.findAll())
                .hasSize(2)
                .allSatisfy(row -> {
                    assertThat(row.getAction()).isEqualTo(AuditAction.REGISTER_REJECTED);
                    assertThat(row.getOutcome()).isEqualTo(AuditOutcome.FAILURE);
                    assertThat(row.getDetail()).isEqualTo("SEMESTER_NOT_ACTIVE");
                });
    }
}
