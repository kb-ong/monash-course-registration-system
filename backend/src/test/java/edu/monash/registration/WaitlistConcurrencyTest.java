package edu.monash.registration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.monash.registration.domain.Course;
import edu.monash.registration.domain.Registration;
import edu.monash.registration.domain.Student;
import edu.monash.registration.support.BaseIntegrationTest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * FR11 concurrency: joins are serialised by the course row lock, so positions are unique even when
 * students click at the same instant, and concurrent drops promote exactly one student each without
 * ever overselling the course.
 */
@DisplayName("FR11 — Waitlist concurrency")
class WaitlistConcurrencyTest extends BaseIntegrationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    @DisplayName("T-FR11-C01 five simultaneous joins receive five distinct positions and five rows")
    void tFr11C01_concurrentJoins() throws Exception {
        Student holder = fixtures.daniel();
        Course course = fixtures.seededFullCourse("FIT3182", holder);
        List<Student> students = fixtures.students("97", 5);
        List<String> tokens = students.stream()
                .map(student -> fixtures.token(student.getStudentId()))
                .toList();

        List<MvcResult> results = joinInParallel(tokens, course.getId());

        assertThat(results).hasSize(5);
        assertThat(results).allSatisfy(result ->
                assertThat(result.getResponse().getStatus()).isEqualTo(201));
        Set<Integer> positions = new HashSet<>();
        for (MvcResult result : results) {
            JsonNode body = MAPPER.readTree(result.getResponse().getContentAsString());
            positions.add(body.path("position").asInt());
        }
        assertThat(positions).containsExactlyInAnyOrder(1, 2, 3, 4, 5);
        assertThat(waitlistRepository.count()).isEqualTo(5);
    }

    @Test
    @DisplayName("T-FR11-C02 two simultaneous drops promote both waiters with no oversell and C1 intact")
    void tFr11C02_concurrentDropsPromote() throws Exception {
        Student fourth = fixtures.student("32055555", "Fourth Student", "BCS");
        Student aisha = fixtures.aisha();
        Student priya = fixtures.student("32011111", "Priya Nair", "BBIS");
        Course course = fixtures.singleSlotCourse("FIT3182", 6, 2, 0);
        Registration danielRegistration = fixtures.registration(fixtures.daniel(), course);
        Registration fourthRegistration = fixtures.registration(fourth, course);

        mockMvc.perform(post("/api/waitlist").header("Authorization", bearer(aisha))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":\"" + course.getId() + "\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .status().isCreated());
        mockMvc.perform(post("/api/waitlist").header("Authorization", bearer(priya))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":\"" + course.getId() + "\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .status().isCreated());

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<MvcResult> results = new ArrayList<>(2);
        try {
            Future<MvcResult> first = pool.submit(() -> {
                start.await();
                return mockMvc.perform(delete(
                                "/api/registrations/" + danielRegistration.getId())
                        .header("Authorization", bearer("32067890"))).andReturn();
            });
            Future<MvcResult> second = pool.submit(() -> {
                start.await();
                return mockMvc.perform(delete(
                                "/api/registrations/" + fourthRegistration.getId())
                        .header("Authorization", bearer("32055555"))).andReturn();
            });
            start.countDown();
            results.add(first.get(60, TimeUnit.SECONDS));
            results.add(second.get(60, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }

        assertThat(results).allSatisfy(result ->
                assertThat(result.getResponse().getStatus()).isEqualTo(200));
        assertThat(waitlistRepository.count()).isZero();
        assertThat(registrationRepository.countByCourseId(course.getId()))
                .as("both waiters are now enrolled and no extra registration was created")
                .isEqualTo(2);
        assertThat(registrationRepository.existsByStudentIdAndCourseId(
                aisha.getId(), course.getId())).isTrue();
        assertThat(registrationRepository.existsByStudentIdAndCourseId(
                priya.getId(), course.getId())).isTrue();

        Course after = courseRepository.findById(course.getId()).orElseThrow();
        assertThat(after.getAvailableSeats()).isZero();
        assertThat(after.getAvailableSeats()
                + registrationRepository.countByCourseId(course.getId())).isEqualTo(2);
    }

    @Test
    @DisplayName("T-FR11-C03 two simultaneous joins by one student yield one row and one 201")
    void tFr11C03_concurrentDuplicateJoin() throws Exception {
        Student aisha = fixtures.aisha();
        Course course = fixtures.seededFullCourse("FIT3182", fixtures.daniel());
        List<String> tokens = List.of(fixtures.token("32012345"), fixtures.token("32012345"));

        List<MvcResult> results = joinInParallel(tokens, course.getId());

        List<Integer> statuses = results.stream()
                .map(result -> result.getResponse().getStatus())
                .sorted()
                .toList();
        assertThat(statuses).containsExactly(201, 409);
        assertThat(waitlistRepository.count()).isEqualTo(1);
        assertThat(waitlistRepository.existsByStudentIdAndCourseId(aisha.getId(), course.getId()))
                .isTrue();
    }

    @Test
    @DisplayName("T-FR11-C04 two simultaneous leaves of one entry yield one 200 and one 404, never a 5xx")
    void tFr11C04_concurrentLeaves() throws Exception {
        Student aisha = fixtures.aisha();
        Course course = fixtures.seededFullCourse("FIT3182", fixtures.daniel());
        String waitlistId = MAPPER.readTree(
                        mockMvc.perform(post("/api/waitlist")
                                        .header("Authorization", bearer(aisha))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content("{\"courseId\":\"" + course.getId() + "\"}"))
                                .andReturn().getResponse().getContentAsString())
                .path("waitlistId").asText();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<MvcResult> results = new ArrayList<>(2);
        List<Future<MvcResult>> futures = new ArrayList<>(2);
        try {
            for (int attempt = 0; attempt < 2; attempt++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return mockMvc.perform(delete("/api/waitlist/" + waitlistId)
                            .header("Authorization", bearer("32012345"))).andReturn();
                }));
            }
            start.countDown();
            for (Future<MvcResult> future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(results.stream().map(r -> r.getResponse().getStatus()).sorted().toList())
                .containsExactly(200, 404);
        assertThat(waitlistRepository.count()).isZero();
    }

    private List<MvcResult> joinInParallel(List<String> tokens, UUID courseId) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(tokens.size());
        CountDownLatch start = new CountDownLatch(1);
        List<Future<MvcResult>> futures = new ArrayList<>(tokens.size());
        try {
            for (String token : tokens) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return mockMvc.perform(post("/api/waitlist")
                                    .header("Authorization", "Bearer " + token)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"courseId\":\"" + courseId + "\"}"))
                            .andReturn();
                }));
            }
            start.countDown();
            List<MvcResult> results = new ArrayList<>(tokens.size());
            for (Future<MvcResult> future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }
}
