package edu.monash.registration;

import com.fasterxml.jackson.databind.JsonNode;
import edu.monash.registration.domain.Course;
import edu.monash.registration.domain.Student;
import edu.monash.registration.support.BaseIntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** FR2 — catalogue scoping, search, semester selection and course detail. */
@DisplayName("FR2 — View available courses")
class CatalogControllerTest extends BaseIntegrationTest {

    private Student aisha;

    @BeforeEach
    void seedCatalogue() {
        fixtures.seedSpecBaseline();
        aisha = studentRepository.findByStudentId("32012345").orElseThrow();
    }

    @Test
    @DisplayName("T-FR2-01 default catalogue is the active semester, 16 items, code ASC")
    void tFr2_01_defaultCatalogue() throws Exception {
        JsonNode courses = body(getAuthed("/api/courses", bearer(aisha)).andExpect(status().isOk()));

        assertThat(courses.size()).isEqualTo(16);
        List<String> codes = new ArrayList<>();
        for (JsonNode course : courses) {
            assertThat(course.path("semester").asText()).isEqualTo("2026-S2");
            assertThat(course.path("schedule").size()).isGreaterThan(0);
            codes.add(course.path("code").asText());
        }
        assertThat(codes).isSorted();
        assertThat(codes).doesNotContain("FIT1008", "FIT1047", "MTH1020");
    }

    @Test
    @DisplayName("T-FR2-02 search by exact code returns exactly that course")
    void tFr2_02_searchByCode() throws Exception {
        JsonNode courses = body(getAuthed("/api/courses?semester=2026-S2&q=FIT1045", bearer(aisha))
                .andExpect(status().isOk()));
        assertThat(courses.size()).isEqualTo(1);
        assertThat(courses.get(0).path("code").asText()).isEqualTo("FIT1045");
    }

    @Test
    @DisplayName("T-FR2-03 search is case-insensitive and never leaks prior semesters")
    void tFr2_03_mixedCaseSearch() throws Exception {
        JsonNode courses = body(getAuthed("/api/courses?q=fIt10", bearer(aisha))
                .andExpect(status().isOk()));
        assertThat(courses.size()).isGreaterThan(0);
        for (JsonNode course : courses) {
            assertThat(course.path("code").asText().toLowerCase(Locale.ROOT)).contains("fit10");
        }
        assertThat(codes(courses)).doesNotContain("FIT1047");
    }

    @Test
    @DisplayName("T-FR2-04 search matches course name case-insensitively")
    void tFr2_04_searchByName() throws Exception {
        JsonNode courses = body(getAuthed("/api/courses?q=algorithms", bearer(aisha))
                .andExpect(status().isOk()));
        assertThat(codes(courses)).contains("FIT2004");
    }

    @Test
    @DisplayName("T-FR2-05 whitespace-only q returns the whole catalogue")
    void tFr2_05_blankQuery() throws Exception {
        JsonNode courses = body(mockMvc.perform(get("/api/courses")
                        .param("q", "  ")
                        .header("Authorization", bearer(aisha)))
                .andExpect(status().isOk()));
        assertThat(courses.size()).isEqualTo(16);
    }

    @Test
    @DisplayName("T-FR2-06 no matches is 200 [] rather than 404")
    void tFr2_06_noMatches() throws Exception {
        mockMvc.perform(get("/api/courses?q=zzzz").header("Authorization", bearer(aisha)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("T-FR2-07 unknown semester is a 400 VALIDATION_ERROR")
    void tFr2_07_unknownSemester() throws Exception {
        mockMvc.perform(get("/api/courses?semester=2025-S1").header("Authorization", bearer(aisha)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("T-FR2-08 q longer than 100 characters is a 400 VALIDATION_ERROR")
    void tFr2_08_queryTooLong() throws Exception {
        String q = "a".repeat(101);
        mockMvc.perform(get("/api/courses?q=" + q).header("Authorization", bearer(aisha)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("T-FR2-09 catalogue requires authentication")
    void tFr2_09_requiresAuth() throws Exception {
        mockMvc.perform(get("/api/courses"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("T-FR2-10 course detail returns both slots ordered by day then start")
    void tFr2_10_multiSlotDetail() throws Exception {
        Course fit1045 = fixtures.courseByCode("FIT1045");
        JsonNode course = body(getAuthed("/api/courses/" + fit1045.getId(), bearer(aisha))
                .andExpect(status().isOk()));

        assertThat(course.path("schedule").size()).isEqualTo(2);
        assertThat(course.path("schedule").get(0).path("day").asText()).isEqualTo("MON");
        assertThat(course.path("schedule").get(1).path("day").asText()).isEqualTo("WED");
    }

    @Test
    @DisplayName("T-FR2-11 unknown course id is 404 COURSE_NOT_FOUND")
    void tFr2_11_unknownCourse() throws Exception {
        mockMvc.perform(get("/api/courses/00000000-0000-0000-0000-000000000000")
                        .header("Authorization", bearer(aisha)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("COURSE_NOT_FOUND"));
    }

    @Test
    @DisplayName("T-FR2-11b non-UUID course id is 404, not 400")
    void tFr2_11b_nonUuidCourseId() throws Exception {
        mockMvc.perform(get("/api/courses/not-a-uuid").header("Authorization", bearer(aisha)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("COURSE_NOT_FOUND"));
    }

    @Test
    @DisplayName("T-FR2-12 semesters is public, ordered DESC and flags the active one")
    void tFr2_12_semesters() throws Exception {
        JsonNode semesters = body(mockMvc.perform(get("/api/semesters"))
                .andExpect(status().isOk()));
        assertThat(semesters.size()).isEqualTo(2);
        assertThat(semesters.get(0).path("code").asText()).isEqualTo("2026-S2");
        assertThat(semesters.get(0).path("active").asBoolean()).isTrue();
        assertThat(semesters.get(1).path("code").asText()).isEqualTo("2026-S1");
        assertThat(semesters.get(1).path("active").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("T-FR2-13 explicit prior semester returns its 3 courses only")
    void tFr2_13_priorSemester() throws Exception {
        JsonNode courses = body(getAuthed("/api/courses?semester=2026-S1", bearer(aisha))
                .andExpect(status().isOk()));
        assertThat(courses.size()).isEqualTo(3);
        for (JsonNode course : courses) {
            assertThat(course.path("semester").asText()).isEqualTo("2026-S1");
        }
        assertThat(codes(courses)).doesNotContain("FIT1045");
    }

    private List<String> codes(JsonNode courses) {
        List<String> codes = new ArrayList<>();
        courses.forEach(c -> codes.add(c.path("code").asText()));
        return codes;
    }
}
