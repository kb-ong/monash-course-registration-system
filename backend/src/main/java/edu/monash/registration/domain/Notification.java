package edu.monash.registration.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * FR11: a persisted in-app notification for one student about one course. Rows are immutable apart
 * from {@code readAt}; there is no email or SMS transport (NG10).
 */
@Entity
@Table(
        name = "notification",
        indexes = {
            @Index(name = "idx_notification_student", columnList = "student_id"),
            @Index(name = "idx_notification_student_unread", columnList = "student_id, read_at")
        })
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private NotificationType type;

    @Column(name = "message", nullable = false, length = 255)
    private String message;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Null while unread. Set exactly once; notification content never changes. */
    @Column(name = "read_at")
    private Instant readAt;

    protected Notification() {
    }

    public Notification(Student student, Course course, NotificationType type, String message,
            Instant createdAt) {
        this.student = student;
        this.course = course;
        this.type = type;
        this.message = message;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Student getStudent() {
        return student;
    }

    public Course getCourse() {
        return course;
    }

    public NotificationType getType() {
        return type;
    }

    public String getMessage() {
        return message;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public boolean isRead() {
        return readAt != null;
    }

    /** Idempotent: marking an already-read notification keeps its first read instant. */
    public void markRead(Instant at) {
        if (readAt == null) {
            readAt = at;
        }
    }
}
