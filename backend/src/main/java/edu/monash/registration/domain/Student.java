package edu.monash.registration.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
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
}
