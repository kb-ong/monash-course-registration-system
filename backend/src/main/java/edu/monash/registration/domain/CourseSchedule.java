package edu.monash.registration.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalTime;
import java.util.UUID;
import org.hibernate.annotations.Check;

/**
 * One weekly meeting slot of a course. A course has 1..n slots.
 *
 * <p>{@code day}, {@code startTime} and {@code endTime} are non-null by mapping; the check adds the
 * ordering invariant a null-blind column mapping cannot express (§7.4).
 */
@Entity
@Table(name = "course_schedule")
@Check(name = "ck_course_schedule_time_order", constraints = "end_time > start_time")
public class CourseSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Enumerated(EnumType.STRING)
    @Column(name = "day_code", nullable = false, length = 3)
    private DayOfWeekCode day;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "location", nullable = false, length = 120)
    private String location;

    protected CourseSchedule() {
    }

    public CourseSchedule(DayOfWeekCode day, LocalTime startTime, LocalTime endTime, String location) {
        this.day = day;
        this.startTime = startTime;
        this.endTime = endTime;
        this.location = location;
    }

    void attachTo(Course course) {
        this.course = course;
    }

    public UUID getId() {
        return id;
    }

    public Course getCourse() {
        return course;
    }

    public DayOfWeekCode getDay() {
        return day;
    }

    public LocalTime getStartTime() {
        return startTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    public String getLocation() {
        return location;
    }
}
