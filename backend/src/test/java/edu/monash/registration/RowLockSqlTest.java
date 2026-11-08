package edu.monash.registration;

import edu.monash.registration.domain.Course;
import edu.monash.registration.domain.Student;
import edu.monash.registration.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T-FR6-08: the seat read must be a database row lock, not an in-JVM {@code synchronized} block.
 * SQL logging is enabled for this context only; a successful registration must emit
 * {@code ... for update}.
 */
@DisplayName("FR6 — Capacity protection uses a row lock")
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = {
        "spring.jpa.show-sql=true",
        "spring.jpa.properties.hibernate.format_sql=false",
        "logging.level.org.hibernate.SQL=DEBUG"
})
class RowLockSqlTest extends BaseIntegrationTest {

    @Test
    @DisplayName("T-FR6-08 registration SQL contains FOR UPDATE")
    void tFr6_08_sqlUsesForUpdate(CapturedOutput output) throws Exception {
        Student aisha = fixtures.aisha();
        Course course = fixtures.singleSlotCourse("FIT1045", 6, 50, 50);

        postRegisterAuthed(bearer(aisha), course.getId()).andExpect(status().isCreated());

        assertThat(output.getAll().toLowerCase())
                .as("expected SELECT ... FOR UPDATE in emitted SQL")
                .contains("for update");
    }
}
