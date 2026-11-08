package edu.monash.registration;

import edu.monash.registration.domain.AuditAction;
import edu.monash.registration.domain.AuditLog;
import edu.monash.registration.domain.AuditOutcome;
import edu.monash.registration.domain.Student;
import edu.monash.registration.support.BaseIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression for review finding N-7 / decision A-19: a register or drop attempt rejected by request
 * validation is still an attempt and must leave exactly one {@code VALIDATION_ERROR} audit row.
 *
 * <p>Coverage gap this closes: the pre-round-3 suite asserted the 400 envelope (T-FR3-05,
 * T-NFR2-10) and the 404 drop envelope (T-FR4-10) but never inspected {@code audit_log}, so the
 * E-12/E-13 rows could have disappeared without any test noticing.
 */
@DisplayName("REGRESSION N-7 / A-19 — validation rejections are audited")
class RegressionValidationAuditTest extends BaseIntegrationTest {

    @Test
    @DisplayName("T-A19-01 malformed courseId: 400 VALIDATION_ERROR and exactly one E-12 row")
    void tA19_01_registerValidationIsAudited() throws Exception {
        Student aisha = fixtures.aisha();

        mockMvc.perform(post("/api/registrations")
                        .header("Authorization", bearer(aisha))
                        .contentType(APPLICATION_JSON)
                        .content("{\"courseId\":\"not-a-uuid\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));

        assertThat(registrationRepository.count()).isZero();

        List<AuditLog> rows = auditLogRepository.findByStudentIdOrderByIdAsc("32012345");
        assertThat(rows).as("one row for the rejected attempt (E-12)").hasSize(1);
        AuditLog row = rows.get(0);
        assertThat(row.getStudentId()).isEqualTo("32012345");
        assertThat(row.getAction()).isEqualTo(AuditAction.REGISTER_REJECTED);
        assertThat(row.getOutcome()).isEqualTo(AuditOutcome.FAILURE);
        assertThat(row.getDetail()).isEqualTo("VALIDATION_ERROR");
        assertThat(row.getCourseCode()).as("the course never resolved").isNull();
    }

    @Test
    @DisplayName("T-A19-01b two malformed register attempts: two rows, never one retried row")
    void tA19_01b_twoAttemptsTwoRows() throws Exception {
        Student aisha = fixtures.aisha();
        String token = bearer(aisha);

        for (int attempt = 1; attempt <= 2; attempt++) {
            mockMvc.perform(post("/api/registrations")
                            .header("Authorization", token)
                            .contentType(APPLICATION_JSON)
                            .content("{\"courseId\":\"still-not-a-uuid\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        }

        List<AuditLog> rows = auditLogRepository.findByStudentIdOrderByIdAsc("32012345");
        assertThat(rows).as("one row per attempt, no de-duplication across attempts").hasSize(2);
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.getAction()).isEqualTo(AuditAction.REGISTER_REJECTED);
            assertThat(row.getOutcome()).isEqualTo(AuditOutcome.FAILURE);
            assertThat(row.getDetail()).isEqualTo("VALIDATION_ERROR");
            assertThat(row.getCourseCode()).isNull();
        });
        assertThat(rows.stream().map(AuditLog::getId).distinct().count()).isEqualTo(2);
    }

    @Test
    @DisplayName("T-A19-02 non-UUID registrationId: frozen 404 REGISTRATION_NOT_FOUND plus one E-13 row")
    void tA19_02_dropValidationIsAudited() throws Exception {
        Student aisha = fixtures.aisha();

        mockMvc.perform(delete("/api/registrations/not-a-uuid")
                        .header("Authorization", bearer(aisha)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("REGISTRATION_NOT_FOUND"));

        List<AuditLog> rows = auditLogRepository.findByStudentIdOrderByIdAsc("32012345");
        assertThat(rows).as("one row for the rejected drop attempt (E-13)").hasSize(1);
        AuditLog row = rows.get(0);
        assertThat(row.getAction()).isEqualTo(AuditAction.DROP_REJECTED);
        assertThat(row.getOutcome()).isEqualTo(AuditOutcome.FAILURE);
        assertThat(row.getDetail()).isEqualTo("VALIDATION_ERROR");
        assertThat(row.getCourseCode()).isNull();
    }

    @Test
    @DisplayName("T-A19-03 unauthenticated malformed attempt: 401 and no audit row (no subject)")
    void tA19_03_unauthenticatedIsNotAudited() throws Exception {
        mockMvc.perform(post("/api/registrations")
                        .contentType(APPLICATION_JSON)
                        .content("{\"courseId\":\"not-a-uuid\"}"))
                .andExpect(status().isUnauthorized());

        assertThat(auditLogRepository.count())
                .as("A-19: authentication rejections are not audited, there is no subject")
                .isZero();
    }
}
