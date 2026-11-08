package edu.monash.registration;

import com.fasterxml.jackson.databind.JsonNode;
import edu.monash.registration.domain.Course;
import edu.monash.registration.domain.Student;
import edu.monash.registration.support.BaseIntegrationTest;
import edu.monash.registration.support.BurstSupport;
import edu.monash.registration.support.BurstSupport.Response;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FR6 — the critical capacity suite. Every burst uses N distinct students so a duplicate-key 409
 * can never mask an oversell, and asserts on counts rather than on which thread won.
 */
@DisplayName("FR6 — Capacity validation and concurrency")
class CapacityConcurrencyTest extends BaseIntegrationTest {

    private Student aisha;
    private Student daniel;

    @BeforeEach
    void seed() {
        aisha = fixtures.aisha();
        daniel = fixtures.daniel();
    }

    @Test
    @DisplayName("T-FR6-01 one registration decrements exactly one seat and keeps invariant C1")
    void tFr6_01_oneSeat() throws Exception {
        Course course = fixtures.singleSlotCourse("FIT2004", 6, 80, 80);

        postRegisterAuthed(bearer(aisha), course.getId())
                .andExpect(status().isCreated())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.availableSeats").value(79));

        assertInvariant(course.getId(), 80);
    }

    @Test
    @DisplayName("T-FR6-02 a full single-seat course rejects the next student")
    void tFr6_02_fullSingleSeat() throws Exception {
        Course course = fixtures.singleSlotCourse("FIT3143", 6, 1, 1);
        postRegisterAuthed(bearer(daniel), course.getId()).andExpect(status().isCreated());

        postRegisterAuthed(bearer(aisha), course.getId())
                .andExpect(status().isConflict())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.error.code").value("COURSE_FULL"));

        assertThat(registrationRepository.count()).isEqualTo(1);
        assertThat(courseRepository.findById(course.getId()).orElseThrow().getAvailableSeats())
                .isZero();
        assertThat(registrationRepository.existsByStudentIdAndCourseId(
                daniel.getId(), course.getId())).isTrue();
        assertThat(registrationRepository.existsByStudentIdAndCourseId(
                aisha.getId(), course.getId())).isFalse();
    }

    @Test
    @DisplayName("T-FR6-03 five concurrent students on one seat yield exactly one success")
    void tFr6_03_fiveOnOneSeat() throws Exception {
        Course course = fixtures.singleSlotCourse("TST0001", 6, 1, 1);
        List<String> tokens = tokens(fixtures.students("91", 5));

        List<Response> responses = BurstSupport.registerInParallel(mockMvc, tokens, course.getId());

        assertThat(BurstSupport.countStatus(responses, 201)).isEqualTo(1);
        assertThat(BurstSupport.countErrorCode(responses, "COURSE_FULL")).isEqualTo(4);
        assertThat(registrationRepository.count()).isEqualTo(1);
        assertInvariant(course.getId(), 1);
        assertNoServerErrors(responses);
    }

    @Test
    @DisplayName("T-FR6-04 25 concurrent students on 10 seats: 10 x 201, 15 x COURSE_FULL")
    void tFr6_04_twentyFiveOnTenSeats() throws Exception {
        Course course = fixtures.singleSlotCourse("TST0010", 6, 10, 10);
        List<Student> students = fixtures.students("92", 25);
        List<String> tokens = tokens(students);

        List<Response> responses = BurstSupport.registerInParallel(mockMvc, tokens, course.getId());

        assertThat(BurstSupport.countStatus(responses, 201)).isEqualTo(10);
        assertThat(BurstSupport.countStatus(responses, 409)).isEqualTo(15);
        assertThat(BurstSupport.countErrorCode(responses, "COURSE_FULL")).isEqualTo(15);
        assertThat(registrationRepository.count()).isEqualTo(10);
        assertThat(courseRepository.findById(course.getId()).orElseThrow().getAvailableSeats())
                .isZero();
        assertInvariant(course.getId(), 10);
        assertNoServerErrors(responses);
        long distinctStudents = students.stream()
                .filter(s -> registrationRepository.existsByStudentIdAndCourseId(
                        s.getId(), course.getId()))
                .count();
        assertThat(distinctStudents).isEqualTo(10);
    }

    @Test
    @DisplayName("T-FR6-05 the burst is stable across three fresh courses")
    void tFr6_05_repeatedBursts() throws Exception {
        for (int run = 1; run <= 3; run++) {
            Course course = fixtures.singleSlotCourse("TST10" + run, 6, 10, 10);
            List<String> tokens = tokens(fixtures.students("93" + run, 15));

            List<Response> responses = BurstSupport.registerInParallel(mockMvc, tokens,
                    course.getId());

            assertThat(BurstSupport.countStatus(responses, 201)).as("run %s successes", run)
                    .isEqualTo(10);
            assertThat(BurstSupport.countErrorCode(responses, "COURSE_FULL"))
                    .as("run %s rejections", run).isEqualTo(5);
            assertInvariant(course.getId(), 10);
            assertNoServerErrors(responses);
        }
    }

    @Test
    @DisplayName("T-FR6-06 / T-FR4-08 a register/drop race never breaks invariant C1")
    void tFr6_06_registerDropRace() throws Exception {
        Course course = fixtures.singleSlotCourse("TST0006", 6, 1, 1);
        JsonNode danielRegistration = body(postRegisterAuthed(bearer(daniel), course.getId())
                .andExpect(status().isCreated()));
        String registrationId = danielRegistration.path("registrationId").asText();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Integer> drop = pool.submit(() -> {
                start.await();
                return mockMvc.perform(delete("/api/registrations/" + registrationId)
                                .header("Authorization", bearer(daniel)))
                        .andReturn().getResponse().getStatus();
            });
            Future<Integer> register = pool.submit(() -> {
                start.await();
                return mockMvc.perform(org.springframework.test.web.servlet.request
                                .MockMvcRequestBuilders.post("/api/registrations")
                                .header("Authorization", bearer(aisha))
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                .content("{\"courseId\":\"" + course.getId() + "\"}"))
                        .andReturn().getResponse().getStatus();
            });
            start.countDown();

            assertThat(drop.get(30, TimeUnit.SECONDS)).isEqualTo(200);
            assertThat(register.get(30, TimeUnit.SECONDS)).isIn(201, 409);
        } finally {
            pool.shutdownNow();
        }

        assertInvariant(course.getId(), 1);
    }

    @Test
    @DisplayName("T-FR6-07 the seeded-full course has no free seat and still honours invariant C1")
    void tFr6_07_seededFull() throws Exception {
        // M-4: the real seed is capacity 1 with the only seat held by a registration.
        Course course = fixtures.seededFullCourse("FIT3182", daniel);

        postRegisterAuthed(bearer(aisha), course.getId())
                .andExpect(status().isConflict())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.error.code").value("COURSE_FULL"));

        assertThat(courseRepository.findById(course.getId()).orElseThrow().getAvailableSeats())
                .isZero();
        assertThat(registrationRepository.countByCourseId(course.getId())).isEqualTo(1);
        assertThat(registrationRepository.existsByStudentIdAndCourseId(
                daniel.getId(), course.getId())).isTrue();
        assertThat(registrationRepository.existsByStudentIdAndCourseId(
                aisha.getId(), course.getId())).isFalse();
        // No TD-4 carve-out any more: C1 holds for the seeded-full shape too.
        assertInvariant(course.getId(), 1);
    }

    @Test
    @DisplayName("T-FR6-10 dropping the only seat makes the course registrable again")
    void tFr6_10_lastSeatRecycles() throws Exception {
        Course course = fixtures.singleSlotCourse("TST0011", 6, 1, 1);
        JsonNode first = body(postRegisterAuthed(bearer(daniel), course.getId())
                .andExpect(status().isCreated()));
        String regId = first.path("registrationId").asText();

        deleteAuthed(bearer(daniel), regId).andExpect(status().isOk());
        postRegisterAuthed(bearer(aisha), course.getId())
                .andExpect(status().isCreated())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.availableSeats").value(0));

        assertInvariant(course.getId(), 1);
    }

    @Test
    @DisplayName("T-FR6-11 a rejected registration is atomic: no seat change, no row")
    void tFr6_11_rejectedIsAtomic() throws Exception {
        Course course = fixtures.singleSlotCourse("TST0012", 6, 2, 2);
        // Fill the course legitimately so the invariant holds before the rejected attempt.
        postRegisterAuthed(bearer(aisha), course.getId()).andExpect(status().isCreated());
        postRegisterAuthed(bearer(daniel), course.getId()).andExpect(status().isCreated());
        assertInvariant(course.getId(), 2);

        Student third = fixtures.student("98999999");
        postRegisterAuthed(bearer(third), course.getId())
                .andExpect(status().isConflict());

        assertThat(registrationRepository.count()).isEqualTo(2);
        assertThat(courseRepository.findById(course.getId()).orElseThrow().getAvailableSeats())
                .isZero();
        assertInvariant(course.getId(), 2);
    }

    private List<String> tokens(List<Student> students) {
        List<String> tokens = new ArrayList<>(students.size());
        for (Student student : students) {
            tokens.add(fixtures.token(student.getStudentId()));
        }
        return tokens;
    }

    private void assertInvariant(UUID courseId, int capacity) {
        Course course = courseRepository.findById(courseId).orElseThrow();
        long rows = registrationRepository.countByCourseId(courseId);
        assertThat(course.getAvailableSeats() + rows)
                .as("availableSeats + registrations == capacity")
                .isEqualTo(capacity);
        assertThat(course.getAvailableSeats()).isBetween(0, capacity);
    }

    private void assertNoServerErrors(List<Response> responses) {
        Set<Integer> statuses = ConcurrentHashMap.newKeySet();
        responses.forEach(r -> statuses.add(r.status()));
        assertThat(statuses).doesNotContain(500, 502, 503, 504);
    }
}
