package edu.monash.registration;

import com.fasterxml.jackson.databind.JsonNode;
import edu.monash.registration.domain.Student;
import edu.monash.registration.support.BaseIntegrationTest;
import edu.monash.registration.support.MutableClock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** FR1 — student login, profile rehydration and token hygiene. */
@DisplayName("FR1 — Student login")
class AuthControllerTest extends BaseIntegrationTest {

    private Student aisha;

    @BeforeEach
    void setUp() {
        // Login issues real tokens whose expiry is validated against the machine clock, so this
        // class runs on real time rather than the frozen business clock.
        MutableClock.set(Instant.now().truncatedTo(ChronoUnit.SECONDS));
        aisha = fixtures.aisha();
    }

    @Test
    @DisplayName("T-FR1-01 valid credentials return a token and a hash-free student")
    void tFr1_01_validLogin() throws Exception {
        MvcResult result = login("32012345", "Password123!")
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.student.studentId").value("32012345"))
                .andExpect(jsonPath("$.student.name").value("Aisha Rahman"))
                .andExpect(jsonPath("$.student.program").value("BCS"))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        String token = body.path("token").asText();
        assertThat(token.split("\\.")).hasSize(3);
        assertNoPasswordMaterial(result.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("T-FR1-02 /me returns the same profile for a login token")
    void tFr1_02_meReturnsProfile() throws Exception {
        JsonNode login = body(login("32012345", "Password123!").andExpect(status().isOk()));
        String token = login.path("token").asText();

        MvcResult me = mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentId").value("32012345"))
                .andExpect(jsonPath("$.name").value("Aisha Rahman"))
                .andExpect(jsonPath("$.email").value("s32012345@student.monash.edu"))
                .andExpect(jsonPath("$.program").value("BCS"))
                .andReturn();

        assertThat(objectMapper.readTree(me.getResponse().getContentAsString()))
                .isEqualTo(login.path("student"));
        assertNoPasswordMaterial(me.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("T-FR1-03 wrong password is a uniform 401")
    void tFr1_03_wrongPassword() throws Exception {
        MvcResult result = login("32012345", "wrong-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.error.message").isNotEmpty())
                .andExpect(jsonPath("$.error.timestamp").isNotEmpty())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andReturn();
        assertNoPasswordMaterial(result.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("T-FR1-04 unknown student is indistinguishable from a wrong password")
    void tFr1_04_unknownStudent() throws Exception {
        JsonNode wrongPassword = body(login("32012345", "wrong-password")
                .andExpect(status().isUnauthorized()));
        JsonNode unknown = body(login("99999999", "whatever")
                .andExpect(status().isUnauthorized()));

        assertThat(unknown.path("error").path("code").asText()).isEqualTo("INVALID_CREDENTIALS");
        assertThat(unknown.path("error").path("code").asText())
                .isEqualTo(wrongPassword.path("error").path("code").asText());
        assertThat(unknown.path("error").path("message").asText())
                .isEqualTo(wrongPassword.path("error").path("message").asText());
    }

    @Test
    @DisplayName("T-FR1-06 empty body is a 400 validation error")
    void tFr1_06_emptyBody() throws Exception {
        MvcResult result = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .post("/api/auth/login")
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("Password123!");
    }

    @Test
    @DisplayName("T-FR1-07 studentId is trimmed before lookup")
    void tFr1_07_paddedStudentId() throws Exception {
        login(" 32012345 ", "Password123!")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.student.studentId").value("32012345"));
    }

    @Test
    @DisplayName("T-FR1-08 password is not trimmed")
    void tFr1_08_passwordWithTrailingSpace() throws Exception {
        login("32012345", "Password123! ")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("T-FR1-09 /me without a token is 401 UNAUTHORIZED with WWW-Authenticate")
    void tFr1_09_meWithoutToken() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("T-FR1-10 a token signed with another secret is rejected, not 500")
    void tFr1_10_wrongSignature() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer "
                                + fixtures.wrongSignatureToken("32012345")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("T-FR1-11 an expired token is 401 UNAUTHORIZED")
    void tFr1_11_expiredToken() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + fixtures.expiredToken("32012345")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("T-FR1-12 no response leaks password material, stack traces or class names")
    void tFr1_12_noLeakage() throws Exception {
        String loginBody = rawBody(login("32012345", "Password123!").andExpect(status().isOk()));
        String failureBody = rawBody(login("32012345", "nope").andExpect(status().isUnauthorized()));
        assertNoPasswordMaterial(loginBody);
        assertNoPasswordMaterial(failureBody);
    }

    private void assertNoPasswordMaterial(String response) {
        assertThat(response)
                .doesNotContain("passwordHash")
                .doesNotContain("password_hash")
                .doesNotContain("$2a$")
                .doesNotContain("$2b$")
                .doesNotContain("Exception")
                .doesNotContain("at edu.monash");
    }
}
