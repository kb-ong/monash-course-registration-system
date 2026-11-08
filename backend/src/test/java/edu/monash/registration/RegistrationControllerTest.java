package edu.monash.registration;

import com.fasterxml.jackson.databind.JsonNode;
import edu.monash.registration.domain.AuditAction;
import edu.monash.registration.domain.AuditLog;
import edu.monash.registration.domain.AuditOutcome;
import edu.monash.registration.domain.Course;
import edu.monash.registration.domain.Registration;
import edu.monash.registration.domain.Student;
import edu.monash.registration.support.BaseIntegrationTest;
import edu.monash.registration.support.MutableClock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** FR3 — registration decisions, ordering, idempotency and audit evidence. */
@DisplayName("FR3 — Register for course")
class RegistrationControllerTest extends BaseIntegrationTest {

    private Student aisha;
    private Student daniel;
    private Course fit1045;
    private Course fit2004;
    private Course tst0001;
    private Course tst0002;
    private Course tst0003;
    private Course fit3143;

    @BeforeEach
    void seed() {
        aisha = fixtures.aisha();
        daniel = fixtures.daniel();
        fit1045 = fixtures.singleSlotCourse("FIT1045", 6, 120, 120);
        fit2004 = fixtures.singleSlotCourse("FIT2004", 6, 80, 80);
        tst0001 = fixtures.singleSlotCourse("TST0001", 6, 50, 50);
        tst0002 = fixtures.singleSlotCourse("TST0002", 6, 50, 50);
        tst0003 = fixtures.singleSlotCourse("TST0003", 3, 50, 50);
        fit3143 = fixtures.singleSlotCourse("FIT3143", 6, 1, 1);
    }

    @Test
    @DisplayName("T-FR3-01 happy path returns 201, decrements one seat and persists one row")
    void tFr3_01_happyPath() throws Exception {
        MvcResult result = postRegisterAuthed(bearer(aisha), fit1045.getId())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.registrationId").isNotEmpty())
                .andExpect(jsonPath("$.course.code").value("FIT1045"))
                .andExpect(jsonPath("$.availableSeats").value(119))
                .andExpect(jsonPath("$.course.availableSeats").value(119))
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("FIT1045")))
                .andReturn();

        UUID registrationId = UUID.fromString(
                objectMapper.readTree(result.getResponse().getContentAsString())
                        .path("registrationId").asText());
        assertThat(registrationRepository.findById(registrationId)).isPresent();
        assertThat(courseRepository.findById(fit1045.getId()).orElseThrow().getAvailableSeats())
                .isEqualTo(119);
        assertThat(registrationRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("T-FR3-02 the new registration is visible through the read endpoint")
    void tFr3_02_persisted() throws Exception {
        postRegisterAuthed(bearer(aisha), fit1045.getId()).andExpect(status().isCreated());

        JsonNode list = body(getAuthed("/api/registrations", bearer(aisha))
                .andExpect(status().isOk()));
        assertThat(list.path("items").size()).isEqualTo(1);
        assertThat(list.path("items").get(0).path("course").path("code").asText())
                .isEqualTo("FIT1045");
        assertThat(list.path("totalCredits").asInt()).isEqualTo(6);
    }

    @Test
    @DisplayName("T-FR3-03 unauthenticated registration is 401 with no writes at all")
    void tFr3_03_unauthenticated() throws Exception {
        mockMvc.perform(post("/api/registrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":\"" + fit1045.getId() + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));

        assertThat(registrationRepository.count()).isZero();
        assertThat(auditLogRepository.count()).isZero();
        assertThat(courseRepository.findById(fit1045.getId()).orElseThrow().getAvailableSeats())
                .isEqualTo(120);
    }

    @Test
    @DisplayName("T-FR3-04 closed window is 403 REGISTRATION_CLOSED, no seat change, audit written")
    void tFr3_04_closedWindow() throws Exception {
        MutableClock.setOffset("2026-12-21T00:00:00+08:00");

        postRegisterAuthed(bearer(aisha), fit1045.getId())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("REGISTRATION_CLOSED"));

        assertThat(registrationRepository.count()).isZero();
        assertThat(courseRepository.findById(fit1045.getId()).orElseThrow().getAvailableSeats())
                .isEqualTo(120);
        List<AuditLog> rows = auditLogRepository.findByStudentIdOrderByIdAsc("32012345");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getAction()).isEqualTo(AuditAction.REGISTER_REJECTED);
        assertThat(rows.get(0).getOutcome()).isEqualTo(AuditOutcome.FAILURE);
        assertThat(rows.get(0).getDetail()).isEqualTo("REGISTRATION_CLOSED");
    }

    @Test
    @DisplayName("T-FR3-05 malformed courseId is 400 and the message names the field")
    void tFr3_05_malformedCourseId() throws Exception {
        mockMvc.perform(post("/api/registrations")
                        .header("Authorization", bearer(aisha))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":\"not-a-uuid\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.message").value(
                        org.hamcrest.Matchers.containsString("courseId")));
    }

    @Test
    @DisplayName("T-FR3-06 unknown course UUID is 404 and audited as COURSE_NOT_FOUND")
    void tFr3_06_unknownCourse() throws Exception {
        postRegisterAuthed(bearer(aisha), UUID.randomUUID())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("COURSE_NOT_FOUND"));

        List<AuditLog> rows = auditLogRepository.findByStudentIdOrderByIdAsc("32012345");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getAction()).isEqualTo(AuditAction.REGISTER_REJECTED);
        assertThat(rows.get(0).getDetail()).isEqualTo("COURSE_NOT_FOUND");
        assertThat(rows.get(0).getCourseCode()).isNull();
    }

    @Test
    @DisplayName("T-FR3-07 duplicate registration is 409 and leaves one row and 119 seats")
    void tFr3_07_duplicate() throws Exception {
        postRegisterAuthed(bearer(aisha), fit1045.getId()).andExpect(status().isCreated());

        postRegisterAuthed(bearer(aisha), fit1045.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ALREADY_REGISTERED"));

        assertThat(registrationRepository.count()).isEqualTo(1);
        assertThat(courseRepository.findById(fit1045.getId()).orElseThrow().getAvailableSeats())
                .isEqualTo(119);
    }

    @Test
    @DisplayName("T-FR3-08 landing exactly on 24 credits is allowed")
    void tFr3_08_exactlyTwentyFourCredits() throws Exception {
        postRegisterAuthed(bearer(aisha), fit1045.getId()).andExpect(status().isCreated());
        postRegisterAuthed(bearer(aisha), fit2004.getId()).andExpect(status().isCreated());
        postRegisterAuthed(bearer(aisha), tst0001.getId()).andExpect(status().isCreated());

        postRegisterAuthed(bearer(aisha), tst0002.getId())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.course.code").value("TST0002"));

        JsonNode list = body(getAuthed("/api/registrations", bearer(aisha)));
        assertThat(list.path("totalCredits").asInt()).isEqualTo(24);
        assertThat(registrationRepository.count()).isEqualTo(4);
    }

    @Test
    @DisplayName("T-FR3-09 exceeding 24 credits is 409 with no row and no seat change")
    void tFr3_09_creditLimit() throws Exception {
        postRegisterAuthed(bearer(aisha), fit1045.getId()).andExpect(status().isCreated());
        postRegisterAuthed(bearer(aisha), fit2004.getId()).andExpect(status().isCreated());
        postRegisterAuthed(bearer(aisha), tst0001.getId()).andExpect(status().isCreated());
        postRegisterAuthed(bearer(aisha), tst0003.getId()).andExpect(status().isCreated());

        postRegisterAuthed(bearer(aisha), tst0002.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CREDIT_LIMIT_EXCEEDED"));

        assertThat(registrationRepository.count()).isEqualTo(4);
        assertThat(courseRepository.findById(tst0002.getId()).orElseThrow().getAvailableSeats())
                .isEqualTo(50);
        assertThat(auditLogRepository.findByStudentIdOrderByIdAsc("32012345"))
                .anyMatch(row -> row.getAction() == AuditAction.REGISTER_REJECTED
                        && "CREDIT_LIMIT_EXCEEDED".equals(row.getDetail()));
    }

    @Test
    @DisplayName("T-FR3-10 the seeded-full course is 409 COURSE_FULL with zero seats")
    void tFr3_10_seededFull() throws Exception {
        // M-4 fixture: capacity 1, seats 0, the only seat held by Daniel's real registration.
        Course fit3182 = fixtures.seededFullCourse("FIT3182", daniel);

        postRegisterAuthed(bearer(aisha), fit3182.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("COURSE_FULL"));

        assertThat(registrationRepository.countByCourseId(fit3182.getId())).isEqualTo(1);
        assertThat(registrationRepository.existsByStudentIdAndCourseId(
                aisha.getId(), fit3182.getId())).isFalse();
        assertThat(courseRepository.findById(fit3182.getId()).orElseThrow().getAvailableSeats())
                .isZero();
        assertThat(auditLogRepository.findByStudentIdOrderByIdAsc("32012345").get(0).getDetail())
                .isEqualTo("COURSE_FULL");
    }

    @Test
    @DisplayName("T-FR3-12 studentId in the body is ignored; the token subject owns the row")
    void tFr3_12_bodyStudentIdIgnored() throws Exception {
        mockMvc.perform(post("/api/registrations")
                        .header("Authorization", bearer(aisha))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":\"" + fit1045.getId()
                                + "\",\"studentId\":\"32067890\"}"))
                .andExpect(status().isCreated());

        assertThat(registrationRepository.count()).isEqualTo(1);
        assertThat(registrationRepository.existsByStudentIdAndCourseId(
                aisha.getId(), fit1045.getId())).isTrue();
        assertThat(registrationRepository.existsByStudentIdAndCourseId(
                daniel.getId(), fit1045.getId())).isFalse();
        assertThat(auditLogRepository.findByStudentIdOrderByIdAsc("32067890")).isEmpty();
    }

    @Test
    @DisplayName("T-FR3-13 a retried POST is a 409, never a second row")
    void tFr3_13_idempotentRetry() throws Exception {
        postRegisterAuthed(bearer(aisha), fit2004.getId()).andExpect(status().isCreated());
        postRegisterAuthed(bearer(aisha), fit2004.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ALREADY_REGISTERED"));

        assertThat(registrationRepository.count()).isEqualTo(1);
        assertThat(courseRepository.findById(fit2004.getId()).orElseThrow().getAvailableSeats())
                .isEqualTo(79);
    }

    @Test
    @DisplayName("T-FR3-11 a capacity-1 course admits exactly one of five concurrent students")
    void tFr3_11_singleSeatConcurrency() throws Exception {
        // Full coverage lives in CapacityConcurrencyTest; this is the FR3 view of the same race.
        List<String> tokens = fixtures.students("99", 5).stream()
                .map(s -> fixtures.token(s.getStudentId()))
                .toList();
        var responses = edu.monash.registration.support.BurstSupport
                .registerInParallel(mockMvc, tokens, fit3143.getId());

        assertThat(edu.monash.registration.support.BurstSupport.countStatus(responses, 201))
                .isEqualTo(1);
        assertThat(edu.monash.registration.support.BurstSupport
                .countErrorCode(responses, "COURSE_FULL")).isEqualTo(4);
        assertThat(registrationRepository.count()).isEqualTo(1);
        assertThat(courseRepository.findById(fit3143.getId()).orElseThrow().getAvailableSeats())
                .isZero();
    }

    @Test
    @DisplayName("T-FR3-14 registration timestamps come from the server clock")
    void tFr3_14_serverTimestamp() throws Exception {
        MutableClock.setOffset("2026-11-08T12:34:56+08:00");
        postRegisterAuthed(bearer(aisha), fit1045.getId()).andExpect(status().isCreated());

        Registration row = registrationRepository.findAll().get(0);
        assertThat(row.getRegisteredAt())
                .isEqualTo(OffsetDateTime.parse("2026-11-08T12:34:56+08:00").toInstant());
    }
}
