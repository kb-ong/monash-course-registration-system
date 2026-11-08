package edu.monash.registration;

import com.fasterxml.jackson.databind.JsonNode;
import edu.monash.registration.domain.AuditAction;
import edu.monash.registration.domain.Course;
import edu.monash.registration.domain.Student;
import edu.monash.registration.support.BaseIntegrationTest;
import edu.monash.registration.support.MutableClock;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** FR7 — server-authoritative registration window with inclusive boundaries. */
@DisplayName("FR7 — Registration period")
class RegistrationPeriodTest extends BaseIntegrationTest {

    private static final String START = "2026-10-01T00:00:00+08:00";
    private static final String END = "2026-12-20T23:59:59+08:00";

    private Student aisha;
    private Course course;

    @BeforeEach
    void seed() {
        aisha = fixtures.aisha();
        course = fixtures.singleSlotCourse("TST0001", 6, 50, 50);
    }

    @Test
    @DisplayName("T-FR7-01 an instant inside the window reports open=true with the active semester")
    void tFr7_01_insideWindow() throws Exception {
        MutableClock.setOffset("2026-11-08T11:26:41+08:00");

        JsonNode period = body(mockMvc.perform(get("/api/registration-period"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.semester").value("2026-S2"))
                .andExpect(jsonPath("$.open").value(true)));

        OffsetDateTime start = OffsetDateTime.parse(period.path("startAt").asText());
        OffsetDateTime end = OffsetDateTime.parse(period.path("endAt").asText());
        OffsetDateTime server = OffsetDateTime.parse(period.path("serverTime").asText());
        assertThat(server).isAfter(start).isBefore(end);
        assertThat(start.toInstant()).isEqualTo(OffsetDateTime.parse(START).toInstant());
        assertThat(end.toInstant()).isEqualTo(OffsetDateTime.parse(END).toInstant());
    }

    @Test
    @DisplayName("T-FR7-02 exactly at startAt the window is open and registration succeeds")
    void tFr7_02_atStart() throws Exception {
        MutableClock.setOffset(START);

        mockMvc.perform(get("/api/registration-period"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.open").value(true));
        postRegisterAuthed(bearer(aisha), course.getId()).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("T-FR7-03 exactly at endAt the window is open and registration succeeds")
    void tFr7_03_atEnd() throws Exception {
        MutableClock.setOffset(END);

        mockMvc.perform(get("/api/registration-period"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.open").value(true));
        postRegisterAuthed(bearer(aisha), course.getId()).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("T-FR7-04 one second before startAt is closed and creates nothing")
    void tFr7_04_beforeStart() throws Exception {
        MutableClock.setOffset("2026-09-30T23:59:59+08:00");

        mockMvc.perform(get("/api/registration-period"))
                .andExpect(jsonPath("$.open").value(false));
        postRegisterAuthed(bearer(aisha), course.getId())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("REGISTRATION_CLOSED"));

        assertThat(registrationRepository.count()).isZero();
    }

    @Test
    @DisplayName("T-FR7-05 one second after endAt is closed and audited")
    void tFr7_05_afterEnd() throws Exception {
        MutableClock.setOffset("2026-12-21T00:00:00+08:00");

        mockMvc.perform(get("/api/registration-period"))
                .andExpect(jsonPath("$.open").value(false));
        postRegisterAuthed(bearer(aisha), course.getId())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("REGISTRATION_CLOSED"));

        assertThat(auditLogRepository.findByStudentIdOrderByIdAsc("32012345"))
                .anyMatch(row -> row.getAction() == AuditAction.REGISTER_REJECTED
                        && row.getOutcome() == edu.monash.registration.domain.AuditOutcome.FAILURE
                        && "REGISTRATION_CLOSED".equals(row.getDetail()));
    }

    @Test
    @DisplayName("T-FR7-06 a drop after the window is 403 and the row survives")
    void tFr7_06_dropAfterEnd() throws Exception {
        JsonNode registration = body(postRegisterAuthed(bearer(aisha), course.getId())
                .andExpect(status().isCreated()));
        String registrationId = registration.path("registrationId").asText();

        MutableClock.setOffset("2026-12-21T00:00:00+08:00");
        mockMvc.perform(delete("/api/registrations/" + registrationId)
                        .header("Authorization", bearer(aisha)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("REGISTRATION_CLOSED"));

        assertThat(registrationRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("T-FR7-07 reads stay available while the window is closed")
    void tFr7_07_readsWhileClosed() throws Exception {
        postRegisterAuthed(bearer(aisha), course.getId()).andExpect(status().isCreated());
        MutableClock.setOffset("2026-12-21T00:00:00+08:00");

        getAuthed("/api/courses", bearer(aisha)).andExpect(status().isOk());
        getAuthed("/api/registrations", bearer(aisha))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1));
        getAuthed("/api/registrations/export", bearer(aisha)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("T-FR7-09 authentication precedes the period check")
    void tFr7_09_authBeforePeriod() throws Exception {
        MutableClock.setOffset("2026-12-21T00:00:00+08:00");

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/api/registrations")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":\"" + course.getId() + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("T-FR7-10 the period endpoint is public and ignores a garbage token")
    void tFr7_10_publicIgnoresBadToken() throws Exception {
        MutableClock.setOffset("2026-11-08T11:26:41+08:00");

        mockMvc.perform(get("/api/registration-period")
                        .header("Authorization", "garbage"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.open").value(true));
    }
}
