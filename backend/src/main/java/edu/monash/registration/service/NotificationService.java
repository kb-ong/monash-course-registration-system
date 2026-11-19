package edu.monash.registration.service;

import edu.monash.registration.api.dto.NotificationItemDto;
import edu.monash.registration.api.dto.NotificationListDto;
import edu.monash.registration.api.dto.NotificationReadResponse;
import edu.monash.registration.domain.Course;
import edu.monash.registration.domain.Notification;
import edu.monash.registration.domain.NotificationType;
import edu.monash.registration.domain.Student;
import edu.monash.registration.error.NotificationNotFoundException;
import edu.monash.registration.error.UnauthorizedException;
import edu.monash.registration.repository.NotificationRepository;
import edu.monash.registration.repository.StudentRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * FR11: persists and serves in-app notifications. A notification is an event record (join
 * confirmation, automatic promotion); it is never edited except to record the first read.
 */
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final StudentRepository studentRepository;
    private final Clock clock;

    public NotificationService(NotificationRepository notificationRepository,
            StudentRepository studentRepository, Clock clock) {
        this.notificationRepository = notificationRepository;
        this.studentRepository = studentRepository;
        this.clock = clock;
    }

    /** Joins the caller's transaction: the notification exists exactly when the queue place does. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void notifyWaitlistJoined(Student student, Course course, int position) {
        persist(student, course, NotificationType.WAITLIST_JOINED,
                "You are #" + position + " on the waitlist for " + course.getCode() + " "
                        + course.getName() + ".");
    }

    /** Joins the drop/promotion transaction: the student is told they were enrolled automatically. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void notifyWaitlistPromoted(Student student, Course course) {
        persist(student, course, NotificationType.WAITLIST_PROMOTED,
                "A seat opened in " + course.getCode() + " " + course.getName()
                        + ". You were first on the waitlist, so you have been enrolled automatically.");
    }

    @Transactional(readOnly = true)
    public NotificationListDto list(String studentId) {
        Student student = requireStudent(studentId);
        List<Notification> notifications = notificationRepository.findForStudent(student.getId());
        List<NotificationItemDto> items = notifications.stream()
                .map(notification -> new NotificationItemDto(
                        notification.getId(),
                        notification.getType().name(),
                        notification.getMessage(),
                        notification.getCreatedAt(),
                        notification.isRead(),
                        notification.getCourse().getId(),
                        notification.getCourse().getCode(),
                        notification.getCourse().getName()))
                .toList();
        return new NotificationListDto((int) unreadCount(student.getId()), items);
    }

    @Transactional
    public NotificationReadResponse markRead(String studentId, Long notificationId) {
        Student student = requireStudent(studentId);
        Notification notification = notificationRepository
                .findOwned(notificationId, student.getId())
                .orElseThrow(NotificationNotFoundException::new);
        notification.markRead(Instant.now(clock).truncatedTo(ChronoUnit.SECONDS));
        notificationRepository.flush();
        return new NotificationReadResponse((int) unreadCount(student.getId()));
    }

    private void persist(Student student, Course course, NotificationType type, String message) {
        notificationRepository.save(new Notification(student, course, type, message,
                Instant.now(clock).truncatedTo(ChronoUnit.SECONDS)));
    }

    private long unreadCount(UUID studentId) {
        return notificationRepository.countByStudentIdAndReadAtIsNull(studentId);
    }

    private Student requireStudent(String studentId) {
        return studentRepository.findByStudentId(studentId)
                .orElseThrow(UnauthorizedException::new);
    }
}
