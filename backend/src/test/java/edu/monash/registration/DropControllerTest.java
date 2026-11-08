package edu.monash.registration;

import com.fasterxml.jackson.databind.JsonNode;
import edu.monash.registration.domain.AuditAction;
import edu.monash.registration.domain.Course;
import edu.monash.registration.domain.Registration;
import edu.monash.registration.domain.Student;
import edu.monash.registration.support.BaseIntegrationTest;
import edu.monash.registration.support.MutableClock;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** FR4 — drop semantics: ownership, period, seat release and audit. */
@DisplayName("FR4 — Drop course")
class DropControllerTest extends BaseIntegrationTest {

    private Student aisha;
    private Student daniel;
    private Course fit1045;

    @BeforeEach
    void seed() {
        aisha = fixtures.aisha();
        daniel = fixtures.daniel();
        fit1045 = fixtures.singleSlotCourse("FIT1045", 6, 120, 120);
    }

    @Test
    @DisplayName("T-FR4-01 own registration drops with 200 and one seat returned")
    void tFr4_01_happyPath() throws Exception {
        String regId = register(aisha, fit1045);

        deleteAuthed(bearer(aisha), regId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableSeats").value(120))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("FIT1045")));

        assertThat(registrationRepository.count()).isZero();
        assertThat(courseRepository.findById(fit1045.getId()).orElseThrow().getAvailableSeats())
                .isEqualTo(120);
    }

    @Test
    @DisplayName("T-FR4-02 after a drop the list no longer contains the course")
    void tFr4_02_removedFromList() throws Exception {
        String regId = register(aisha, fit1045);
        deleteAuthed(bearer(aisha), regId).andExpect(status().isOk());

        JsonNode list = body(getAuthed("/api/registrations", bearer(aisha)));
        assertThat(list.path("items").size()).isZero();
        assertThat(list.path("totalCredits").asInt()).isZero();
    }

    @Test
    @DisplayName("T-FR4-03 unknown registration id is 404 and audited")
    void tFr4_03_unknownRegistration() throws Exception {
        UUID id = UUID.randomUUID();
        deleteAuthed(bearer(aisha), id.toString())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("REGISTRATION_NOT_FOUND"));

        List<Registration> rows = registrationRepository.findAll();
        assertThat(rows).isEmpty();
        assertThat(auditLogRepository.findByStudentIdOrderByIdAsc("32012345"))
                .anyMatch(row -> row.getAction() == AuditAction.DROP
                        && "REGISTRATION_NOT_FOUND".equals(row.getDetail()));
    }

    @Test
    @DisplayName("T-FR4-04 another student's registration is 403 NOT_OWNER with no data leak")
    void tFr4_04_notOwner() throws Exception {
        String regId = register(daniel, fit1045);

        String body = rawBody(deleteAuthed(bearer(aisha), regId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("NOT_OWNER")));

        assertThat(body).doesNotContain("FIT").doesNotContain("Introduction");
        assertThat(registrationRepository.count()).isEqualTo(1);
        assertThat(courseRepository.findById(fit1045.getId()).orElseThrow().getAvailableSeats())
                .isEqualTo(119);
    }

    @Test
    @DisplayName("T-FR4-05 closed window blocks drops and leaves the row untouched")
    void tFr4_05_closedWindow() throws Exception {
        String regId = register(aisha, fit1045);
        MutableClock.setOffset("2026-12-21T00:00:00+08:00");

        deleteAuthed(bearer(aisha), regId)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("REGISTRATION_CLOSED"));

        assertThat(registrationRepository.count()).isEqualTo(1);
        assertThat(courseRepository.findById(fit1045.getId()).orElseThrow().getAvailableSeats())
                .isEqualTo(119);
        assertThat(auditLogRepository.findByStudentIdOrderByIdAsc("32012345"))
                .anyMatch(row -> row.getAction() == AuditAction.DROP
                        && row.getOutcome() == edu.monash.registration.domain.AuditOutcome.FAILURE
                        && "REGISTRATION_CLOSED".equals(row.getDetail()));
    }

    @Test
    @DisplayName("T-FR4-06 dropping twice returns 200 then 404 and releases one seat total")
    void tFr4_06_doubleDrop() throws Exception {
        String regId = register(aisha, fit1045);

        deleteAuthed(bearer(aisha), regId).andExpect(status().isOk());
        deleteAuthed(bearer(aisha), regId)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("REGISTRATION_NOT_FOUND"));

        assertThat(courseRepository.findById(fit1045.getId()).orElseThrow().getAvailableSeats())
                .isEqualTo(120);
    }

    @Test
    @DisplayName("T-FR4-07 / T-FR6-09 a drop never pushes availableSeats above capacity")
    void tFr4_07_clampedAtCapacity() throws Exception {
        Course small = fixtures.singleSlotCourse("TST0005", 6, 5, 5);
        String regId = register(aisha, small);
        assertThat(courseRepository.findById(small.getId()).orElseThrow().getAvailableSeats())
                .isEqualTo(4);

        // Simulate a course already back at capacity while a registration still exists.
        Course current = courseRepository.findById(small.getId()).orElseThrow();
        current.setAvailableSeats(5);
        courseRepository.saveAndFlush(current);

        deleteAuthed(bearer(aisha), regId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableSeats").value(5));

        assertThat(courseRepository.findById(small.getId()).orElseThrow().getAvailableSeats())
                .isEqualTo(5);
    }

    @Test
    @DisplayName("T-FR4-09 no token is 401, the row survives and no DROP audit is written")
    void tFr4_09_unauthenticated() throws Exception {
        String regId = register(aisha, fit1045);

        mockMvc.perform(delete("/api/registrations/" + regId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        assertThat(registrationRepository.count()).isEqualTo(1);
        assertThat(auditLogRepository.findByStudentIdOrderByIdAsc("32012345"))
                .noneMatch(row -> row.getAction() == AuditAction.DROP);
    }

    @Test
    @DisplayName("T-FR4-10 malformed registration id is 404, not 400 or 500")
    void tFr4_10_malformedId() throws Exception {
        deleteAuthed(bearer(aisha), "abc")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("REGISTRATION_NOT_FOUND"));
    }

    private String register(Student student, Course course) throws Exception {
        JsonNode response = body(postRegisterAuthed(bearer(student), course.getId())
                .andExpect(status().isCreated()));
        return response.path("registrationId").asText();
    }
}
