package edu.monash.registration.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A pre-provisioned student. The password hash is never serialised by any DTO (NFR2).
 */
@Entity
@Table(
        name = "student",
        uniqueConstraints = {
            @UniqueConstraint(name = "uk_student_student_id", columnNames = "student_id"),
            @UniqueConstraint(name = "uk_student_email", columnNames = "email")
        })
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "student_id", nullable = false, length = 8)
    private String studentId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "email", nullable = false, length = 160)
    private String email;

    @Column(name = "program", nullable = false, length = 16)
    private String program;

    @Column(name = "password_hash", nullable = false, length = 60)
    private String passwordHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /**
     * FR8: the student's completed-course history. Deliberately separate from {@code registration}:
     * registering for a course in the current semester does not count as having completed it. Rows
     * are seeded (there is no admin/academic-history UI in Phase 2).
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "student_completed_course",
            joinColumns = @JoinColumn(name = "student_id"),
            inverseJoinColumns = @JoinColumn(name = "course_id"),
            uniqueConstraints = @UniqueConstraint(
                    name = "uk_student_completed_course",
                    columnNames = {"student_id", "course_id"}))
    private Set<Course> completedCourses = new LinkedHashSet<>();

    protected Student() {
    }

    public Student(String studentId, String name, String email, String program, String passwordHash,
            Instant createdAt) {
        this.studentId = studentId;
        this.name = name;
        this.email = email;
        this.program = program;
        this.passwordHash = passwordHash;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getStudentId() {
        return studentId;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getProgram() {
        return program;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Set<Course> getCompletedCourses() {
        return completedCourses;
    }

    public void markCompleted(Course course) {
        completedCourses.add(course);
    }
}
