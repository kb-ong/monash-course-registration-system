package edu.monash.registration;

import com.fasterxml.jackson.databind.JsonNode;
import edu.monash.registration.domain.AuditAction;
import edu.monash.registration.domain.AuditLog;
import edu.monash.registration.domain.AuditOutcome;
import edu.monash.registration.domain.Course;
import edu.monash.registration.domain.Registration;
import edu.monash.registration.domain.Student;
import edu.monash.registration.support.BaseIntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;

/**
 * Regression for review finding M-2: two concurrent DELETEs on the same registration must be
 * serialised on the registration row ({@code SELECT ... FOR UPDATE}), so the loser gets a clean
 * 404 instead of a 500, and both attempts are audited.
 */
@DisplayName("REGRESSION M-2 — concurrent double drop")
class RegressionConcurrentDropTest extends BaseIntegrationTest {

    @Test
    @DisplayName("T-M2-01 two parallel DELETEs on one registration -> 200 + 404, no 5xx, 2 audit rows, one seat returned")
    void tM2_01_concurrentDoubleDrop() throws Exception {
        Student aisha = fixtures.aisha();
        Student daniel = fixtures.daniel();

        // capacity 5, seats 3, two held registrations -> C1 (3 + 2 == 5) holds before the race.
        // seats deliberately below capacity-1 so an erroneous double increment would break C1.
        Course course = fixtures.singleSlotCourse("TST0500", 6, 5, 3);
        Registration target = fixtures.registration(aisha, course);
        fixtures.registration(daniel, course);
        assertThat(registrationRepository.countByCourseId(course.getId())).isEqualTo(2);

        String registrationId = target.getId().toString();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<MvcResult> results = new ArrayList<>(2);
        try {
            Future<MvcResult> first = pool.submit(() -> {
                start.await();
                return mockMvc.perform(delete("/api/registrations/" + registrationId)
                        .header("Authorization", bearer(aisha))).andReturn();
            });
            Future<MvcResult> second = pool.submit(() -> {
                start.await();
                return mockMvc.perform(delete("/api/registrations/" + registrationId)
                        .header("Authorization", bearer(aisha))).andReturn();
            });
            start.countDown();
            results.add(first.get(60, TimeUnit.SECONDS));
            results.add(second.get(60, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }

        List<Integer> statuses = results.stream()
                .map(result -> result.getResponse().getStatus())
                .sorted()
                .toList();
        assertThat(statuses)
                .as("exactly one 200 and one 404 - never a 5xx (was 500 INTERNAL_ERROR)")
                .containsExactly(200, 404);

        MvcResult notFound = results.stream()
                .filter(result -> result.getResponse().getStatus() == 404)
                .findFirst()
                .orElseThrow();
        JsonNode error = objectMapper.readTree(
                notFound.getResponse().getContentAsString());
        assertThat(error.path("error").path("code").asText())
                .isEqualTo("REGISTRATION_NOT_FOUND");

        Course after = courseRepository.findById(course.getId()).orElseThrow();
        assertThat(after.getAvailableSeats())
                .as("the seat is returned exactly once (3 -> 4, not 3 -> 5)")
                .isEqualTo(4);
        assertThat(registrationRepository.countByCourseId(course.getId())).isEqualTo(1);
        assertThat(after.getAvailableSeats()
                + registrationRepository.countByCourseId(course.getId()))
                .as("availableSeats + registrations == capacity")
                .isEqualTo(5);

        List<AuditLog> rows = auditLogRepository.findAll();
        assertThat(rows).as("one audit row per attempt").hasSize(2);
        assertThat(rows).filteredOn(row -> row.getAction() == AuditAction.DROP
                && row.getOutcome() == AuditOutcome.SUCCESS).hasSize(1);
        assertThat(rows).filteredOn(row -> row.getAction() == AuditAction.DROP
                && row.getOutcome() == AuditOutcome.FAILURE
                && "REGISTRATION_NOT_FOUND".equals(row.getDetail())).hasSize(1);
        assertThat(rows).allSatisfy(row -> assertThat(row.getStudentId()).isEqualTo("32012345"));
    }
}
