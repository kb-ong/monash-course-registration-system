package edu.monash.registration.service;

import edu.monash.registration.api.dto.DropResponse;
import edu.monash.registration.api.dto.RegisterResponse;
import edu.monash.registration.api.dto.RegistrationItemDto;
import edu.monash.registration.api.dto.RegistrationListDto;
import edu.monash.registration.api.dto.RegistrationPeriodDto;
import edu.monash.registration.domain.AuditAction;
import edu.monash.registration.domain.Course;
import edu.monash.registration.domain.Registration;
import edu.monash.registration.domain.Student;
import edu.monash.registration.error.AlreadyRegisteredException;
import edu.monash.registration.error.CourseFullException;
import edu.monash.registration.error.CourseNotFoundException;
import edu.monash.registration.error.CreditLimitExceededException;
import edu.monash.registration.error.NotOwnerException;
import edu.monash.registration.error.RegistrationClosedException;
import edu.monash.registration.error.RegistrationNotFoundException;
import edu.monash.registration.error.UnauthorizedException;
import edu.monash.registration.repository.CourseRepository;
import edu.monash.registration.repository.RegistrationRepository;
import edu.monash.registration.repository.StudentRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR3/FR4/FR5/FR6: the transactional heart of the system.
 *
 * <p>Seat arithmetic happens only while holding a {@code SELECT ... FOR UPDATE} lock on the course
 * row, inside the same transaction that inserts or deletes the registration. Check order is
 * normative: period → course exists → duplicate → credit limit → capacity (A-03) for register, and
 * registration exists → owner → period for drop.
 */
@Service
public class RegistrationService {

    private static final Logger log = LoggerFactory.getLogger(RegistrationService.class);

    public static final int MAX_CREDITS = 24;

    private final RegistrationRepository registrationRepository;
    private final CourseRepository courseRepository;
    private final StudentRepository studentRepository;
    private final RegistrationPeriodService periodService;
    private final AuditService auditService;
    private final CourseDtoMapper mapper;
    private final Clock clock;

    public RegistrationService(RegistrationRepository registrationRepository,
            CourseRepository courseRepository,
            StudentRepository studentRepository,
            RegistrationPeriodService periodService,
            AuditService auditService,
            CourseDtoMapper mapper,
            Clock clock) {
        this.registrationRepository = registrationRepository;
        this.courseRepository = courseRepository;
        this.studentRepository = studentRepository;
        this.periodService = periodService;
        this.auditService = auditService;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public RegisterResponse register(String studentId, UUID courseId) {
        RegistrationPeriodDto period = periodService.current();
        if (!period.open()) {
            auditFailure(studentId, null, AuditAction.REGISTER_REJECTED, "REGISTRATION_CLOSED");
            throw new RegistrationClosedException(periodService.closedMessage());
        }

        Course course = courseRepository
                .findWithLockByIdAndSemester(courseId, period.semester())
                .orElse(null);
        if (course == null) {
            // A miss can mean "no such course" or "course belongs to another semester". The
            // existence probe only runs on the failure path and never touches the seat counter.
            Course outOfSemester = courseRepository.findById(courseId).orElse(null);
            String detail = outOfSemester != null ? "SEMESTER_NOT_ACTIVE" : "COURSE_NOT_FOUND";
            String auditCode = outOfSemester != null ? outOfSemester.getCode() : null;
            auditFailure(studentId, auditCode, AuditAction.REGISTER_REJECTED, detail);
            throw new CourseNotFoundException();
        }

        Student student = studentRepository.findByStudentId(studentId)
                .orElseThrow(UnauthorizedException::new);

        if (registrationRepository.existsByStudentIdAndCourseId(student.getId(), course.getId())) {
            auditFailure(studentId, course.getCode(), AuditAction.REGISTER_REJECTED, "ALREADY_REGISTERED");
            throw new AlreadyRegisteredException();
        }

        int currentCredits = currentCredits(student.getId());
        if (currentCredits + course.getCreditHours() > MAX_CREDITS) {
            auditFailure(studentId, course.getCode(), AuditAction.REGISTER_REJECTED,
                    "CREDIT_LIMIT_EXCEEDED");
            throw new CreditLimitExceededException(course.getCode(), course.getCreditHours(), MAX_CREDITS);
        }

        if (course.getAvailableSeats() <= 0) {
            auditFailure(studentId, course.getCode(), AuditAction.REGISTER_REJECTED, "COURSE_FULL");
            throw new CourseFullException(course.getCode());
        }

        course.setAvailableSeats(course.getAvailableSeats() - 1);
        Registration registration = new Registration(student, course,
                Instant.now(clock).truncatedTo(ChronoUnit.SECONDS));
        try {
            registrationRepository.saveAndFlush(registration);
        } catch (DataIntegrityViolationException ex) {
            // The unique key is only a backstop; the existsBy check above is the normal path.
            auditFailure(studentId, course.getCode(), AuditAction.REGISTER_REJECTED, "ALREADY_REGISTERED");
            throw new AlreadyRegisteredException();
        }

        auditService.recordSuccess(studentId, course.getCode(), AuditAction.REGISTER);
        log.info("registration.created studentId={} course={} availableSeats={}",
                studentId, course.getCode(), course.getAvailableSeats());

        String message = "Registered for " + course.getCode() + " " + course.getName() + ".";
        return new RegisterResponse(registration.getId(), message, mapper.toDto(course),
                course.getAvailableSeats());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public DropResponse drop(String studentId, UUID registrationId) {
        Registration registration = registrationRepository.findWithLockById(registrationId)
                .orElse(null);
        if (registration == null) {
            auditFailure(studentId, null, AuditAction.DROP, "REGISTRATION_NOT_FOUND");
            throw new RegistrationNotFoundException();
        }

        if (!registration.getStudent().getStudentId().equals(studentId)) {
            auditFailure(studentId, registration.getCourse().getCode(), AuditAction.DROP, "NOT_OWNER");
            throw new NotOwnerException();
        }

        String courseCode = registration.getCourse().getCode();
        UUID courseId = registration.getCourse().getId();

        RegistrationPeriodDto period = periodService.current();
        if (!period.open()) {
            auditFailure(studentId, courseCode, AuditAction.DROP, "REGISTRATION_CLOSED");
            throw new RegistrationClosedException(periodService.closedMessage());
        }

        Course course = courseRepository.findWithLockById(courseId)
                .orElseThrow(CourseNotFoundException::new);

        try {
            registrationRepository.delete(registration);
            registrationRepository.flush();
        } catch (ObjectOptimisticLockingFailureException ex) {
            // A concurrent drop removed the row between our locked read and the delete. Treat it
            // exactly like an already-dropped registration: 404, never 500, still audited.
            auditFailure(studentId, null, AuditAction.DROP, "REGISTRATION_NOT_FOUND");
            throw new RegistrationNotFoundException();
        }
        course.setAvailableSeats(Math.min(course.getCapacity(), course.getAvailableSeats() + 1));

        auditService.recordSuccess(studentId, course.getCode(), AuditAction.DROP);
        log.info("registration.dropped studentId={} course={} availableSeats={}",
                studentId, course.getCode(), course.getAvailableSeats());

        return new DropResponse("Dropped " + course.getCode() + " " + course.getName() + ".",
                course.getAvailableSeats());
    }

    @Transactional(readOnly = true)
    public RegistrationListDto listRegistrations(String studentId) {
        Student student = studentRepository.findByStudentId(studentId)
                .orElseThrow(UnauthorizedException::new);
        String semester = periodService.activeSemester();
        List<Registration> registrations =
                registrationRepository.findAllForStudentInSemester(student.getId(), semester);

        int totalCredits = 0;
        List<RegistrationItemDto> items = new ArrayList<>();
        for (Registration registration : registrations) {
            totalCredits += registration.getCourse().getCreditHours();
            items.add(new RegistrationItemDto(registration.getId(), registration.getRegisteredAt(),
                    mapper.toDto(registration.getCourse())));
        }
        log.debug("registrations.view studentId={} count={}", studentId, items.size());
        return new RegistrationListDto(semester, totalCredits, MAX_CREDITS, items);
    }

    private int currentCredits(UUID studentId) {
        Long sum = registrationRepository.sumCredits(studentId, periodService.activeSemester());
        return sum == null ? 0 : sum.intValue();
    }

    /**
     * NFR4-6: never let an audit write failure mask the original business error, but never lose it
     * silently either.
     */
    private void auditFailure(String studentId, String courseCode, AuditAction action, String detail) {
        try {
            auditService.publishFailure(studentId, courseCode, action, detail);
        } catch (RuntimeException ex) {
            log.error("audit.write.failed studentId={} action={} detail={}", studentId, action, detail, ex);
        }
    }
}
