package edu.monash.registration;

import edu.monash.registration.support.BaseIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T-FR1-05: a malformed student id is rejected by bean validation before any BCrypt comparison.
 * A spy is used instead of a timing threshold because wall-clock timing is not a reliable proof.
 */
@DisplayName("FR1 — login validation never reaches BCrypt")
class LoginValidationNoBcryptTest extends BaseIntegrationTest {

    @SpyBean
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void clearStartupInvocations() {
        // AuthService builds a dummy hash during construction (encode call); ignore that.
        Mockito.clearInvocations(passwordEncoder);
    }

    @Test
    @DisplayName("T-FR1-05 seven-digit studentId is 400 and performs no password comparison")
    void tFr1_05_malformedStudentIdDoesNoBcryptWork() throws Exception {
        login("3201234", "Password123!")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.message").value(
                        org.hamcrest.Matchers.containsString("studentId")));

        verify(passwordEncoder, never()).matches(anyString(), anyString());
    }
}
