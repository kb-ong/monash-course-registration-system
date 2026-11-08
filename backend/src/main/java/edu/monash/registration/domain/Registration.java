package edu.monash.registration.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

/** A persisted (student, course) link. The unique key is the duplicate defence. */
@Entity
@Table(
        name = "registration",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_registration_student_course",
                columnNames = {"student_id", "course_id"}))
public class Registration {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Column(name = "registered_at", nullable = false)
    private Instant registeredAt;

    protected Registration() {
    }

    public Registration(Student student, Course course, Instant registeredAt) {
        this.student = student;
        this.course = course;
        this.registeredAt = registeredAt;
    }

    public UUID getId() {
        return id;
    }

    public Student getStudent() {
        return student;
    }

    public Course getCourse() {
        return course;
    }

    public Instant getRegisteredAt() {
        return registeredAt;
    }
}
