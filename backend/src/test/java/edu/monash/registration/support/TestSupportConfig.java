package edu.monash.registration.support;

import edu.monash.registration.config.JwtProperties;
import edu.monash.registration.repository.AuditLogRepository;
import edu.monash.registration.repository.CourseRepository;
import edu.monash.registration.repository.RegistrationRepository;
import edu.monash.registration.repository.StudentRepository;
import java.time.Clock;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Test-only beans. The {@code test} profile disables {@code DataSeeder}; fixtures are created by
 * {@link TestFixtures} instead so every class owns a deterministic data set.
 */
@TestConfiguration
@Profile("test")
public class TestSupportConfig {

    @Bean
    @Primary
    public Clock testClock() {
        return new MutableClock();
    }

    @Bean
    public TestFixtures testFixtures(StudentRepository studentRepository,
            CourseRepository courseRepository, RegistrationRepository registrationRepository,
            AuditLogRepository auditLogRepository, PasswordEncoder passwordEncoder,
            JwtProperties jwtProperties) {
        return new TestFixtures(studentRepository, courseRepository, registrationRepository,
                auditLogRepository, passwordEncoder, jwtProperties);
    }
}
