package edu.monash.registration;

import com.fasterxml.jackson.databind.JsonNode;
import edu.monash.registration.domain.AuditAction;
import edu.monash.registration.domain.AuditLog;
import edu.monash.registration.domain.AuditOutcome;
import edu.monash.registration.domain.Course;
import edu.monash.registration.domain.Student;
import edu.monash.registration.support.BaseIntegrationTest;
import edu.monash.registration.support.BurstSupport;
import edu.monash.registration.support.MutableClock;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** NFR4 — exactly one immutable audit row per register/drop attempt, failures survive rollback. */
@DisplayName("NFR4 — Audit logging")
class AuditLogTest extends BaseIntegrationTest {

    private Student aisha;
    private Student daniel;
    private Course fit1045;

    @BeforeEach
    void seed() {
        aisha = fixtures.aisha();
        daniel = fixtures.daniel();
        fit1045 = fixtures.singleSlotCourse("FIT1045", 6, 50, 50);
    }

    @Test
    @DisplayName("T-NFR4-01 a successful register writes exactly one E-01 row")
    void tNfr4_01_registerSuccess() throws Exception {
        postRegisterAuthed(bearer(aisha), fit1045.getId()).andExpect(status().isCreated());

        List<AuditLog> rows = auditLogRepository.findByStudentIdOrderByIdAsc("32012345");
        assertThat(rows).hasSize(1);
        AuditLog row = rows.get(0);
        assertThat(row.getAction()).isEqualTo(AuditAction.REGISTER);
        assertThat(row.getOutcome()).isEqualTo(AuditOutcome.SUCCESS);
        assertThat(row.getDetail()).isNull();
        assertThat(row.getCourseCode()).isEqualTo("FIT1045");
        assertThat(row.getCreatedAt()).isEqualTo(MutableClock.currentInstant());
    }

    @Test
    @DisplayName("T-NFR4-02 a closed-window failure is committed in REQUIRES_NEW with no registration")
    void tNfr4_02_closedWindowFailureSurvives() throws Exception {
        MutableClock.setOffset("2026-12-21T00:00:00+08:00");

        postRegisterAuthed(bearer(aisha), fit1045.getId()).andExpect(status().isForbidden());

        List<AuditLog> rows = auditLogRepository.findByStudentIdOrderByIdAsc("32012345");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getAction()).isEqualTo(AuditAction.REGISTER_REJECTED);
        assertThat(rows.get(0).getOutcome()).isEqualTo(AuditOutcome.FAILURE);
        assertThat(rows.get(0).getDetail()).isEqualTo("REGISTRATION_CLOSED");
        assertThat(registrationRepository.count()).isZero();
    }

    @Test
    @DisplayName("T-NFR4-03 a duplicate produces one success row and one ALREADY_REGISTERED row")
    void tNfr4_03_duplicateRows() throws Exception {
        postRegisterAuthed(bearer(aisha), fit1045.getId()).andExpect(status().isCreated());
        postRegisterAuthed(bearer(aisha), fit1045.getId()).andExpect(status().isConflict());

        List<AuditLog> rows = auditLogRepository.findByStudentIdOrderByIdAsc("32012345");
        assertThat(rows).hasSize(2);
        assertThat(rows).filteredOn(r -> r.getOutcome() == AuditOutcome.SUCCESS).hasSize(1);
        assertThat(rows).filteredOn(r -> r.getOutcome() == AuditOutcome.FAILURE)
                .singleElement()
                .satisfies(r -> {
                    assertThat(r.getAction()).isEqualTo(AuditAction.REGISTER_REJECTED);
                    assertThat(r.getDetail()).isEqualTo("ALREADY_REGISTERED");
                });
    }

    @Test
    @DisplayName("T-NFR4-04 a full-course rejection names the course code")
    void tNfr4_04_courseFullRow() throws Exception {
        // M-4 fixture: capacity 1, seats 0, one held registration (Daniel owns the only seat).
        Course fit3182 = fixtures.seededFullCourse("FIT3182", daniel);

        postRegisterAuthed(bearer(aisha), fit3182.getId()).andExpect(status().isConflict());

        List<AuditLog> rows = auditLogRepository.findByStudentIdOrderByIdAsc("32012345");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getAction()).isEqualTo(AuditAction.REGISTER_REJECTED);
        assertThat(rows.get(0).getDetail()).isEqualTo("COURSE_FULL");
        assertThat(rows.get(0).getCourseCode()).isEqualTo("FIT3182");
        assertThat(registrationRepository.countByCourseId(fit3182.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("T-NFR4-05 a NOT_OWNER drop is audited against the caller")
    void tNfr4_05_notOwnerRow() throws Exception {
        JsonNode registration = body(postRegisterAuthed(bearer(daniel), fit1045.getId())
                .andExpect(status().isCreated()));
        String registrationId = registration.path("registrationId").asText();

        deleteAuthed(bearer(aisha), registrationId).andExpect(status().isForbidden());

        List<AuditLog> callerRows = auditLogRepository.findByStudentIdOrderByIdAsc("32012345");
        assertThat(callerRows).hasSize(1);
        assertThat(callerRows.get(0).getAction()).isEqualTo(AuditAction.DROP);
        assertThat(callerRows.get(0).getOutcome()).isEqualTo(AuditOutcome.FAILURE);
        assertThat(callerRows.get(0).getDetail()).isEqualTo("NOT_OWNER");
    }

    @Test
    @DisplayName("T-NFR4-06 a successful drop appends E-07 without touching earlier rows")
    void tNfr4_06_dropSuccessAppends() throws Exception {
        JsonNode registration = body(postRegisterAuthed(bearer(aisha), fit1045.getId())
                .andExpect(status().isCreated()));
        String registrationId = registration.path("registrationId").asText();
        long afterRegister = auditLogRepository.count();

        deleteAuthed(bearer(aisha), registrationId).andExpect(status().isOk());

        assertThat(auditLogRepository.count()).isEqualTo(afterRegister + 1);
        List<AuditLog> rows = auditLogRepository.findByStudentIdOrderByIdAsc("32012345");
        assertThat(rows).hasSize(2);
        assertThat(rows.get(1).getAction()).isEqualTo(AuditAction.DROP);
        assertThat(rows.get(1).getOutcome()).isEqualTo(AuditOutcome.SUCCESS);
    }

    @Test
    @DisplayName("T-NFR4-07 a 20-thread burst writes 10 success and 10 COURSE_FULL rows")
    void tNfr4_07_burstAudit() throws Exception {
        Course course = fixtures.singleSlotCourse("TST0020", 6, 10, 10);
        List<String> tokens = fixtures.students("95", 20).stream()
                .map(s -> fixtures.token(s.getStudentId()))
                .toList();

        var responses = BurstSupport.registerInParallel(mockMvc, tokens, course.getId());
        assertThat(BurstSupport.countStatus(responses, 201)).isEqualTo(10);
        assertThat(BurstSupport.countErrorCode(responses, "COURSE_FULL")).isEqualTo(10);

        List<AuditLog> rows = auditLogRepository.findAll();
        assertThat(rows).hasSize(20);
        assertThat(rows).filteredOn(r -> r.getAction() == AuditAction.REGISTER
                && r.getOutcome() == AuditOutcome.SUCCESS).hasSize(10);
        assertThat(rows).filteredOn(r -> r.getAction() == AuditAction.REGISTER_REJECTED
                && "COURSE_FULL".equals(r.getDetail())).hasSize(10);
        assertThat(rows).allSatisfy(r -> {
            assertThat(r.getAction()).isNotNull();
            assertThat(r.getOutcome()).isNotNull();
            assertThat(r.getCreatedAt()).isNotNull();
        });
    }

    @Test
    @DisplayName("T-NFR4-08 no API exposes audit rows")
    void tNfr4_08_noAuditEndpoint() throws Exception {
        mockMvc.perform(get("/api/audit").header("Authorization", bearer(aisha)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/audit-logs").header("Authorization", bearer(aisha)))
                .andExpect(status().isNotFound());
    }
}
