package edu.monash.registration;

import edu.monash.registration.repository.CourseRepository;
import edu.monash.registration.support.BaseIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * T-NFR2-06 / T-NFR3-02: an unexpected repository fault becomes a generic 500 INTERNAL_ERROR,
 * never a stack trace or an exception class name.
 */
@DisplayName("NFR2/NFR3 — 500 error envelope")
class ErrorEnvelopeTest extends BaseIntegrationTest {

    @MockBean
    private CourseRepository mockedCourseRepository;

    @Test
    @DisplayName("T-NFR2-06 an unexpected fault returns 500 INTERNAL_ERROR with no internals")
    void tNfr2_06_internalErrorIsOpaque() throws Exception {
        when(mockedCourseRepository.findById(any(UUID.class)))
                .thenThrow(new IllegalStateException("boom-database-failure"));

        MvcResult result = mockMvc.perform(get("/api/courses/" + UUID.randomUUID())
                        .header("Authorization", bearer("32012345")))
                .andExpect(status().isInternalServerError())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.error.message").isNotEmpty())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body)
                .doesNotContain("boom-database-failure")
                .doesNotContain("IllegalStateException")
                .doesNotContain("java.lang")
                .doesNotContain("org.springframework")
                .doesNotContain("at edu.monash")
                .doesNotContain("\"trace\"");
    }
}
