package edu.monash.registration.support;

import edu.monash.registration.config.JwtProperties;
import edu.monash.registration.domain.Course;
import edu.monash.registration.domain.CourseSchedule;
import edu.monash.registration.domain.DayOfWeekCode;
import edu.monash.registration.domain.Registration;
import edu.monash.registration.domain.Student;
import edu.monash.registration.repository.AuditLogRepository;
import edu.monash.registration.repository.CourseRepository;
import edu.monash.registration.repository.RegistrationRepository;
import edu.monash.registration.repository.StudentRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Deterministic fixtures for the integration suite. The production seeder is disabled under the
 * {@code test} profile, so each test class builds exactly the rows it needs and starts from a clean
 * database.
 */
public class TestFixtures {

    public static final String PASSWORD = "Password123!";
    public static final String S2 = "2026-S2";
    public static final String S1 = "2026-S1";

    private final StudentRepository studentRepository;
    private final CourseRepository courseRepository;
    private final RegistrationRepository registrationRepository;
    private final AuditLogRepository auditLogRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProperties jwtProperties;
    private final SecretKey signingKey;
    private final String passwordHash;

    public TestFixtures(StudentRepository studentRepository, CourseRepository courseRepository,
            RegistrationRepository registrationRepository, AuditLogRepository auditLogRepository,
            PasswordEncoder passwordEncoder, JwtProperties jwtProperties) {
        this.studentRepository = studentRepository;
        this.courseRepository = courseRepository;
        this.registrationRepository = registrationRepository;
        this.auditLogRepository = auditLogRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtProperties = jwtProperties;
        this.signingKey = Keys.hmacShaKeyFor(
                jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
        // One BCrypt hash is reused: every fixture student has the same demo password.
        this.passwordHash = passwordEncoder.encode(PASSWORD);
    }

    /** Clean every table in dependency order. */
    public void clearAll() {
        auditLogRepository.deleteAll();
        registrationRepository.deleteAll();
        courseRepository.deleteAll();
        studentRepository.deleteAll();
    }

    // ------------------------------------------------------------------ students

    public Student student(String studentId) {
        return student(studentId, "Student " + studentId, "BCS");
    }

    public Student student(String studentId, String name, String program) {
        return studentRepository.save(new Student(studentId, name,
                "s" + studentId + "@student.monash.edu", program, passwordHash, Instant.now()));
    }

    public List<Student> students(String prefix, int count) {
        List<Student> created = new ArrayList<>(count);
        int digits = Math.max(1, 8 - prefix.length());
        for (int i = 1; i <= count; i++) {
            created.add(student(prefix + String.format("%0" + digits + "d", i)));
        }
        return created;
    }

    public Student aisha() {
        return student("32012345", "Aisha Rahman", "BCS");
    }

    public Student daniel() {
        return student("32067890", "Daniel Tan", "BSE");
    }

    // ------------------------------------------------------------------ courses

    public record Slot(String day, String start, String end, String location) {

        public static Slot of(String day, String start, String end, String location) {
            return new Slot(day, start, end, location);
        }
    }

    public Course course(String code, int credits, int capacity, int seats, Slot... slots) {
        return course(code, "Course " + code, credits, "Dr Test", capacity, seats, S2,
                "Information Technology", List.of(slots));
    }

    public Course course(String code, String name, int credits, String instructor, int capacity,
            int seats, String semester, String faculty, List<Slot> slots) {
        Course course = new Course(null, code, name, credits, instructor, capacity, seats, semester,
                faculty, "Fixture course " + code + ".");
        for (Slot slot : slots) {
            course.addSchedule(new CourseSchedule(DayOfWeekCode.valueOf(slot.day()),
                    LocalTime.parse(slot.start()), LocalTime.parse(slot.end()), slot.location()));
        }
        return courseRepository.save(course);
    }

    /** Convenience: a 2026-S2 course with one weekly slot. */
    public Course singleSlotCourse(String code, int credits, int capacity, int seats) {
        return course(code, credits, capacity, seats,
                Slot.of("MON", "09:00", "11:00", "Clayton, Building 25, Rm 101"));
    }

    public Course courseByCode(String code) {
        return courseRepository.findAll().stream()
                .filter(c -> c.getCode().equals(code))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No fixture course " + code));
    }

    /** Directly persist a registration (fixture convenience, no capacity checks). */
    public Registration registration(Student student, Course course) {
        return registrationRepository.save(
                new Registration(student, course, MutableClock.currentInstant()));
    }

    /**
     * The seeded-full course exactly as {@code DataSeeder} builds it after M-4: capacity 1, seats
     * 0 and one real registration holding the only seat, so invariant C1
     * ({@code availableSeats + rows == capacity}) holds without a carve-out.
     */
    public Course seededFullCourse(String code, Student holder) {
        Course course = singleSlotCourse(code, 6, 1, 0);
        registration(holder, course);
        return course;
    }

    // ------------------------------------------------------------------ tokens

    /**
     * A valid HS256 token signed with the application secret. The expiry is deliberately far in
     * the future so FR7 tests can move business time to 2026-10-01 or 2026-12-20 without the token
     * becoming expired as judged by the machine clock.
     */
    public String token(String studentId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(studentId)
                .issuedAt(Date.from(now.minus(5, ChronoUnit.SECONDS)))
                .expiration(Date.from(now.plus(3650, ChronoUnit.DAYS)))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    public String wrongSignatureToken(String studentId) {
        SecretKey other = Keys.hmacShaKeyFor(
                "a-completely-different-test-secret-key-0123456789-abcdefghij"
                        .getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(studentId)
                .issuedAt(Date.from(now.minus(5, ChronoUnit.SECONDS)))
                .expiration(Date.from(now.plus(1, ChronoUnit.DAYS)))
                .signWith(other, Jwts.SIG.HS256)
                .compact();
    }

    public String expiredToken(String studentId) {
        Instant past = Instant.now().minus(2, ChronoUnit.HOURS);
        return Jwts.builder()
                .subject(studentId)
                .issuedAt(Date.from(past.minus(120, ChronoUnit.MINUTES)))
                .expiration(Date.from(past))
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    public String malformedToken() {
        return "not.a.jwt";
    }

    // ------------------------------------------------------------------ spec §8 baseline

    /**
     * The normative seed set from spec §8: 3 students, 16 2026-S2 courses and 3 2026-S1 courses.
     * Used by the catalogue tests, which assert exact counts and code ordering.
     */
    public void seedSpecBaseline() {
        aisha();
        Student daniel = daniel();
        student("32011111", "Priya Nair", "BBIS");

        String it = "Information Technology";
        course("FIT1045", "Introduction to Programming", 6, "Dr Alan Ng", 120, 120, S2, it,
                List.of(Slot.of("MON", "10:00", "12:00", "Clayton, Building 25, Rm 125"),
                        Slot.of("WED", "14:00", "16:00", "Clayton, Building 25, Rm 130")));
        course("FIT1051", "Programming Foundations", 6, "Dr Priya Sharma", 100, 100, S2, it,
                List.of(Slot.of("TUE", "09:00", "11:00", "Clayton, Building 25, Rm 128")));
        course("FIT1055", "Industry Experience", 3, "Ms Helen Ford", 30, 30, S2, it,
                List.of(Slot.of("THU", "13:00", "15:00", "Clayton, Building 6, Rm 210")));
        course("FIT2004", "Algorithms and Data Structures", 6, "Dr Wei Chen", 80, 80, S2, it,
                List.of(Slot.of("TUE", "10:00", "12:00", "Clayton, Building 25, Rm 125")));
        course("FIT2099", "Object Oriented Design", 6, "Dr Marcus Lee", 60, 60, S2, it,
                List.of(Slot.of("WED", "10:00", "12:00", "Clayton, Building 25, Rm 140")));
        course("FIT2101", "Business Process Modelling", 6, "Dr Sarah Whitfield", 55, 55, S2, it,
                List.of(Slot.of("MON", "14:00", "16:00", "Caulfield, Building H, Rm 220")));
        course("FIT3077", "Software Engineering: Architecture and Design", 6, "Prof Anika Rao",
                45, 45, S2, it,
                List.of(Slot.of("WED", "10:00", "12:00", "Clayton, Building 25, Rm 150")));
        course("FIT3143", "Parallel Computing", 6, "Dr Nikolai Petrov", 1, 1, S2, it,
                List.of(Slot.of("FRI", "09:00", "11:00", "Clayton, Building 25, Rm 160")));
        course("FIT3179", "Data Visualisation", 6, "Dr Emily Zhao", 50, 50, S2, it,
                List.of(Slot.of("THU", "10:00", "12:00", "Caulfield, Building B, Rm 310")));
        // M-4: the seeded-full course is capacity 1 with its seat held by a real registration,
        // so invariant C1 holds for the whole seed set.
        Course fit3182 = course("FIT3182", "Big Data Management", 6, "Dr Raj Patel", 1, 0, S2,
                it, List.of(Slot.of("FRI", "14:00", "16:00", "Clayton, Building 25, Rm 170")));
        registration(daniel, fit3182);
        course("ECF1100", "Principles of Economics", 6, "Dr Laura Bennett", 90, 90, S2, "Business",
                List.of(Slot.of("MON", "08:00", "10:00", "Clayton, Building 11, Rm 105")));
        course("ENG1001", "Engineering Design", 6, "Dr Samuel Okafor", 70, 70, S2, "Engineering",
                List.of(Slot.of("TUE", "14:00", "16:00", "Clayton, Building 36, Rm 201")));
        course("LAW1111", "Foundations of Law", 6, "Dr Rebecca Hall", 65, 65, S2, "Law",
                List.of(Slot.of("WED", "16:00", "18:00", "Clayton, Building 12, Rm 300")));
        course("MTH1010", "Functions and Their Applications", 3, "Dr Tanya Ilic", 40, 40, S2,
                "Science",
                List.of(Slot.of("FRI", "10:00", "12:00", "Clayton, Building 28, Rm 110")));
        course("MTH1030", "Techniques for Modelling", 6, "Dr James Wong", 75, 75, S2, "Science",
                List.of(Slot.of("THU", "08:00", "10:00", "Clayton, Building 28, Rm 115")));
        course("SCI1020", "Science of Nature", 6, "Dr Olivia Grant", 85, 85, S2, "Science",
                List.of(Slot.of("MON", "16:00", "18:00", "Clayton, Building 27, Rm 120")));

        course("FIT1008", "Introduction to Computer Science", 6, "Dr Alan Ng", 80, 80, S1, it,
                List.of(Slot.of("TUE", "10:00", "12:00", "Clayton, Building 25, Rm 120")));
        course("FIT1047", "Introduction to Computer Systems", 6, "Dr Wei Chen", 90, 90, S1, it,
                List.of(Slot.of("WED", "09:00", "11:00", "Clayton, Building 25, Rm 122")));
        course("MTH1020", "Analysis of Change", 6, "Dr James Wong", 60, 60, S1, "Science",
                List.of(Slot.of("THU", "14:00", "16:00", "Clayton, Building 28, Rm 118")));
    }

    public UUID randomUuid() {
        return UUID.randomUUID();
    }
}
