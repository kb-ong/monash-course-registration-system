package edu.monash.registration.seed;

import edu.monash.registration.domain.Course;
import edu.monash.registration.domain.CourseSchedule;
import edu.monash.registration.domain.DayOfWeekCode;
import edu.monash.registration.domain.Registration;
import edu.monash.registration.domain.Student;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deterministic demo data (spec §8). Idempotent: if any student exists, nothing happens.
 *
 * <p>Course identifiers are fixed constants so integration tests can reference rows without a
 * lookup: 2026-S2 courses are {@code bbbbbbbb-0000-4000-8000-0000000000NN} (NN = 01..16) and
 * 2026-S1 courses are {@code cccccccc-0000-4000-8000-0000000000NN} (NN = 01..03).
 */
@Component
@Profile("!test")
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private static final String DEMO_PASSWORD = "Password123!";
    private static final String SEMESTER_S2 = "2026-S2";
    private static final String SEMESTER_S1 = "2026-S1";
    private static final String FACULTY_IT = "Information Technology";
    private static final String FACULTY_BUSINESS = "Business";
    private static final String FACULTY_ENGINEERING = "Engineering";
    private static final String FACULTY_LAW = "Law";
    private static final String FACULTY_SCIENCE = "Science";

    /**
     * 16 active-semester courses (spec §8.2). FIT3143 = 1 seat, FIT3182 = one seat held by a
     * seeded registration so invariant C1 holds (availableSeats + rows = capacity).
     */
    private static final List<CourseSeed> ACTIVE_COURSES = List.of(
            new CourseSeed("FIT1045", "Introduction to Programming", 6, "Dr Alan Ng",
                    120, 120, FACULTY_IT,
                    "Foundations of procedural programming: variables, control flow, functions and files.",
                    List.of(new Slot("MON", "10:00", "12:00", "Clayton, Building 25, Rm 125"),
                            new Slot("WED", "14:00", "16:00", "Clayton, Building 25, Rm 130"))),
            new CourseSeed("FIT1051", "Programming Foundations", 6, "Dr Priya Sharma",
                    100, 100, FACULTY_IT,
                    "Object-oriented foundations in Java with an emphasis on design and testing.",
                    List.of(new Slot("TUE", "09:00", "11:00", "Clayton, Building 25, Rm 128"))),
            new CourseSeed("FIT1055", "Industry Experience", 3, "Ms Helen Ford",
                    30, 30, FACULTY_IT,
                    "Work-integrated learning project with an industry partner and reflective practice.",
                    List.of(new Slot("THU", "13:00", "15:00", "Clayton, Building 6, Rm 210"))),
            new CourseSeed("FIT2004", "Algorithms and Data Structures", 6, "Dr Wei Chen",
                    80, 80, FACULTY_IT,
                    "Algorithm design paradigms, complexity analysis and core data structures.",
                    List.of(new Slot("TUE", "10:00", "12:00", "Clayton, Building 25, Rm 125"))),
            new CourseSeed("FIT2099", "Object Oriented Design", 6, "Dr Marcus Lee",
                    60, 60, FACULTY_IT,
                    "Design patterns, modelling and refactoring for maintainable object-oriented systems.",
                    List.of(new Slot("WED", "10:00", "12:00", "Clayton, Building 25, Rm 140"))),
            new CourseSeed("FIT2101", "Business Process Modelling", 6, "Dr Sarah Whitfield",
                    55, 55, FACULTY_IT,
                    "Process discovery, BPMN modelling and analysis of enterprise workflows.",
                    List.of(new Slot("MON", "14:00", "16:00", "Caulfield, Building H, Rm 220"))),
            new CourseSeed("FIT3077", "Software Engineering: Architecture and Design", 6,
                    "Prof Anika Rao", 45, 45, FACULTY_IT,
                    "Architectural styles, quality attributes and design of large software systems.",
                    List.of(new Slot("WED", "10:00", "12:00", "Clayton, Building 25, Rm 150"))),
            new CourseSeed("FIT3143", "Parallel Computing", 6, "Dr Nikolai Petrov",
                    1, 1, FACULTY_IT,
                    "Shared-memory and message-passing parallelism, performance and correctness.",
                    List.of(new Slot("FRI", "09:00", "11:00", "Clayton, Building 25, Rm 160"))),
            new CourseSeed("FIT3179", "Data Visualisation", 6, "Dr Emily Zhao",
                    50, 50, FACULTY_IT,
                    "Perception, encoding and interaction for effective data visualisation.",
                    List.of(new Slot("THU", "10:00", "12:00", "Caulfield, Building B, Rm 310"))),
            new CourseSeed("FIT3182", "Big Data Management", 6, "Dr Raj Patel",
                    1, 0, FACULTY_IT,
                    "Distributed storage and query processing for large-scale datasets.",
                    List.of(new Slot("FRI", "14:00", "16:00", "Clayton, Building 25, Rm 170"))),
            new CourseSeed("ECF1100", "Principles of Economics", 6, "Dr Laura Bennett",
                    90, 90, FACULTY_BUSINESS,
                    "Microeconomic and macroeconomic principles applied to real markets.",
                    List.of(new Slot("MON", "08:00", "10:00", "Clayton, Building 11, Rm 105"))),
            new CourseSeed("ENG1001", "Engineering Design", 6, "Dr Samuel Okafor",
                    70, 70, FACULTY_ENGINEERING,
                    "Team-based design projects with requirements, prototyping and evaluation.",
                    List.of(new Slot("TUE", "14:00", "16:00", "Clayton, Building 36, Rm 201"))),
            new CourseSeed("LAW1111", "Foundations of Law", 6, "Dr Rebecca Hall",
                    65, 65, FACULTY_LAW,
                    "Sources of law, legal reasoning and the Australian legal system.",
                    List.of(new Slot("WED", "16:00", "18:00", "Clayton, Building 12, Rm 300"))),
            new CourseSeed("MTH1010", "Functions and Their Applications", 3, "Dr Tanya Ilic",
                    40, 40, FACULTY_SCIENCE,
                    "Functions, graphs and mathematical modelling for science students.",
                    List.of(new Slot("FRI", "10:00", "12:00", "Clayton, Building 28, Rm 110"))),
            new CourseSeed("MTH1030", "Techniques for Modelling", 6, "Dr James Wong",
                    75, 75, FACULTY_SCIENCE,
                    "Vectors, matrices, differential equations and their modelling applications.",
                    List.of(new Slot("THU", "08:00", "10:00", "Clayton, Building 28, Rm 115"))),
            new CourseSeed("SCI1020", "Science of Nature", 6, "Dr Olivia Grant",
                    85, 85, FACULTY_SCIENCE,
                    "Scientific method, data literacy and interdisciplinary science perspectives.",
                    List.of(new Slot("MON", "16:00", "18:00", "Clayton, Building 27, Rm 120"))));

    /** 3 prior-semester courses that must never appear in the active catalogue (spec §8.3). */
    private static final List<CourseSeed> PRIOR_COURSES = List.of(
            new CourseSeed("FIT1008", "Introduction to Computer Science", 6, "Dr Alan Ng",
                    80, 80, FACULTY_IT,
                    "Computational thinking and problem solving with an introduction to programming.",
                    List.of(new Slot("TUE", "10:00", "12:00", "Clayton, Building 25, Rm 120"))),
            new CourseSeed("FIT1047", "Introduction to Computer Systems", 6, "Dr Wei Chen",
                    90, 90, FACULTY_IT,
                    "Data representation, digital logic and the structure of computer systems.",
                    List.of(new Slot("WED", "09:00", "11:00", "Clayton, Building 25, Rm 122"))),
            new CourseSeed("MTH1020", "Analysis of Change", 6, "Dr James Wong",
                    60, 60, FACULTY_SCIENCE,
                    "Differential and integral calculus with applications to the sciences.",
                    List.of(new Slot("THU", "14:00", "16:00", "Clayton, Building 28, Rm 118"))));

    private final EntityManager entityManager;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public DataSeeder(EntityManager entityManager, PasswordEncoder passwordEncoder, Clock clock) {
        this.entityManager = entityManager;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(String... args) {
        long existing = entityManager
                .createQuery("select count(s) from Student s", Long.class)
                .getSingleResult();
        if (existing > 0) {
            log.info("seed.skipped reason=existing-data students={}", existing);
            return;
        }

        seedStudents();
        int courses = seedCourses();
        seedFullCourseRegistration();
        entityManager.flush();
        log.info("seed.completed students=3 courses={} registrations=1", courses);
    }

    private void seedStudents() {
        String hash = passwordEncoder.encode(DEMO_PASSWORD);
        Instant createdAt = Instant.now(clock);
        persistStudent("32012345", "Aisha Rahman", "aisha.rahman@student.monash.edu", "BCS", hash,
                createdAt);
        persistStudent("32067890", "Daniel Tan", "daniel.tan@student.monash.edu", "BSE", hash,
                createdAt);
        persistStudent("32011111", "Priya Nair", "priya.nair@student.monash.edu", "BBIS", hash,
                createdAt);
    }

    private void persistStudent(String studentId, String name, String email, String program,
            String hash, Instant createdAt) {
        entityManager.persist(new Student(studentId, name, email, program, hash, createdAt));
    }

    private int seedCourses() {
        int index = 0;
        for (CourseSeed seed : ACTIVE_COURSES) {
            index++;
            persistCourse(courseId('b', index), SEMESTER_S2, seed);
        }
        int priorIndex = 0;
        for (CourseSeed seed : PRIOR_COURSES) {
            priorIndex++;
            persistCourse(courseId('c', priorIndex), SEMESTER_S1, seed);
        }
        return ACTIVE_COURSES.size() + PRIOR_COURSES.size();
    }

    /**
     * FIT3182 is capacity 1 with its only seat held by Daniel, so Aisha sees a genuinely full
     * course and invariant C1 holds without special cases (M-4).
     */
    private void seedFullCourseRegistration() {
        Student daniel = entityManager
                .createQuery("select s from Student s where s.studentId = :studentId", Student.class)
                .setParameter("studentId", "32067890")
                .getSingleResult();
        Course fullCourse = entityManager
                .createQuery("select c from Course c where c.code = :code and c.semester = :semester",
                        Course.class)
                .setParameter("code", "FIT3182")
                .setParameter("semester", SEMESTER_S2)
                .getSingleResult();
        entityManager.persist(new Registration(daniel, fullCourse,
                Instant.now(clock).truncatedTo(ChronoUnit.SECONDS)));
    }

    private void persistCourse(UUID id, String semester, CourseSeed seed) {
        Course course = new Course(id, seed.code(), seed.name(), seed.creditHours(),
                seed.instructor(), seed.capacity(), seed.availableSeats(), semester, seed.faculty(),
                seed.description());
        for (Slot slot : seed.slots()) {
            course.addSchedule(new CourseSchedule(
                    DayOfWeekCode.valueOf(slot.day()),
                    LocalTime.parse(slot.startTime()),
                    LocalTime.parse(slot.endTime()),
                    slot.location()));
        }
        entityManager.persist(course);
    }

    private UUID courseId(char prefix, int index) {
        return UUID.fromString(String.format("%s-0000-4000-8000-%012d",
                String.valueOf(prefix).repeat(8), index));
    }

    private record Slot(String day, String startTime, String endTime, String location) {
    }

    private record CourseSeed(String code, String name, int creditHours, String instructor,
            int capacity, int availableSeats, String faculty, String description, List<Slot> slots) {
    }
}
