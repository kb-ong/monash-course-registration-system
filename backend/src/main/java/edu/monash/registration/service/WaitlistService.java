package edu.monash.registration.service;

import edu.monash.registration.api.dto.CourseDto;
import edu.monash.registration.api.dto.RegistrationPeriodDto;
import edu.monash.registration.api.dto.WaitlistItemDto;
import edu.monash.registration.api.dto.WaitlistJoinResponse;
import edu.monash.registration.api.dto.WaitlistLeaveResponse;
import edu.monash.registration.api.dto.WaitlistListDto;
import edu.monash.registration.config.RegistrationProperties;
import edu.monash.registration.domain.AuditAction;
import edu.monash.registration.domain.Course;
import edu.monash.registration.domain.Registration;
import edu.monash.registration.domain.Student;
import edu.monash.registration.domain.WaitlistEntry;
import edu.monash.registration.error.AlreadyRegisteredException;
import edu.monash.registration.error.AlreadyWaitlistedException;
import edu.monash.registration.error.CourseNotFoundException;
import edu.monash.registration.error.CreditLimitExceededException;
import edu.monash.registration.error.PrerequisiteNotMetException;
import edu.monash.registration.error.RegistrationClosedException;
import edu.monash.registration.error.SeatAvailableException;
import edu.monash.registration.error.UnauthorizedException;
import edu.monash.registration.error.WaitlistNotFoundException;
import edu.monash.registration.repository.CourseRepository;
import edu.monash.registration.repository.RegistrationRepository;
import edu.monash.registration.repository.StudentRepository;
import edu.monash.registration.repository.WaitlistRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR11: first-in-first-out waitlists for full courses and the automatic promotion that fills a seat
 * the moment a registration is dropped.
 *
 * <p>Every queue mutation takes the same {@code SELECT ... FOR UPDATE} course row lock that protects
 * the seat counter (FR6). That single lock serialises joins, leaves and promotions against seat
 * changes, so a queue position can never be handed out for a seat that another transaction is
 * simultaneously granting. Queue order is the entry identity id (see {@link WaitlistEntry}).
 *
 * <p>Join check order is normative (A-24): period → course in the active semester → already
 * registered → already waitlisted → prerequisites → credit limit → seats available. It mirrors the
 * registration order (A-21, A-23), with the capacity check replaced by "the course must be full".
 */
@Service
public class WaitlistService {

    private static final Logger log = LoggerFactory.getLogger(WaitlistService.class);

    private final WaitlistRepository waitlistRepository;
    private final CourseRepository courseRepository;
    private final StudentRepository studentRepository;
    private final RegistrationRepository registrationRepository;
    private final RegistrationPeriodService periodService;
    private final RegistrationProperties registrationProperties;
    private final EligibilityService eligibility;
    private final NotificationService notificationService;
    private final AuditService auditService;
    private final CourseDtoMapper mapper;
    private final Clock clock;

    public WaitlistService(WaitlistRepository waitlistRepository, CourseRepository courseRepository,
            StudentRepository studentRepository, RegistrationRepository registrationRepository,
            RegistrationPeriodService periodService, RegistrationProperties registrationProperties,
            EligibilityService eligibility, NotificationService notificationService,
            AuditService auditService, CourseDtoMapper mapper, Clock clock) {
        this.waitlistRepository = waitlistRepository;
        this.courseRepository = courseRepository;
        this.studentRepository = studentRepository;
        this.registrationRepository = registrationRepository;
        this.periodService = periodService;
        this.registrationProperties = registrationProperties;
        this.eligibility = eligibility;
        this.notificationService = notificationService;
        this.auditService = auditService;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public WaitlistJoinResponse join(String studentId, UUID courseId) {
        RegistrationPeriodDto period = periodService.current();
        if (!period.open()) {
            auditFailure(studentId, null, "REGISTRATION_CLOSED");
            throw new RegistrationClosedException(periodService.closedMessage());
        }

        Course course = courseRepository
                .findWithLockByIdAndSemester(courseId, period.semester())
                .orElse(null);
        if (course == null) {
            Course outOfSemester = courseRepository.findById(courseId).orElse(null);
            String detail = outOfSemester != null ? "SEMESTER_NOT_ACTIVE" : "COURSE_NOT_FOUND";
            String auditCode = outOfSemester != null ? outOfSemester.getCode() : null;
            auditFailure(studentId, auditCode, detail);
            throw new CourseNotFoundException();
        }

        Student student = studentRepository.findByStudentId(studentId)
                .orElseThrow(UnauthorizedException::new);

        if (registrationRepository.existsByStudentIdAndCourseId(student.getId(), course.getId())) {
            auditFailure(studentId, course.getCode(), "ALREADY_REGISTERED");
            throw new AlreadyRegisteredException();
        }

        if (waitlistRepository.existsByStudentIdAndCourseId(student.getId(), course.getId())) {
            auditFailure(studentId, course.getCode(), "ALREADY_WAITLISTED");
            throw new AlreadyWaitlistedException(course.getCode());
        }

        List<String> missingPrerequisites = eligibility.missingPrerequisites(student, course);
        if (!missingPrerequisites.isEmpty()) {
            auditFailure(studentId, course.getCode(), "PREREQUISITE_NOT_MET");
            throw new PrerequisiteNotMetException(course.getCode(), missingPrerequisites);
        }

        int maxCredits = registrationProperties.getMaxCredits();
        int currentCredits = eligibility.currentCredits(student.getId(), period.semester());
        if (!eligibility.withinCreditLimit(currentCredits, course, maxCredits)) {
            auditFailure(studentId, course.getCode(), "CREDIT_LIMIT_EXCEEDED");
            throw new CreditLimitExceededException(course.getCode(), course.getCreditHours(),
                    currentCredits, maxCredits);
        }

        if (course.getAvailableSeats() > 0) {
            auditFailure(studentId, course.getCode(), "SEAT_AVAILABLE");
            throw new SeatAvailableException(course.getCode());
        }

        WaitlistEntry entry = waitlistRepository.saveAndFlush(
                new WaitlistEntry(student, course,
                        Instant.now(clock).truncatedTo(ChronoUnit.SECONDS)));
        int position = positionOf(entry);
        int size = (int) waitlistRepository.countByCourseId(course.getId());

        notificationService.notifyWaitlistJoined(student, course, position);
        auditService.recordSuccess(studentId, course.getCode(), AuditAction.WAITLIST_JOIN);
        log.info("waitlist.joined studentId={} course={} position={} size={}",
                studentId, course.getCode(), position, size);

        String message = "You are #" + position + " on the waitlist for " + course.getCode() + " "
                + course.getName() + ".";
        return new WaitlistJoinResponse(entry.getId(), position, size, message,
                mapper.toDto(course));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public WaitlistLeaveResponse leave(String studentId, Long waitlistId) {
        WaitlistEntry entry = waitlistRepository.findOwned(waitlistId, studentId).orElse(null);
        if (entry == null) {
            auditFailure(studentId, null, AuditAction.WAITLIST_LEAVE, "WAITLIST_NOT_FOUND");
            throw new WaitlistNotFoundException();
        }

        Course course = courseRepository.findWithLockById(entry.getCourse().getId())
                .orElseThrow(WaitlistNotFoundException::new);
        String courseCode = course.getCode();

        // Re-read under the course lock: a concurrent drop may have promoted this student between
        // the first read and acquiring the lock. Deleting a row that no longer exists would turn a
        // harmless race into a 500; report the same 404 as any other missing entry instead.
        WaitlistEntry current = waitlistRepository.findOwned(waitlistId, studentId).orElse(null);
        if (current == null) {
            auditFailure(studentId, courseCode, AuditAction.WAITLIST_LEAVE, "WAITLIST_NOT_FOUND");
            throw new WaitlistNotFoundException();
        }
        waitlistRepository.delete(current);
        waitlistRepository.flush();

        auditService.recordSuccess(studentId, courseCode, AuditAction.WAITLIST_LEAVE);
        log.info("waitlist.left studentId={} course={}", studentId, courseCode);
        return new WaitlistLeaveResponse(
                "You left the waitlist for " + courseCode + " " + course.getName() + ".");
    }

    @Transactional(readOnly = true)
    public WaitlistListDto list(String studentId) {
        Student student = studentRepository.findByStudentId(studentId)
                .orElseThrow(UnauthorizedException::new);
        String semester = periodService.activeSemester();
        List<WaitlistItemDto> items = waitlistRepository
                .findForStudentInSemester(student.getId(), semester)
                .stream()
                .map(entry -> new WaitlistItemDto(entry.getId(), entry.getJoinedAt(),
                        positionOf(entry), mapper.toDto(entry.getCourse())))
                .toList();
        return new WaitlistListDto(semester, items);
    }

    /**
     * Gives one freed seat to the first eligible student in queue order. Called from
     * {@link RegistrationService#drop} inside the drop transaction, after the course row lock and
     * after the freed seat has been added back to {@code availableSeats}; promotion consumes it
     * again, so {@code availableSeats} ends unchanged when a student is promoted.
     *
     * <p>Entries that are no longer eligible (prerequisites removed, credit cap reached, or the
     * course now clashes with the student's timetable per FR9) are skipped and stay queued — they
     * do not block the students behind them, and can be promoted on a later drop if they become
     * eligible again. A skipped entry is never silently deleted. The student row is locked before
     * the clash/credit reads so the promotion cannot race that student's own registration.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<Promotion> promoteFirstEligible(Course course) {
        if (course.getAvailableSeats() <= 0) {
            return Optional.empty();
        }
        int maxCredits = registrationProperties.getMaxCredits();
        for (WaitlistEntry entry : waitlistRepository.findQueueForCourse(course.getId())) {
            // FR9: the promoted student's own register attempts must be serialised with this
            // automatic enrolment, so the clash check below cannot race a concurrent registration.
            Student student = studentRepository
                    .findWithLockByStudentId(entry.getStudent().getStudentId())
                    .orElse(null);
            if (student == null) {
                continue;
            }
            if (registrationRepository.existsByStudentIdAndCourseId(student.getId(),
                    course.getId())) {
                log.warn("waitlist.promote.skip alreadyRegistered studentId={} course={}",
                        student.getStudentId(), course.getCode());
                continue;
            }
            if (!eligibility.missingPrerequisites(student, course).isEmpty()) {
                log.debug("waitlist.promote.skip prerequisites studentId={} course={}",
                        student.getStudentId(), course.getCode());
                continue;
            }
            int currentCredits = eligibility.currentCredits(student.getId(),
                    periodService.activeSemester());
            if (!eligibility.withinCreditLimit(currentCredits, course, maxCredits)) {
                log.info("waitlist.promote.skip creditLimit studentId={} course={} credits={}",
                        student.getStudentId(), course.getCode(), currentCredits);
                continue;
            }
            List<TimetableClashDetector.Clash> clashes = TimetableClashDetector.detect(course,
                    registrationRepository.findAllForStudentInSemesterWithSchedule(
                            student.getId(), periodService.activeSemester()));
            if (!clashes.isEmpty()) {
                log.info("waitlist.promote.skip timetableClash studentId={} course={} conflicts={}",
                        student.getStudentId(), course.getCode(),
                        TimetableClashDetector.describe(clashes));
                continue;
            }

            course.setAvailableSeats(course.getAvailableSeats() - 1);
            Registration registration = new Registration(student, course,
                    Instant.now(clock).truncatedTo(ChronoUnit.SECONDS));
            registrationRepository.saveAndFlush(registration);
            waitlistRepository.delete(entry);
            waitlistRepository.flush();

            notificationService.notifyWaitlistPromoted(student, course);
            auditService.recordSuccess(student.getStudentId(), course.getCode(),
                    AuditAction.WAITLIST_PROMOTED);
            log.info("waitlist.promoted studentId={} course={} registrationId={}",
                    student.getStudentId(), course.getCode(), registration.getId());
            return Optional.of(new Promotion(student.getStudentId(), registration.getId(),
                    course.getCode()));
        }
        return Optional.empty();
    }

    private int positionOf(WaitlistEntry entry) {
        return (int) waitlistRepository.countByCourseIdAndIdLessThan(
                entry.getCourse().getId(), entry.getId()) + 1;
    }

    private void auditFailure(String studentId, String courseCode, String detail) {
        auditFailure(studentId, courseCode, AuditAction.WAITLIST_REJECTED, detail);
    }

    private void auditFailure(String studentId, String courseCode, AuditAction action,
            String detail) {
        try {
            auditService.publishFailure(studentId, courseCode, action, detail);
        } catch (RuntimeException ex) {
            log.error("audit.write.failed studentId={} action={} detail={}", studentId, action,
                    detail, ex);
        }
    }

    /** The student who received a freed seat, and the registration created for them. */
    public record Promotion(String studentId, UUID registrationId, String courseCode) {
    }
}
