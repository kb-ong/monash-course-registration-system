package edu.monash.registration;

import com.fasterxml.jackson.databind.JsonNode;
import edu.monash.registration.domain.AuditAction;
import edu.monash.registration.domain.Course;
import edu.monash.registration.domain.Student;
import edu.monash.registration.support.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FR10 — the semester credit limit is configuration, not a constant. This context runs with the
 * limit set to 18 (the FR10 example), while the rest of the suite exercises the 24 default.
 */
@DisplayName("FR10 — Configured maximum credit hours")
@TestPropertySource(properties = "app.registration.max-credits=18")
class MaxCreditConfigTest extends BaseIntegrationTest {

    private Student aisha;
    private Course fit1045;
    private Course fit2004;
    private Course tst0002;
    private Course tst0003;
    private Course tst0004;

    @BeforeEach
    void seed() {
        aisha = fixtures.aisha();
        fit1045 = fixtures.singleSlotCourse("FIT1045", 6, 120, 120);
        fit2004 = fixtures.singleSlotCourse("FIT2004", 6, 80, 80);
        tst0002 = fixtures.singleSlotCourse("TST0002", 3, 50, 50);
        tst0003 = fixtures.singleSlotCourse("TST0003", 3, 50, 50);
        tst0004 = fixtures.singleSlotCourse("TST0004", 6, 50, 50);
    }

    @Test
    @DisplayName("T-FR10-01 a 6cp addition is rejected at 15/18 and names the 3 remaining credits")
    void tFr10_01_rejectsOverConfiguredLimit() throws Exception {
        register(fit1045);
        register(fit2004);
        register(tst0002);

        JsonNode list = body(getAuthed("/api/registrations", bearer(aisha))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCredits").value(15))
                .andExpect(jsonPath("$.maxCredits").value(18))
                .andExpect(jsonPath("$.remainingCredits").value(3)));
        assertThat(list.path("items").size()).isEqualTo(3);

        postRegisterAuthed(bearer(aisha), tst0004.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CREDIT_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.error.message").value(
                        org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.containsString("18 credit point limit"),
                                org.hamcrest.Matchers.containsString("3 credit points remaining"))));

        assertThat(registrationRepository.count()).isEqualTo(3);
        assertThat(courseRepository.findById(tst0004.getId()).orElseThrow().getAvailableSeats())
                .isEqualTo(50);
        assertThat(auditLogRepository.findByStudentIdOrderByIdAsc("32012345"))
                .anyMatch(row -> row.getAction() == AuditAction.REGISTER_REJECTED
                        && "CREDIT_LIMIT_EXCEEDED".equals(row.getDetail()));
    }

    @Test
    @DisplayName("T-FR10-05 an empty registration list reports the configured limit")
    void tFr10_05_emptyListUsesConfiguredLimit() throws Exception {
        getAuthed("/api/registrations", bearer(aisha))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCredits").value(0))
                .andExpect(jsonPath("$.maxCredits").value(18))
                .andExpect(jsonPath("$.remainingCredits").value(18))
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    @DisplayName("T-FR10-02 landing exactly on 18 is allowed and leaves zero remaining")
    void tFr10_02_landingExactlyOnConfiguredLimit() throws Exception {
        register(fit1045);
        register(fit2004);
        register(tst0002);

        postRegisterAuthed(bearer(aisha), tst0003.getId())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.course.code").value("TST0003"));

        getAuthed("/api/registrations", bearer(aisha))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCredits").value(18))
                .andExpect(jsonPath("$.maxCredits").value(18))
                .andExpect(jsonPath("$.remainingCredits").value(0));
    }

    @Test
    @DisplayName("T-FR10-03 a course attempt at the limit is rejected with zero remaining")
    void tFr10_03_atLimitRejectsFurtherCourse() throws Exception {
        register(fit1045);
        register(fit2004);
        register(tst0002);
        register(tst0003);

        postRegisterAuthed(bearer(aisha), tst0004.getId())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CREDIT_LIMIT_EXCEEDED"))
                .andExpect(jsonPath("$.error.message").value(
                        org.hamcrest.Matchers.containsString("0 credit points remaining")));

        assertThat(registrationRepository.count()).isEqualTo(4);
    }

    private void register(Course course) throws Exception {
        postRegisterAuthed(bearer(aisha), course.getId()).andExpect(status().isCreated());
    }
}
