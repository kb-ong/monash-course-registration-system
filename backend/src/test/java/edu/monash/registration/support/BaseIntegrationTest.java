package edu.monash.registration.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.monash.registration.domain.Student;
import edu.monash.registration.repository.AuditLogRepository;
import edu.monash.registration.repository.CourseRepository;
import edu.monash.registration.repository.RegistrationRepository;
import edu.monash.registration.repository.StudentRepository;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Shared full-context MockMvc harness. Every subclass runs against H2, the {@code test} profile
 * (seeder off), a controllable clock and a freshly cleaned database.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSupportConfig.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class BaseIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected TestFixtures fixtures;

    @Autowired
    protected StudentRepository studentRepository;

    @Autowired
    protected CourseRepository courseRepository;

    @Autowired
    protected RegistrationRepository registrationRepository;

    @Autowired
    protected AuditLogRepository auditLogRepository;

    @BeforeEach
    void resetClockAndDatabase() {
        MutableClock.reset();
        fixtures.clearAll();
    }

    protected String bearer(Student student) {
        return "Bearer " + fixtures.token(student.getStudentId());
    }

    protected String bearer(String studentId) {
        return "Bearer " + fixtures.token(studentId);
    }

    protected String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    protected JsonNode body(ResultActions actions) throws Exception {
        String raw = actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return objectMapper.readTree(raw);
    }

    protected String rawBody(ResultActions actions) throws Exception {
        return actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    protected ResultActions getAuthed(String path, String token) throws Exception {
        return mockMvc.perform(get(path).header("Authorization", token));
    }

    protected ResultActions postRegisterAuthed(String token, UUID courseId) throws Exception {
        return mockMvc.perform(post("/api/registrations")
                .header("Authorization", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"courseId\":\"" + courseId + "\"}"));
    }

    protected ResultActions deleteAuthed(String token, String registrationId) throws Exception {
        return mockMvc.perform(delete("/api/registrations/" + registrationId)
                .header("Authorization", token));
    }

    protected ResultActions login(String studentId, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"studentId\":\"" + studentId + "\",\"password\":\"" + password + "\"}"));
    }

    protected ResultActions performWith(MockHttpServletRequestBuilder request, String token)
            throws Exception {
        return mockMvc.perform(request.header("Authorization", token));
    }
}
