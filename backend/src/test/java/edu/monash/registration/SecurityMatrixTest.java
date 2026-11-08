package edu.monash.registration;

import com.fasterxml.jackson.databind.JsonNode;
import edu.monash.registration.domain.Course;
import edu.monash.registration.domain.Student;
import edu.monash.registration.support.BaseIntegrationTest;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** NFR2 — authentication matrix, leak scan, error-envelope hygiene and CORS. */
@DisplayName("NFR2 — Security")
class SecurityMatrixTest extends BaseIntegrationTest {

    private Student aisha;
    private Course course;
    private String registrationId;

    @BeforeEach
    void seed() throws Exception {
        aisha = fixtures.aisha();
        course = fixtures.singleSlotCourse("FIT1045", 6, 50, 50);
        JsonNode created = body(postRegisterAuthed(bearer(aisha), course.getId())
                .andExpect(status().isCreated()));
        registrationId = created.path("registrationId").asText();
    }

    enum TokenKind {
        NONE, MALFORMED, EXPIRED, WRONG_SIGNATURE, NOT_BEARER
    }

    enum Route {
        ME("GET", "/api/auth/me", false),
        COURSES("GET", "/api/courses", false),
        COURSE_DETAIL("GET", "/api/courses/{courseId}", false),
        REGISTRATIONS("GET", "/api/registrations", false),
        REGISTER("POST", "/api/registrations", true),
        DROP("DELETE", "/api/registrations/{registrationId}", false),
        EXPORT("GET", "/api/registrations/export", false);

        final String method;
        final String path;
        final boolean hasBody;

        Route(String method, String path, boolean hasBody) {
            this.method = method;
            this.path = path;
            this.hasBody = hasBody;
        }
    }

    static Stream<Arguments> protectedRoutes() {
        Stream.Builder<Arguments> builder = Stream.builder();
        for (Route route : Route.values()) {
            for (TokenKind token : TokenKind.values()) {
                builder.add(Arguments.of(route, token));
            }
        }
        return builder.build();
    }

    @ParameterizedTest(name = "T-NFR2-03 {0} with {1} -> 401 UNAUTHORIZED")
    @MethodSource("protectedRoutes")
    @DisplayName("T-NFR2-03 every protected route rejects every unusable token")
    void tNfr2_03_unauthorizedMatrix(Route route, TokenKind tokenKind) throws Exception {
        MockHttpServletRequestBuilder request = requestFor(route);
        switch (tokenKind) {
            case NONE -> {
                // no header
            }
            case MALFORMED -> request.header("Authorization", "Bearer " + fixtures.malformedToken());
            case EXPIRED -> request.header("Authorization",
                    "Bearer " + fixtures.expiredToken("32012345"));
            case WRONG_SIGNATURE -> request.header("Authorization",
                    "Bearer " + fixtures.wrongSignatureToken("32012345"));
            case NOT_BEARER -> request.header("Authorization", "Token abcdef");
        }

        mockMvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.error.message").isNotEmpty())
                .andExpect(jsonPath("$.error.timestamp").isNotEmpty());
    }

    @Test
    @DisplayName("T-NFR2-01 passwords are stored as BCrypt cost 10, never in plaintext")
    void tNfr2_01_passwordStorage() {
        Student stored = studentRepository.findByStudentId("32012345").orElseThrow();
        assertThat(stored.getPasswordHash())
                .startsWith("$2a$10$")
                .hasSize(60)
                .isNotEqualTo("Password123!");
    }

    @Test
    @DisplayName("T-NFR2-02 no endpoint leaks password material")
    void tNfr2_02_noPasswordLeak() throws Exception {
        List<String> responses = List.of(
                rawBody(login("32012345", "Password123!").andExpect(status().isOk())),
                rawBody(getAuthed("/api/auth/me", bearer(aisha)).andExpect(status().isOk())),
                rawBody(getAuthed("/api/courses", bearer(aisha)).andExpect(status().isOk())),
                rawBody(getAuthed("/api/registrations", bearer(aisha)).andExpect(status().isOk())),
                rawBody(getAuthed("/api/registrations/export", bearer(aisha))
                        .andExpect(status().isOk())),
                rawBody(login("32012345", "wrong").andExpect(status().isUnauthorized())));
        for (String response : responses) {
            assertThat(response)
                    .doesNotContain("passwordHash")
                    .doesNotContain("password_hash")
                    .doesNotContain("$2a$")
                    .doesNotContain("$2b$");
        }
    }

    @Test
    @DisplayName("T-NFR2-05 errors use only code/message/timestamp and never leak internals")
    void tNfr2_05_errorEnvelopeHygiene() throws Exception {
        List<MvcResult> results = List.of(
                mockMvc.perform(get("/api/courses/00000000-0000-0000-0000-000000000000")
                        .header("Authorization", bearer(aisha))).andReturn(),
                mockMvc.perform(post("/api/registrations")
                        .header("Authorization", bearer(aisha))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":\"nope\"}")).andReturn());

        for (MvcResult result : results) {
            String body = result.getResponse().getContentAsString();
            JsonNode error = objectMapper.readTree(body).path("error");
            List<String> fields = new java.util.ArrayList<>();
            error.fieldNames().forEachRemaining(fields::add);
            assertThat(fields).containsExactlyInAnyOrder("code", "message", "timestamp");
            assertThat(body)
                    .doesNotContain("trace")
                    .doesNotContain("\"exception\"")
                    .doesNotContain("at edu.monash")
                    .doesNotContain("org.springframework");
        }
    }

    @Test
    @DisplayName("T-NFR2-07 an evil origin gets no CORS approval")
    void tNfr2_07_evilOrigin() throws Exception {
        MvcResult result = mockMvc.perform(options("/api/courses")
                        .header("Origin", "http://evil.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andReturn();
        assertThat(result.getResponse().getHeader("Access-Control-Allow-Origin")).isNull();
        assertThat(result.getResponse().getStatus()).isIn(200, 403);
    }

    @Test
    @DisplayName("T-NFR2-08 the dev frontend origin is allowed with Authorization")
    void tNfr2_08_allowedOrigin() throws Exception {
        mockMvc.perform(options("/api/courses")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "Authorization"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Access-Control-Allow-Headers",
                                org.hamcrest.Matchers.containsStringIgnoringCase("Authorization")));
    }

    @Test
    @DisplayName("T-NFR2-10 SQL-looking courseId is rejected as a validation error")
    void tNfr2_10_sqlInjectionAttempt() throws Exception {
        String body = rawBody(mockMvc.perform(post("/api/registrations")
                        .header("Authorization", bearer(aisha))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"courseId\":\"1' OR '1'='1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR")));

        assertThat(body).doesNotContain("SQL").doesNotContain("syntax")
                .doesNotContain("org.h2");
    }

    @Test
    @DisplayName("T-M1-01b the H2 console is not reachable under the test profile either")
    void tM1_01b_h2ConsoleNotExposedUnderTestProfile() throws Exception {
        for (String path : List.of("/h2-console/", "/h2-console/login.do")) {
            MvcResult result = mockMvc.perform(get(path)).andReturn();
            assertThat(result.getResponse().getStatus())
                    .as("%s must never serve the console", path)
                    .isIn(401, 403, 404);
        }
    }

    private MockHttpServletRequestBuilder requestFor(Route route) {
        String path = route.path
                .replace("{courseId}", course.getId().toString())
                .replace("{registrationId}", registrationId);
        MockHttpServletRequestBuilder request = switch (route.method) {
            case "POST" -> post(path).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"courseId\":\"" + course.getId() + "\"}");
            case "DELETE" -> delete(path);
            default -> get(path);
        };
        return request;
    }
}
