package edu.monash.registration;

import edu.monash.registration.domain.AuditAction;
import edu.monash.registration.domain.AuditLog;
import edu.monash.registration.domain.AuditOutcome;
import edu.monash.registration.domain.Course;
import edu.monash.registration.domain.Student;
import edu.monash.registration.support.BaseIntegrationTest;
import edu.monash.registration.support.TestFixtures.Slot;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** FR9 — timetable clash detection before registration. */
@DisplayName("FR9 — Timetable clash detection")
class TimetableClashValidationTest extends BaseIntegrationTest {

    private Student aisha;
    private Student daniel;
    private Course fit2004;
    private Course fit1051;

    @BeforeEach
    void seed() {
        aisha = fixtures.aisha();
        daniel = fixtures.daniel();
        // The demo pair from the requirement: overlapping Tuesday lectures.
        fit2004 = fixtures.course("FIT2004", 6, 80, 80,
                Slot.of("TUE", "10:00", "12:00", "Clayton, Building 25, Rm 125"));
        fit1051 = fixtures.course("FIT1051", 6, 100, 100,
                Slot.of("TUE", "09:00", "11:00", "Clayton, Building 25, Rm 128"));
    }

    @Test
    @DisplayName("T-FR9-01 overlapping same-day registration is rejected and both courses are named")
    void tFr9_01_overlapRejected() throws Exception {
        fixtures.registration(aisha, fit2004);
        int seatsBefore = fit1051.getAvailableSeats();

        postRegisterAuthed(bearer(aisha), fit1051.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("TIMETABLE_CLASH"))
                .andExpect(jsonPath("$.error.message").value(
                        org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString("FIT1051"),
                                org.hamcrest.Matchers.containsString("FIT2004"),
                                org.hamcrest.Matchers.containsString("TUE 10:00-12:00"))));

        assertThat(registrationRepository.count()).isEqualTo(1);
        assertThat(courseRepository.findById(fit1051.getId()).orElseThrow().getAvailableSeats())
                .isEqualTo(seatsBefore);
        assertThat(registrationRepository.existsByStudentIdAndCourseId(
                aisha.getId(), fit1051.getId())).isFalse();
    }

    @Test
    @DisplayName("T-FR9-02 different day at the same time does not clash")
    void tFr9_02_differentDayAllowed() throws Exception {
        fixtures.registration(aisha, fit2004);
        Course monday = fixtures.course("MON1001", 6, 40, 40,
                Slot.of("MON", "10:00", "12:00", "Clayton, Building 25, Rm 130"));

        postRegisterAuthed(bearer(aisha), monday.getId()).andExpect(status().isCreated());

        assertThat(registrationRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("T-FR9-03 touching boundaries are allowed; any positive overlap is rejected")
    void tFr9_03_halfOpenBoundaries() throws Exception {
        fixtures.registration(aisha, fit2004);
        Course after = fixtures.course("AFT1001", 6, 40, 40,
                Slot.of("TUE", "12:00", "14:00", "Clayton, Building 25, Rm 131"));
        Course before = fixtures.course("BEF1001", 6, 40, 40,
                Slot.of("TUE", "08:00", "10:00", "Clayton, Building 25, Rm 132"));

        postRegisterAuthed(bearer(aisha), after.getId()).andExpect(status().isCreated());
        postRegisterAuthed(bearer(aisha), before.getId()).andExpect(status().isCreated());

        Course partial = fixtures.course("PRT1001", 6, 40, 40,
                Slot.of("TUE", "11:30", "13:00", "Clayton, Building 25, Rm 133"));
        postRegisterAuthed(bearer(aisha), partial.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("TIMETABLE_CLASH"));
    }

    @Test
    @DisplayName("T-FR9-04 a single overlapping slot in a multi-slot course blocks registration")
    void tFr9_04_multiSlotCourse() throws Exception {
        Course multi = fixtures.course("MUL2001", 6, 40, 40,
                Slot.of("MON", "10:00", "12:00", "Clayton, Building 25, Rm 140"),
                Slot.of("WED", "14:00", "16:00", "Clayton, Building 25, Rm 141"));
        fixtures.registration(aisha, multi);
        Course requested = fixtures.course("REQ2001", 6, 40, 40,
                Slot.of("FRI", "09:00", "11:00", "Clayton, Building 25, Rm 142"),
                Slot.of("WED", "15:00", "17:00", "Clayton, Building 25, Rm 143"));

        String body = rawBody(postRegisterAuthed(bearer(aisha), requested.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("TIMETABLE_CLASH")));

        // Only the overlapping slot is named, not the unrelated Monday slot.
        assertThat(body).contains("WED 14:00-16:00").doesNotContain("MON 10:00-12:00");
    }

    @Test
    @DisplayName("T-FR9-05 a multi-slot course with no overlap is allowed")
    void tFr9_05_multiSlotNoOverlapAllowed() throws Exception {
        Course multi = fixtures.course("MUL2002", 6, 40, 40,
                Slot.of("MON", "10:00", "12:00", "Clayton, Building 25, Rm 140"),
                Slot.of("WED", "14:00", "16:00", "Clayton, Building 25, Rm 141"));
        fixtures.registration(aisha, multi);
        Course requested = fixtures.course("REQ2002", 6, 40, 40,
                Slot.of("MON", "12:00", "14:00", "Clayton, Building 25, Rm 142"),
                Slot.of("WED", "16:00", "18:00", "Clayton, Building 25, Rm 143"));

        postRegisterAuthed(bearer(aisha), requested.getId()).andExpect(status().isCreated());

        assertThat(registrationRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("T-FR9-06 every conflicting course is named, ordered by course code")
    void tFr9_06_allConflictsNamed() throws Exception {
        Course first = fixtures.course("AAA1001", 6, 40, 40,
                Slot.of("TUE", "10:00", "12:00", "Clayton, Building 25, Rm 150"));
        Course second = fixtures.course("ZZZ1001", 6, 40, 40,
                Slot.of("TUE", "11:00", "13:00", "Clayton, Building 25, Rm 151"));
        fixtures.registration(aisha, second);
        fixtures.registration(aisha, first);
        Course requested = fixtures.course("MID1001", 6, 40, 40,
                Slot.of("TUE", "10:30", "11:30", "Clayton, Building 25, Rm 152"));

        String body = rawBody(postRegisterAuthed(bearer(aisha), requested.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("TIMETABLE_CLASH")));

        assertThat(body.indexOf("AAA1001")).isLessThan(body.indexOf("ZZZ1001"));
        assertThat(body).contains("AAA1001").contains("ZZZ1001");
    }

    @Test
    @DisplayName("T-FR9-07 clash is evaluated only for the student's own active-semester registrations")
    void tFr9_07_scopedToStudentAndSemester() throws Exception {
        // Daniel holds the same slot; Aisha is unaffected.
        fixtures.registration(daniel, fit2004);
        postRegisterAuthed(bearer(aisha), fit1051.getId()).andExpect(status().isCreated());

        // A prior-semester registration is not part of the current timetable.
        Student prior = fixtures.student("32099999");
        Course old = fixtures.course("OLD1001", "Old Course", 6, "Dr Test", 40, 40, "2026-S1",
                "Information Technology",
                List.of(Slot.of("TUE", "10:00", "12:00", "Clayton, Building 25, Rm 160")));
        fixtures.registration(prior, old);
        postRegisterAuthed(bearer(prior), fit1051.getId()).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("T-FR9-08 clash precedes the credit-limit check")
    void tFr9_08_clashPrecedesCreditLimit() throws Exception {
        fixtures.registration(aisha, fit2004);
        for (int i = 1; i <= 3; i++) {
            Course filler = fixtures.course("FIL100" + i, 6, 40, 40,
                    Slot.of("FRI", String.format("%02d:00", 12 + i),
                            String.format("%02d:00", 13 + i),
                            "Clayton, Building 25, Rm 17" + i));
            fixtures.registration(aisha, filler);
        }
        Course requested = fixtures.course("REQ3001", 6, 40, 40,
                Slot.of("TUE", "10:30", "11:00", "Clayton, Building 25, Rm 153"));

        postRegisterAuthed(bearer(aisha), requested.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("TIMETABLE_CLASH"));
    }

    @Test
    @DisplayName("T-FR9-09 an unmet prerequisite is reported before the clash")
    void tFr9_09_prerequisitePrecedesClash() throws Exception {
        Course prerequisite = fixtures.course("PRE1001", 6, 40, 40,
                Slot.of("MON", "08:00", "09:00", "Clayton, Building 25, Rm 170"));
        Course dependent = fixtures.course("DEP2001", 6, 40, 40,
                Slot.of("TUE", "10:00", "12:00", "Clayton, Building 25, Rm 171"));
        fixtures.requires(dependent, prerequisite);
        fixtures.registration(aisha, fit2004);

        postRegisterAuthed(bearer(aisha), dependent.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PREREQUISITE_NOT_MET"));
    }

    @Test
    @DisplayName("T-FR9-10 a clash writes exactly one audit row with TIMETABLE_CLASH")
    void tFr9_10_audited() throws Exception {
        fixtures.registration(aisha, fit2004);

        postRegisterAuthed(bearer(aisha), fit1051.getId()).andExpect(status().isConflict());

        List<AuditLog> rows = auditLogRepository.findByStudentIdOrderByIdAsc("32012345");
        assertThat(rows).hasSize(1);
        AuditLog row = rows.get(0);
        assertThat(row.getAction()).isEqualTo(AuditAction.REGISTER_REJECTED);
        assertThat(row.getOutcome()).isEqualTo(AuditOutcome.FAILURE);
        assertThat(row.getDetail()).isEqualTo("TIMETABLE_CLASH");
        assertThat(row.getCourseCode()).isEqualTo("FIT1051");
    }

    @Test
    @DisplayName("T-FR9-11 two concurrent overlapping registrations admit exactly one")
    void tFr9_11_concurrentOverlap() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Integer> first = pool.submit(() -> {
                start.await();
                return postRegisterAuthed(bearer(aisha), fit2004.getId())
                        .andReturn().getResponse().getStatus();
            });
            Future<Integer> second = pool.submit(() -> {
                start.await();
                return postRegisterAuthed(bearer(aisha), fit1051.getId())
                        .andReturn().getResponse().getStatus();
            });
            start.countDown();

            int firstStatus = first.get(30, TimeUnit.SECONDS);
            int secondStatus = second.get(30, TimeUnit.SECONDS);
            assertThat(List.of(firstStatus, secondStatus).stream().sorted().toList())
                    .containsExactly(201, 409);
        } finally {
            pool.shutdownNow();
        }

        assertThat(registrationRepository.count()).isEqualTo(1);
        long held = registrationRepository.findAll().stream()
                .filter(registration -> registration.getStudent().getId().equals(aisha.getId()))
                .count();
        assertThat(held).isEqualTo(1);
    }
}
