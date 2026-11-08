package edu.monash.registration;

import edu.monash.registration.config.JwtProperties;
import edu.monash.registration.domain.Course;
import edu.monash.registration.domain.Registration;
import edu.monash.registration.repository.CourseRepository;
import edu.monash.registration.repository.RegistrationRepository;
import edu.monash.registration.repository.StudentRepository;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Regression for review findings M-1 (H2 console + committed JWT default) and M-4 (the seeded-full
 * course violated invariant C1).
 *
 * <p>This class deliberately runs on the <em>default</em> profile — the configuration that ships —
 * so {@code DataSeeder} runs and {@code application.yml} (not {@code application-test.yml}) is in
 * force. The test profile's own console exposure is covered by {@code SecurityMatrixTest#tM1_01b}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("REGRESSION M-1/M-4 — shipped profile: console exposure, JWT default, seeded invariants")
class RegressionDefaultProfileTest {

    private static final String COMMITTED_SECRET_MARKER = "change-me-in-production";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private RegistrationRepository registrationRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Test
    @DisplayName("T-M1-01 /h2-console/ is not permitted on the default profile and no JWT secret is committed")
    void tM1_01_consoleClosedAndNoCommittedSecret() throws Exception {
        for (String path : List.of("/h2-console/", "/h2-console/login.do", "/h2-console/query.do")) {
            MvcResult result = mockMvc.perform(get(path)).andReturn();
            int status = result.getResponse().getStatus();
            assertThat(status)
                    .as("%s must never serve the console (401/403/404)", path)
                    .isIn(401, 403, 404);
        }

        // M-1: the committed yml must not carry a usable default signing key.
        String yaml = new String(new ClassPathResource("application.yml").getInputStream()
                .readAllBytes(), StandardCharsets.UTF_8);
        assertThat(yaml).contains("secret: ${JWT_SECRET:}");
        assertThat(yaml).doesNotContain(COMMITTED_SECRET_MARKER);

        String resolved = jwtProperties.getSecret();
        assertThat(resolved == null ? "" : resolved)
                .as("no committed JWT secret is baked into the shipped config")
                .doesNotContain(COMMITTED_SECRET_MARKER);
    }

    @Test
    @DisplayName("T-M4-01 seeded FIT3182 is 1/0 with one held registration, FIT3143 is 1/1/0, 19 courses, C1 everywhere")
    void tM4_01_seedHonoursCapacityInvariant() {
        assertThat(studentRepository.count()).isEqualTo(3);
        assertThat(courseRepository.count()).isEqualTo(19);

        List<Course> active = courseRepository.findBySemesterOrderByCodeAsc("2026-S2");
        assertThat(active).hasSize(16);
        assertThat(courseRepository.findBySemesterOrderByCodeAsc("2026-S1")).hasSize(3);

        Course fit3182 = courseByCode(active, "FIT3182");
        assertThat(fit3182.getCapacity()).isEqualTo(1);
        assertThat(fit3182.getAvailableSeats()).isZero();
        assertThat(registrationRepository.countByCourseId(fit3182.getId())).isEqualTo(1);

        Registration held = registrationRepository.findAll().stream()
                .filter(row -> row.getCourse().getId().equals(fit3182.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("FIT3182 must have exactly one registration"));
        Registration withOwner = registrationRepository
                .findByIdWithStudentAndCourse(held.getId())
                .orElseThrow();
        assertThat(withOwner.getCourse().getCode()).isEqualTo("FIT3182");
        assertThat(withOwner.getStudent().getStudentId()).isEqualTo("32067890");

        Course fit3143 = courseByCode(active, "FIT3143");
        assertThat(fit3143.getCapacity()).isEqualTo(1);
        assertThat(fit3143.getAvailableSeats()).isEqualTo(1);
        assertThat(registrationRepository.countByCourseId(fit3143.getId())).isZero();

        // Invariant C1 for every seeded row - the TD-4 carve-out no longer exists.
        assertThat(courseRepository.count()).isEqualTo(19);
        for (Course course : courseRepository.findAll()) {
            assertThat(course.getAvailableSeats()
                    + registrationRepository.countByCourseId(course.getId()))
                    .as("C1 for seeded %s (%s)", course.getCode(), course.getSemester())
                    .isEqualTo(course.getCapacity());
        }
        assertThat(registrationRepository.count()).as("exactly one seeded registration").isEqualTo(1);
    }

    private Course courseByCode(List<Course> courses, String code) {
        return courses.stream()
                .filter(course -> course.getCode().equals(code))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No seeded course " + code));
    }
}
