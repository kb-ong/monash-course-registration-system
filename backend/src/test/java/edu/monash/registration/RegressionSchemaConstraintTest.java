package edu.monash.registration;

import edu.monash.registration.domain.Course;
import edu.monash.registration.support.BaseIntegrationTest;
import java.sql.Time;
import java.time.LocalTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regression for review finding M-3: invariants C2/C3 (seat bounds, credit hours, capacity) have a
 * database backstop as well as application logic. Without the CHECK constraints a direct UPDATE
 * could put a course into an impossible state.
 *
 * <p>Round 3: the assertions track the constraint names the entities actually declare. The previous
 * revision asserted stale names ({@code CK_COURSE_CREDIT_HOURS_POSITIVE},
 * {@code CK_COURSE_CAPACITY_POSITIVE}) and treated {@code capacity = 0} as illegal, which it is
 * not: A-18 allows a zero-capacity course, so the check is {@code capacity >= 0}.
 */
@DisplayName("REGRESSION M-3 — database CHECK constraints")
class RegressionSchemaConstraintTest extends BaseIntegrationTest {

    /** Named CHECKs declared on {@link Course}. Verified in {@code information_schema}. */
    private static final List<String> COURSE_CHECKS = List.of(
            "CK_COURSE_CREDIT_HOURS_ALLOWED",
            "CK_COURSE_CAPACITY_NON_NEGATIVE",
            "CK_COURSE_AVAILABLE_SEATS_NON_NEGATIVE",
            "CK_COURSE_AVAILABLE_SEATS_WITHIN_CAPACITY");

    /** The schedule ordering backstop declared on {@code CourseSchedule}. */
    private static final List<String> SCHEDULE_CHECKS = List.of(
            "CK_COURSE_SCHEDULE_TIME_ORDER");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("T-M3-01 the named COURSE/COURSE_SCHEDULE CHECKs exist and illegal rows are rejected")
    void tM3_01_checkConstraintsRejectInvalidSeats() {
        assertThat(checkConstraints("COURSE"))
                .as("named CHECK constraints generated from the Course @Check annotations")
                .containsAll(COURSE_CHECKS);
        assertThat(checkConstraints("COURSE_SCHEDULE"))
                .as("the CourseSchedule time-order constraint exists")
                .containsAll(SCHEDULE_CHECKS);

        Course course = fixtures.singleSlotCourse("TST0301", 6, 5, 5);
        UUID id = course.getId();

        // Control: a legal direct update still works, so the failures below are the constraints.
        assertThat(jdbcTemplate.update("update course set available_seats = 4 where id = ?", id))
                .isEqualTo(1);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "update course set available_seats = -1 where id = ?", id))
                .as("C2: available_seats >= 0 is enforced by the database")
                .isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "update course set available_seats = capacity + 1 where id = ?", id))
                .as("C3: available_seats <= capacity is enforced by the database")
                .isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "update course set credit_hours = 0 where id = ?", id))
                .as("credit_hours in (3, 6): 0 is not an allowed value")
                .isInstanceOf(DataAccessException.class);

        // The old constraint only said "positive", so 4 slipped through. It must not.
        assertThatThrownBy(() -> jdbcTemplate.update(
                "update course set credit_hours = 4 where id = ?", id))
                .as("credit_hours in (3, 6): 4 is not an allowed value")
                .isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "update course set credit_hours = 5 where id = ?", id))
                .as("credit_hours in (3, 6): 5 is not an allowed value")
                .isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> jdbcTemplate.update(
                "update course set capacity = -1 where id = ?", id))
                .as("capacity >= 0 is enforced by the database")
                .isInstanceOf(DataAccessException.class);

        // Both allowed credit values remain writable (not an over-tight constraint).
        assertThat(jdbcTemplate.update("update course set credit_hours = 3 where id = ?", id))
                .as("credit_hours = 3 is legal")
                .isEqualTo(1);
        assertThat(jdbcTemplate.update("update course set credit_hours = 6 where id = ?", id))
                .as("credit_hours = 6 is legal")
                .isEqualTo(1);

        // A-18: a zero-capacity course is legal. It only requires seats to be zero as well.
        assertThat(jdbcTemplate.update(
                "update course set capacity = 0, available_seats = 0 where id = ?", id))
                .as("A-18: capacity = 0 with available_seats = 0 must be accepted")
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("select capacity from course where id = ?",
                Integer.class, id)).isZero();
        assertThat(jdbcTemplate.queryForObject("select available_seats from course where id = ?",
                Integer.class, id)).isZero();

        // Restore the fixture shape before exercising the schedule constraint.
        assertThat(jdbcTemplate.update(
                "update course set capacity = 5, available_seats = 4 where id = ?", id))
                .isEqualTo(1);

        UUID legalSlot = UUID.randomUUID();
        assertThat(jdbcTemplate.update("""
                        insert into course_schedule (id, course_id, day_code, start_time, end_time, location)
                        values (?, ?, ?, ?, ?, ?)
                        """, legalSlot, id, "MON", Time.valueOf(LocalTime.parse("13:00")),
                        Time.valueOf(LocalTime.parse("15:00")), "Clayton"))
                .as("an ordered slot is legal")
                .isEqualTo(1);
        assertThat(jdbcTemplate.update("delete from course_schedule where id = ?", legalSlot))
                .isEqualTo(1);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into course_schedule (id, course_id, day_code, start_time, end_time, location)
                values (?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), id, "TUE", Time.valueOf(LocalTime.parse("11:00")),
                Time.valueOf(LocalTime.parse("11:00")), "Clayton"))
                .as("end_time > start_time: equal times are rejected")
                .isInstanceOf(DataAccessException.class);

        assertThatThrownBy(() -> jdbcTemplate.update("""
                insert into course_schedule (id, course_id, day_code, start_time, end_time, location)
                values (?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), id, "WED", Time.valueOf(LocalTime.parse("11:00")),
                Time.valueOf(LocalTime.parse("10:00")), "Clayton"))
                .as("end_time > start_time: a reversed slot is rejected")
                .isInstanceOf(DataAccessException.class);

        assertThat(jdbcTemplate.queryForObject("select available_seats from course where id = ?",
                Integer.class, id))
                .as("rejected updates leave the row untouched")
                .isEqualTo(4);
        assertThat(jdbcTemplate.queryForObject("select count(*) from course_schedule where course_id = ?",
                Integer.class, id))
                .as("only the fixture's original slot remains")
                .isEqualTo(1);
    }

    private List<String> checkConstraints(String tableName) {
        return jdbcTemplate.queryForList("""
                        select constraint_name from information_schema.table_constraints
                        where table_name = ? and constraint_type = 'CHECK'
                        """, String.class, tableName).stream()
                .map(name -> name.toUpperCase(Locale.ROOT))
                .toList();
    }
}
