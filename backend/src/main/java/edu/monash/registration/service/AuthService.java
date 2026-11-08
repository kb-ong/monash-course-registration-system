package edu.monash.registration.service;

import edu.monash.registration.api.dto.LoginRequest;
import edu.monash.registration.api.dto.LoginResponse;
import edu.monash.registration.api.dto.StudentDto;
import edu.monash.registration.domain.Student;
import edu.monash.registration.error.InvalidCredentialsException;
import edu.monash.registration.error.UnauthorizedException;
import edu.monash.registration.repository.StudentRepository;
import edu.monash.registration.security.JwtService;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** FR1: credential check, token issue, profile lookup. Never logs a password or a token. */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final StudentRepository studentRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    /** Dummy hash so an unknown student id costs the same BCrypt work as a wrong password (B3). */
    private final String dummyHash;

    public AuthService(StudentRepository studentRepository, PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.studentRepository = studentRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        String studentId = request.studentId().trim();
        Student student = studentRepository.findByStudentId(studentId).orElse(null);

        if (student == null) {
            passwordEncoder.matches(request.password(), dummyHash);
            log.warn("auth.login.failed studentId={} reason=INVALID_CREDENTIALS", truncate(studentId));
            throw new InvalidCredentialsException();
        }

        if (!passwordEncoder.matches(request.password(), student.getPasswordHash())) {
            log.warn("auth.login.failed studentId={} reason=INVALID_CREDENTIALS", truncate(studentId));
            throw new InvalidCredentialsException();
        }

        log.info("auth.login.success studentId={}", studentId);
        return new LoginResponse(jwtService.issueToken(studentId), StudentDto.from(student));
    }

    /** Fresh from the database, not from token claims (FR1-B8). */
    @Transactional(readOnly = true)
    public StudentDto profile(String studentId) {
        Student student = studentRepository.findByStudentId(studentId)
                .orElseThrow(UnauthorizedException::new);
        return StudentDto.from(student);
    }

    private String truncate(String studentId) {
        return studentId.length() <= 12 ? studentId : studentId.substring(0, 12);
    }
}
