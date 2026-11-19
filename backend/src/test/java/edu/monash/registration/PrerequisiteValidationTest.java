package edu.monash.registration;

import com.fasterxml.jackson.databind.JsonNode;
import edu.monash.registration.domain.AuditAction;
import edu.monash.registration.domain.AuditLog;
import edu.monash.registration.domain.AuditOutcome;
import edu.monash.registration.domain.Course;
import edu.monash.registration.domain.Student;
import edu.monash.registration.support.BaseIntegrationTest;
import edu.monash.registration.support.TestFixtures.Slot;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** FR8 — prerequisite validation before registration. */
@DisplayName("FR8 — Prerequisite validation")
class PrerequisiteValidationTest extends BaseIntegrationTest {

    private Student aisha;
    private Student daniel;
    private Student priya;
    private Course fit1008;
    private Course fit1047;
    private Course fit2004;
    private Course fit3077;
    private Course fit1045;

    @BeforeEach
    void seed() {
        aisha = fixtures.aisha();
        daniel = fixtures.daniel();
        priya = fixtures.student("32011111", "Priya Nair", "BBIS");

        fit1008 = fixtures.course("FIT1008", 6, 80, 80,
                Slot.of("TUE", "10:00", "12:00", "Clayton, Building 25, Rm 120"));
        fit1047 = fixtures.course("FIT1047", 6, 90, 90,
                Slot.of("WED", "09:00", "11:00", "Clayton, Building 25, Rm 122"));

        fit2004 = fixtures.singleSlotCourse("FIT2004", 6, 80, 80);
        fit3077 = fixtures.singleSlotCourse("FIT3077", 6, 45, 45);
        fit1045 = fixtures.singleSlotCourse("FIT1045", 6, 120, 120);

        fixtures.requires(fit2004, fit1008);
        fixtures.requires(fit3077, fit1008, fit1047);

        // Aisha has completed FIT1008; Daniel both; Priya none.
        fixtures.completed(aisha, fit1008);
        fixtures.completed(daniel, fit1008, fit1047);
    }

    @Test
    @DisplayName("T-FR8-01 completed prerequisite allows registration")
    void tFr8_01_completedPrerequisiteAllows() throws Exception {
        postRegisterAuthed(bearer(aisha), fit2004.getId())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.course.code").value("FIT2004"))
                .andExpect(jsonPath("$.availableSeats").value(79));

        assertThat(registrationRepository.count()).isEqualTo(1);
        assertThat(courseRepository.findById(fit2004.getId()).orElseThrow().getAvailableSeats())
                .isEqualTo(79);
        assertThat(auditLogRepository.findByStudentIdOrderByIdAsc("32012345"))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.getAction()).isEqualTo(AuditAction.REGISTER);
                    assertThat(row.getOutcome()).isEqualTo(AuditOutcome.SUCCESS);
                });
    }

    @Test
    @DisplayName("T-FR8-02 missing prerequisite denies registration and names the course")
    void tFr8_02_missingPrerequisiteDenies() throws Exception {
        postRegisterAuthed(bearer(priya), fit2004.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PREREQUISITE_NOT_MET"))
                .andExpect(jsonPath("$.error.message").value(
                        org.hamcrest.Matchers.containsString("FIT1008")));

        assertThat(registrationRepository.count()).isZero();
        assertThat(courseRepository.findById(fit2004.getId()).orElseThrow().getAvailableSeats())
                .isEqualTo(80);
    }

    @Test
    @DisplayName("T-FR8-03 every missing prerequisite is listed, and completed ones are not")
    void tFr8_03_listsAllMissing() throws Exception {
        // Priya lacks both.
        postRegisterAuthed(bearer(priya), fit3077.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PREREQUISITE_NOT_MET"))
                .andExpect(jsonPath("$.error.message").value(
                        org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString("FIT1008"),
                                org.hamcrest.Matchers.containsString("FIT1047"))));

        // Aisha has FIT1008 but not FIT1047: only the missing one is named.
        postRegisterAuthed(bearer(aisha), fit3077.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PREREQUISITE_NOT_MET"))
                .andExpect(jsonPath("$.error.message").value(
                        org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString("FIT1047"),
                                org.hamcrest.Matchers.not(
                                        org.hamcrest.Matchers.containsString("FIT1008")))));
    }

    @Test
    @DisplayName("T-FR8-04 all prerequisites completed allows registration")
    void tFr8_04_allPrerequisitesCompletedAllows() throws Exception {
        postRegisterAuthed(bearer(daniel), fit3077.getId())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.course.code").value("FIT3077"));

        assertThat(registrationRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("T-FR8-05 a course without prerequisites is unaffected")
    void tFr8_05_noPrerequisitesUnaffected() throws Exception {
        postRegisterAuthed(bearer(priya), fit1045.getId())
                .andExpect(status().isCreated());

        assertThat(registrationRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("T-FR8-06 denial is audited with PREREQUISITE_NOT_MET and course_code")
    void tFr8_06_denialAudited() throws Exception {
        postRegisterAuthed(bearer(priya), fit2004.getId())
                .andExpect(status().isConflict());

        List<AuditLog> rows = auditLogRepository.findByStudentIdOrderByIdAsc("32011111");
        assertThat(rows).hasSize(1);
        AuditLog row = rows.get(0);
        assertThat(row.getAction()).isEqualTo(AuditAction.REGISTER_REJECTED);
        assertThat(row.getOutcome()).isEqualTo(AuditOutcome.FAILURE);
        assertThat(row.getDetail()).isEqualTo("PREREQUISITE_NOT_MET");
        assertThat(row.getCourseCode()).isEqualTo("FIT2004");
    }

    @Test
    @DisplayName("T-FR8-07 prerequisite check precedes capacity and credit checks")
    void tFr8_07_prerequisitePrecedesCapacityAndCredit() throws Exception {
        Course fullWithPrerequisite = fixtures.singleSlotCourse("CSE3999", 6, 1, 0);
        fixtures.requires(fullWithPrerequisite, fit1008);

        postRegisterAuthed(bearer(priya), fullWithPrerequisite.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PREREQUISITE_NOT_MET"));

        // Make Priya exceed the credit cap with four 6cp courses she can hold directly.
        for (int i = 1; i <= 4; i++) {
            Course filler = fixtures.singleSlotCourse("FIL000" + i, 6, 50, 50);
            fixtures.registration(priya, filler);
        }
        postRegisterAuthed(bearer(priya), fit2004.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PREREQUISITE_NOT_MET"));
    }

    @Test
    @DisplayName("T-FR8-08 duplicate registration takes precedence over the prerequisite check")
    void tFr8_08_duplicatePrecedesPrerequisite() throws Exception {
        // A direct row simulates an existing registration; the prerequisite edge is unmet.
        fixtures.registration(priya, fit2004);

        postRegisterAuthed(bearer(priya), fit2004.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ALREADY_REGISTERED"));
    }

    @Test
    @DisplayName("T-FR8-09 course payload exposes prerequisites ordered by code")
    void tFr8_09_coursePayloadExposesPrerequisites() throws Exception {
        JsonNode detail = body(getAuthed("/api/courses/" + fit3077.getId(), bearer(priya))
                .andExpect(status().isOk()));

        JsonNode prerequisites = detail.path("prerequisites");
        assertThat(prerequisites.size()).isEqualTo(2);
        assertThat(prerequisites.get(0).path("code").asText()).isEqualTo("FIT1008");
        assertThat(prerequisites.get(1).path("code").asText()).isEqualTo("FIT1047");
        assertThat(prerequisites.get(0).path("name").asText())
                .isEqualTo("Course FIT1008");
        assertThat(prerequisites.get(0).path("id").asText()).isEqualTo(fit1008.getId().toString());

        JsonNode catalogue = body(getAuthed("/api/courses?q=FIT2004", bearer(priya))
                .andExpect(status().isOk()));
        assertThat(catalogue.get(0).path("prerequisites").size()).isEqualTo(1);
    }

    @Test
    @DisplayName("T-FR8-10 a current-semester registration never counts as completion")
    void tFr8_10_registrationIsNotCompletion() throws Exception {
        Course pre1001 = fixtures.singleSlotCourse("PRE1001", 6, 20, 20);
        Course adv2001 = fixtures.singleSlotCourse("ADV2001", 6, 20, 20);
        fixtures.requires(adv2001, pre1001);

        postRegisterAuthed(bearer(priya), pre1001.getId()).andExpect(status().isCreated());

        postRegisterAuthed(bearer(priya), adv2001.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PREREQUISITE_NOT_MET"))
                .andExpect(jsonPath("$.error.message").value(
                        org.hamcrest.Matchers.containsString("PRE1001")));
    }
}
