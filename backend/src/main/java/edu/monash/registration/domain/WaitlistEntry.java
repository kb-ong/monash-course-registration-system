package edu.monash.registration.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

/**
 * FR11: one student's place in a course's first-in-first-out waitlist.
 *
 * <p>The generated identity {@code id} is also the queue order: entries are inserted while holding
 * the course row lock (the same lock that protects seats), so insertion order equals join order and
 * ids are monotonically increasing. The student-facing position is <em>derived</em> from the ids of
 * the entries still queued ({@code 1 + count(id < mine)}), never stored, so it stays correct when
 * an earlier student leaves or is promoted.
 */
@Entity
@Table(
        name = "waitlist_entry",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_waitlist_student_course",
                columnNames = {"student_id", "course_id"}),
        indexes = @Index(name = "idx_waitlist_course", columnList = "course_id"))
public class WaitlistEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    protected WaitlistEntry() {
    }

    public WaitlistEntry(Student student, Course course, Instant joinedAt) {
        this.student = student;
        this.course = course;
        this.joinedAt = joinedAt;
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

    public Instant getJoinedAt() {
        return joinedAt;
    }
}
