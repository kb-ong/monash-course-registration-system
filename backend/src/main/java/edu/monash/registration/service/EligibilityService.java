package edu.monash.registration.service;

import edu.monash.registration.domain.Course;
import edu.monash.registration.domain.Student;
import edu.monash.registration.repository.RegistrationRepository;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * FR3/FR8 eligibility rules shared by registration (FR3) and the waitlist (FR11).
 *
 * <p>The waitlist must apply the same academic checks as registration, otherwise a student could
 * queue for a course they can never be enrolled in. The capacity check deliberately does not live
 * here: capacity is seat arithmetic performed under the course row lock in the calling service.
 */
@Component
public class EligibilityService {

    public static final int MAX_CREDITS = 24;

    private final RegistrationRepository registrationRepository;

    public EligibilityService(RegistrationRepository registrationRepository) {
        this.registrationRepository = registrationRepository;
    }

    /**
     * FR8: the completion history is the only evidence. A current-semester registration for the same
     * course is not a completion (A-21). Codes are sorted so the error message is deterministic.
     */
    public List<String> missingPrerequisites(Student student, Course course) {
        Set<UUID> completedCourseIds = student.getCompletedCourses().stream()
                .map(Course::getId)
                .collect(Collectors.toSet());
        return course.getPrerequisites().stream()
                .filter(prerequisite -> !completedCourseIds.contains(prerequisite.getId()))
                .map(Course::getCode)
                .sorted()
                .toList();
    }

    /** Current enrolled credit total for the active semester, as a JPQL aggregate (FR6). */
    public int currentCredits(UUID studentId, String semester) {
        Long sum = registrationRepository.sumCredits(studentId, semester);
        return sum == null ? 0 : sum.intValue();
    }

    public boolean withinCreditLimit(int currentCredits, Course course) {
        return currentCredits + course.getCreditHours() <= MAX_CREDITS;
    }
}
