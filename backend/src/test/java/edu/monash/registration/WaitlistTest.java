package edu.monash.registration;

import com.fasterxml.jackson.databind.JsonNode;
import edu.monash.registration.domain.AuditAction;
import edu.monash.registration.domain.AuditLog;
import edu.monash.registration.domain.AuditOutcome;
import edu.monash.registration.domain.Course;
import edu.monash.registration.domain.Notification;
import edu.monash.registration.domain.NotificationType;
import edu.monash.registration.domain.Registration;
import edu.monash.registration.domain.Student;
import edu.monash.registration.support.BaseIntegrationTest;
import edu.monash.registration.support.MutableClock;
import edu.monash.registration.support.TestFixtures;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FR11 — waitlist management. Covers joining a full course, derived positions, FIFO promotion on a
 * drop, eligibility (prerequisites and credit cap), notifications and audit.
 */
@DisplayName("FR11 — Waitlist management")
class WaitlistTest extends BaseIntegrationTest {

    private Student aisha;
    private Student daniel;
    private Student priya;

    @BeforeEach
    void seed() {
        aisha = fixtures.aisha();
        daniel = fixtures.daniel();
        priya = fixtures.student("32011111", "Priya Nair", "BBIS");
    }

    @Test
    @DisplayName("T-FR11-01 joining a full course yields position 1, a notification and an audit row")
    void tFr11_01_joinFullCourse() throws Exception {
        Course course = fixtures.seededFullCourse("FIT3182", daniel);

        JsonNode response = body(join(aisha, course).andExpect(status().isCreated()));

        assertThat(response.path("position").asInt()).isEqualTo(1);
        assertThat(response.path("waitlistSize").asInt()).isEqualTo(1);
        assertThat(response.path("course").path("code").asText()).isEqualTo("FIT3182");
        assertThat(response.path("message").asText()).contains("#1").contains("FIT3182");
        assertThat(registrationRepository.existsByStudentIdAndCourseId(aisha.getId(), course.getId()))
                .isFalse();
        assertThat(courseRepository.findById(course.getId()).orElseThrow().getAvailableSeats())
                .isZero();

        List<AuditLog> audit = auditLogRepository.findByStudentIdOrderByIdAsc("32012345");
        assertThat(audit).hasSize(1);
        assertThat(audit.get(0).getAction()).isEqualTo(AuditAction.WAITLIST_JOIN);
        assertThat(audit.get(0).getOutcome()).isEqualTo(AuditOutcome.SUCCESS);
        assertThat(audit.get(0).getCourseCode()).isEqualTo("FIT3182");

        List<Notification> notifications = notificationRepository.findAll();
        assertThat(notifications).hasSize(1);
        assertThat(notifications.get(0).getType()).isEqualTo(NotificationType.WAITLIST_JOINED);
        assertThat(notifications.get(0).getMessage()).contains("#1").contains("FIT3182");
        assertThat(notifications.get(0).isRead()).isFalse();
    }

    @Test
    @DisplayName("T-FR11-02 queue positions are 1, 2, 3 in join order and each student sees their own")
    void tFr11_02_positions() throws Exception {
        Course course = fixtures.seededFullCourse("FIT3182", daniel);

        assertThat(body(join(aisha, course).andExpect(status().isCreated()))
                .path("position").asInt()).isEqualTo(1);
        assertThat(body(join(priya, course).andExpect(status().isCreated()))
                .path("position").asInt()).isEqualTo(2);

        JsonNode aishaList = body(getAuthed("/api/waitlist", bearer(aisha)));
        assertThat(aishaList.path("semester").asText()).isEqualTo("2026-S2");
        assertThat(aishaList.path("items")).hasSize(1);
        assertThat(aishaList.path("items").get(0).path("position").asInt()).isEqualTo(1);
        assertThat(aishaList.path("items").get(0).path("course").path("code").asText())
                .isEqualTo("FIT3182");

        JsonNode priyaList = body(getAuthed("/api/waitlist", bearer(priya)));
        assertThat(priyaList.path("items").get(0).path("position").asInt()).isEqualTo(2);
    }

    @Test
    @DisplayName("T-FR11-03 a course with a free seat rejects a waitlist join with SEAT_AVAILABLE")
    void tFr11_03_seatAvailable() throws Exception {
        Course course = fixtures.singleSlotCourse("FIT1045", 6, 50, 50);

        join(aisha, course)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("SEAT_AVAILABLE"));

        assertThat(waitlistRepository.count()).isZero();
        assertThat(auditLogRepository.findByStudentIdOrderByIdAsc("32012345"))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.getAction()).isEqualTo(AuditAction.WAITLIST_REJECTED);
                    assertThat(row.getOutcome()).isEqualTo(AuditOutcome.FAILURE);
                    assertThat(row.getDetail()).isEqualTo("SEAT_AVAILABLE");
                    assertThat(row.getCourseCode()).isEqualTo("FIT1045");
                });
    }

    @Test
    @DisplayName("T-FR11-04 a duplicate join is 409 ALREADY_WAITLISTED and leaves one queue row")
    void tFr11_04_duplicate() throws Exception {
        Course course = fixtures.seededFullCourse("FIT3182", daniel);
        join(aisha, course).andExpect(status().isCreated());

        join(aisha, course)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ALREADY_WAITLISTED"));

        assertThat(waitlistRepository.count()).isEqualTo(1);
        assertThat(auditLogRepository.findByStudentIdOrderByIdAsc("32012345"))
                .filteredOn(row -> row.getAction() == AuditAction.WAITLIST_REJECTED)
                .singleElement()
                .satisfies(row -> assertThat(row.getDetail()).isEqualTo("ALREADY_WAITLISTED"));
    }

    @Test
    @DisplayName("T-FR11-05 an already registered student cannot join that course's waitlist")
    void tFr11_05_alreadyRegistered() throws Exception {
        Course course = fixtures.singleSlotCourse("FIT3182", 6, 2, 0);
        fixtures.registration(aisha, course);
        fixtures.registration(daniel, course);

        join(aisha, course)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ALREADY_REGISTERED"));

        assertThat(waitlistRepository.count()).isZero();
    }

    @Test
    @DisplayName("T-FR11-06 a missing prerequisite is 409 PREREQUISITE_NOT_MET, naming the code")
    void tFr11_06_missingPrerequisite() throws Exception {
        Course prerequisite = fixtures.singleSlotCourse("FIT1008", 6, 80, 80);
        Course course = fixtures.seededFullCourse("FIT2004", daniel);
        fixtures.requires(course, prerequisite);

        String body = rawBody(join(priya, course)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PREREQUISITE_NOT_MET")));

        assertThat(body).contains("FIT1008");
        assertThat(waitlistRepository.count()).isZero();
        assertThat(auditLogRepository.findByStudentIdOrderByIdAsc("32011111"))
                .singleElement()
                .satisfies(row -> assertThat(row.getDetail()).isEqualTo("PREREQUISITE_NOT_MET"));
    }

    @Test
    @DisplayName("T-FR11-07 a student at the credit cap cannot join a waitlist")
    void tFr11_07_creditLimit() throws Exception {
        for (int index = 1; index <= 4; index++) {
            Course enrolled = fixtures.singleSlotCourse("TST2" + index + "0", 6, 20, 19);
            fixtures.registration(aisha, enrolled);
        }
        Course full = fixtures.seededFullCourse("FIT3182", daniel);

        join(aisha, full)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CREDIT_LIMIT_EXCEEDED"));

        assertThat(waitlistRepository.count()).isZero();
    }

    @Test
    @DisplayName("T-FR11-08 a closed registration window blocks joins and is audited")
    void tFr11_08_closedWindow() throws Exception {
        Course course = fixtures.seededFullCourse("FIT3182", daniel);
        MutableClock.setOffset("2026-12-21T00:00:00+08:00");

        join(aisha, course)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("REGISTRATION_CLOSED"));

        assertThat(waitlistRepository.count()).isZero();
        assertThat(auditLogRepository.findByStudentIdOrderByIdAsc("32012345"))
                .singleElement()
                .satisfies(row -> {
                    assertThat(row.getAction()).isEqualTo(AuditAction.WAITLIST_REJECTED);
                    assertThat(row.getDetail()).isEqualTo("REGISTRATION_CLOSED");
                });
    }

    @Test
    @DisplayName("T-FR11-09 an unknown course id is 404 COURSE_NOT_FOUND")
    void tFr11_09_unknownCourse() throws Exception {
        join(aisha, UUID.randomUUID())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("COURSE_NOT_FOUND"));

        assertThat(waitlistRepository.count()).isZero();
    }

    @Test
    @DisplayName("T-FR11-10 a course in another semester is 404 with detail SEMESTER_NOT_ACTIVE")
    void tFr11_10_semesterScope() throws Exception {
        Course prior = fixtures.course("MTH1020", "Analysis of Change", 6, "Dr Test", 60, 59,
                "2026-S1", "Science",
                List.of(TestFixtures.Slot.of("THU", "14:00", "16:00", "Clayton, Building 28, Rm 118")));
        fixtures.registration(daniel, prior);

        join(aisha, prior)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("COURSE_NOT_FOUND"));

        assertThat(waitlistRepository.count()).isZero();
        assertThat(auditLogRepository.findByStudentIdOrderByIdAsc("32012345"))
                .singleElement()
                .satisfies(row -> assertThat(row.getDetail()).isEqualTo("SEMESTER_NOT_ACTIVE"));
    }

    @Test
    @DisplayName("T-FR11-11 leaving removes the entry and shifts everybody behind up one position")
    void tFr11_11_leaveShiftsPositions() throws Exception {
        Course course = fixtures.seededFullCourse("FIT3182", daniel);
        String aishaEntry = body(join(aisha, course).andExpect(status().isCreated()))
                .path("waitlistId").asText();
        assertThat(body(join(priya, course).andExpect(status().isCreated()))
                .path("position").asInt()).isEqualTo(2);

        mockMvc.perform(delete("/api/waitlist/" + aishaEntry)
                        .header("Authorization", bearer(aisha)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("FIT3182")));

        assertThat(body(getAuthed("/api/waitlist", bearer(aisha))).path("items")).isEmpty();
        assertThat(body(getAuthed("/api/waitlist", bearer(priya)))
                .path("items").get(0).path("position").asInt()).isEqualTo(1);
        assertThat(waitlistRepository.count()).isEqualTo(1);
        assertThat(auditLogRepository.findByStudentIdOrderByIdAsc("32012345"))
                .filteredOn(row -> row.getAction() == AuditAction.WAITLIST_LEAVE)
                .singleElement()
                .satisfies(row -> assertThat(row.getOutcome()).isEqualTo(AuditOutcome.SUCCESS));
    }

    @Test
    @DisplayName("T-FR11-12 another student's or a malformed entry id is 404 WAITLIST_NOT_FOUND")
    void tFr11_12_leaveNotOwned() throws Exception {
        Course course = fixtures.seededFullCourse("FIT3182", daniel);
        String priyaEntry = body(join(priya, course).andExpect(status().isCreated()))
                .path("waitlistId").asText();

        mockMvc.perform(delete("/api/waitlist/" + priyaEntry)
                        .header("Authorization", bearer(aisha)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("WAITLIST_NOT_FOUND"));
        mockMvc.perform(delete("/api/waitlist/not-a-number")
                        .header("Authorization", bearer(aisha)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("WAITLIST_NOT_FOUND"));

        assertThat(waitlistRepository.count()).isEqualTo(1);
        assertThat(auditLogRepository.findByStudentIdOrderByIdAsc("32012345"))
                .filteredOn(row -> row.getAction() == AuditAction.WAITLIST_LEAVE)
                .hasSize(2)
                .allSatisfy(row -> assertThat(row.getOutcome()).isEqualTo(AuditOutcome.FAILURE));
    }

    @Test
    @DisplayName("T-FR11-13 a drop promotes the first waitlisted student in the same transaction")
    void tFr11_13_dropPromotesHead() throws Exception {
        Course course = fixtures.seededFullCourse("FIT3182", daniel);
        join(aisha, course).andExpect(status().isCreated());
        join(priya, course).andExpect(status().isCreated());

        deleteAuthed(bearer(daniel), registrationId(daniel, course))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableSeats").value(0));

        assertThat(registrationRepository.existsByStudentIdAndCourseId(aisha.getId(), course.getId()))
                .as("the first student in the queue receives the seat")
                .isTrue();
        assertThat(registrationRepository.existsByStudentIdAndCourseId(priya.getId(), course.getId()))
                .isFalse();
        assertThat(waitlistRepository.count()).isEqualTo(1);
        assertThat(body(getAuthed("/api/waitlist", bearer(priya)))
                .path("items").get(0).path("position").asInt()).isEqualTo(1);

        Course after = courseRepository.findById(course.getId()).orElseThrow();
        assertThat(after.getAvailableSeats()).isZero();
        assertThat(after.getAvailableSeats()
                + registrationRepository.countByCourseId(course.getId())).isEqualTo(1);

        assertThat(auditLogRepository.findByStudentIdOrderByIdAsc("32012345"))
                .filteredOn(row -> row.getAction() == AuditAction.WAITLIST_PROMOTED)
                .singleElement()
                .satisfies(row -> assertThat(row.getOutcome()).isEqualTo(AuditOutcome.SUCCESS));
        assertThat(notificationRepository.findForStudent(aisha.getId()))
                .anySatisfy(n -> {
                    assertThat(n.getType()).isEqualTo(NotificationType.WAITLIST_PROMOTED);
                    assertThat(n.getMessage()).contains("enrolled automatically");
                });
    }

    @Test
    @DisplayName("T-FR11-14 each further drop promotes the next student and keeps C1")
    void tFr11_14_secondDropPromotesNext() throws Exception {
        Student fourth = fixtures.student("32055555", "Fourth Student", "BCS");
        Course course = fixtures.singleSlotCourse("FIT3182", 6, 2, 0);
        fixtures.registration(daniel, course);
        fixtures.registration(fourth, course);
        join(aisha, course).andExpect(status().isCreated());
        join(priya, course).andExpect(status().isCreated());

        deleteAuthed(bearer(daniel), registrationId(daniel, course)).andExpect(status().isOk());
        assertThat(registrationRepository.existsByStudentIdAndCourseId(aisha.getId(), course.getId()))
                .isTrue();
        assertThat(body(getAuthed("/api/waitlist", bearer(priya)))
                .path("items").get(0).path("position").asInt()).isEqualTo(1);

        deleteAuthed(bearer(fourth), registrationId(fourth, course)).andExpect(status().isOk());
        assertThat(registrationRepository.existsByStudentIdAndCourseId(priya.getId(), course.getId()))
                .isTrue();
        assertThat(waitlistRepository.count()).isZero();
        Course after = courseRepository.findById(course.getId()).orElseThrow();
        assertThat(after.getAvailableSeats()).isZero();
        assertThat(after.getAvailableSeats()
                + registrationRepository.countByCourseId(course.getId())).isEqualTo(2);
    }

    @Test
    @DisplayName("T-FR11-15 a drop with nobody queued still releases the seat")
    void tFr11_15_dropWithoutQueue() throws Exception {
        Course course = fixtures.singleSlotCourse("FIT1045", 6, 20, 19);
        fixtures.registration(daniel, course);

        deleteAuthed(bearer(daniel), registrationId(daniel, course))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableSeats").value(20));

        assertThat(waitlistRepository.count()).isZero();
    }

    @Test
    @DisplayName("T-FR11-16 an ineligible head is skipped so the seat reaches the next student")
    void tFr11_16_promotionSkipsIneligible() throws Exception {
        Course course = fixtures.seededFullCourse("FIT3182", daniel);
        join(aisha, course).andExpect(status().isCreated());
        join(priya, course).andExpect(status().isCreated());
        // Aisha reaches the 24-credit cap after joining; the queue is persistent, eligibility is
        // re-evaluated at promotion time.
        for (int index = 1; index <= 4; index++) {
            Course enrolled = fixtures.singleSlotCourse("TST3" + index + "0", 6, 20, 19);
            fixtures.registration(aisha, enrolled);
        }

        deleteAuthed(bearer(daniel), registrationId(daniel, course)).andExpect(status().isOk());

        assertThat(registrationRepository.existsByStudentIdAndCourseId(aisha.getId(), course.getId()))
                .as("the ineligible head is not enrolled")
                .isFalse();
        assertThat(registrationRepository.existsByStudentIdAndCourseId(priya.getId(), course.getId()))
                .as("the next eligible student receives the seat")
                .isTrue();
        assertThat(waitlistRepository.count())
                .as("the skipped entry stays queued and is not silently deleted")
                .isEqualTo(1);
        assertThat(body(getAuthed("/api/waitlist", bearer(aisha)))
                .path("items").get(0).path("position").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("T-FR11-21 a queued student whose timetable now clashes is skipped at promotion")
    void tFr11_21_promotionSkipsClash() throws Exception {
        Course course = fixtures.seededFullCourse("FIT3182", daniel);
        join(aisha, course).andExpect(status().isCreated());
        join(priya, course).andExpect(status().isCreated());
        // The seeded-full course is MON 09:00-11:00; give Aisha an overlapping registration after
        // she joined. She is still within the credit limit, so FR9 is the only reason to skip her.
        Course clashing = fixtures.singleSlotCourse("TST0601", 6, 20, 19);
        fixtures.registration(aisha, clashing);

        deleteAuthed(bearer(daniel), registrationId(daniel, course)).andExpect(status().isOk());

        assertThat(registrationRepository.existsByStudentIdAndCourseId(aisha.getId(), course.getId()))
                .as("the clashing head is not enrolled")
                .isFalse();
        assertThat(registrationRepository.existsByStudentIdAndCourseId(priya.getId(), course.getId()))
                .as("the next eligible student receives the seat")
                .isTrue();
        assertThat(waitlistRepository.count()).isEqualTo(1);
        assertThat(body(getAuthed("/api/waitlist", bearer(aisha)))
                .path("items").get(0).path("position").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("T-FR11-17 a successful direct registration clears a stale queue place")
    void tFr11_17_registerClearsWaitlist() throws Exception {
        Course course = fixtures.singleSlotCourse("FIT2004", 6, 2, 1);
        fixtures.registration(daniel, course);
        fixtures.waitlist(aisha, course);

        postRegisterAuthed(bearer(aisha), course.getId()).andExpect(status().isCreated());

        assertThat(waitlistRepository.count()).isZero();
    }

    @Test
    @DisplayName("T-FR11-18 notifications are listed, unread-counted, markable and private")
    void tFr11_18_notifications() throws Exception {
        Course course = fixtures.seededFullCourse("FIT3182", daniel);
        join(aisha, course).andExpect(status().isCreated());
        join(priya, course).andExpect(status().isCreated());

        JsonNode inbox = body(getAuthed("/api/notifications", bearer(aisha)));
        assertThat(inbox.path("unreadCount").asInt()).isEqualTo(1);
        assertThat(inbox.path("items")).hasSize(1);
        assertThat(inbox.path("items").get(0).path("type").asText()).isEqualTo("WAITLIST_JOINED");
        assertThat(inbox.path("items").get(0).path("courseCode").asText()).isEqualTo("FIT3182");
        assertThat(inbox.path("items").get(0).path("read").asBoolean()).isFalse();
        String aishaNotificationId = inbox.path("items").get(0).path("id").asText();

        // Another student's notification is invisible and not markable.
        mockMvc.perform(post("/api/notifications/" + aishaNotificationId + "/read")
                        .header("Authorization", bearer(priya)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOTIFICATION_NOT_FOUND"));

        mockMvc.perform(post("/api/notifications/" + aishaNotificationId + "/read")
                        .header("Authorization", bearer(aisha)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(0));

        JsonNode after = body(getAuthed("/api/notifications", bearer(aisha)));
        assertThat(after.path("unreadCount").asInt()).isZero();
        assertThat(after.path("items").get(0).path("read").asBoolean()).isTrue();
        assertThat(body(getAuthed("/api/notifications", bearer(priya)))
                .path("unreadCount").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("T-FR11-19 every waitlist and notification endpoint requires a bearer token")
    void tFr11_19_auth() throws Exception {
        mockMvc.perform(get("/api/waitlist")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/waitlist")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/waitlist/1")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/notifications/1/read")).andExpect(status().isUnauthorized());
    }

    private ResultActions join(Student student, Course course) throws Exception {
        return mockMvc.perform(post("/api/waitlist")
                .header("Authorization", bearer(student))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"courseId\":\"" + course.getId() + "\"}"));
    }

    private ResultActions join(Student student, UUID courseId) throws Exception {
        return mockMvc.perform(post("/api/waitlist")
                .header("Authorization", bearer(student))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"courseId\":\"" + courseId + "\"}"));
    }

    private String registrationId(Student student, Course course) {
        Registration registration = registrationRepository
                .findAllForStudentInSemester(student.getId(), course.getSemester())
                .stream()
                .filter(row -> row.getCourse().getId().equals(course.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No registration for " + student.getStudentId()));
        return registration.getId().toString();
    }
}
