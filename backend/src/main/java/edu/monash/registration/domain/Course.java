package edu.monash.registration.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.Check;

/**
 * A catalogue course. {@code availableSeats} is only ever mutated while holding a pessimistic
 * write lock on the row (FR6).
 */
@Entity
@Table(
        name = "course",
        uniqueConstraints = @UniqueConstraint(name = "uk_course_code", columnNames = "code"),
        indexes = @Index(name = "idx_course_semester", columnList = "semester"))
public class Course {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "code", nullable = false, length = 10)
    private String code;

    @Column(name = "name", nullable = false, length = 160)
    private String name;

    @Check(name = "ck_course_credit_hours_allowed", constraints = "credit_hours in (3, 6)")
    @Column(name = "credit_hours", nullable = false)
    private int creditHours;

    @Column(name = "instructor", nullable = false, length = 120)
    private String instructor;

    @Check(name = "ck_course_capacity_non_negative", constraints = "capacity >= 0")
    @Column(name = "capacity", nullable = false)
    private int capacity;

    @Check(name = "ck_course_available_seats_non_negative", constraints = "available_seats >= 0")
    @Check(name = "ck_course_available_seats_within_capacity",
            constraints = "available_seats <= capacity")
    @Column(name = "available_seats", nullable = false)
    private int availableSeats;

    @Column(name = "semester", nullable = false, length = 10)
    private String semester;

    @Column(name = "faculty", nullable = false, length = 80)
    private String faculty;

    @Column(name = "description", nullable = false, length = 1000)
    private String description;

    @OneToMany(mappedBy = "course", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    private List<CourseSchedule> schedule = new ArrayList<>();

    protected Course() {
    }

    public Course(UUID id, String code, String name, int creditHours, String instructor, int capacity,
            int availableSeats, String semester, String faculty, String description) {
        this.id = id != null ? id : UUID.randomUUID();
        this.code = code;
        this.name = name;
        this.creditHours = creditHours;
        this.instructor = instructor;
        this.capacity = capacity;
        this.availableSeats = availableSeats;
        this.semester = semester;
        this.faculty = faculty;
        this.description = description;
    }

    public void addSchedule(CourseSchedule slot) {
        slot.attachTo(this);
        schedule.add(slot);
    }

    public UUID getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public int getCreditHours() {
        return creditHours;
    }

    public String getInstructor() {
        return instructor;
    }

    public int getCapacity() {
        return capacity;
    }

    public int getAvailableSeats() {
        return availableSeats;
    }

    public void setAvailableSeats(int availableSeats) {
        this.availableSeats = availableSeats;
    }

    public String getSemester() {
        return semester;
    }

    public String getFaculty() {
        return faculty;
    }

    public String getDescription() {
        return description;
    }

    public List<CourseSchedule> getSchedule() {
        return schedule;
    }
}
