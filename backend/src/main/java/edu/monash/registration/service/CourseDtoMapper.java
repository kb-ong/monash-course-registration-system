package edu.monash.registration.service;

import edu.monash.registration.api.dto.CourseDto;
import edu.monash.registration.api.dto.ScheduleSlotDto;
import edu.monash.registration.domain.Course;
import edu.monash.registration.domain.CourseSchedule;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Maps course aggregates to the frozen course JSON shape. Schedule slots are ordered day then
 * start time (spec §7.4). Must be called with the persistence context open (schedules are lazy).
 */
@Component
public class CourseDtoMapper {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private static final Comparator<CourseSchedule> SLOT_ORDER =
            Comparator.comparing(CourseSchedule::getDay)
                    .thenComparing(CourseSchedule::getStartTime);

    public CourseDto toDto(Course course) {
        List<ScheduleSlotDto> slots = course.getSchedule().stream()
                .sorted(SLOT_ORDER)
                .map(slot -> new ScheduleSlotDto(
                        slot.getDay().name(),
                        slot.getStartTime().format(TIME),
                        slot.getEndTime().format(TIME),
                        slot.getLocation()))
                .toList();
        return new CourseDto(
                course.getId(),
                course.getCode(),
                course.getName(),
                course.getCreditHours(),
                course.getInstructor(),
                course.getCapacity(),
                course.getAvailableSeats(),
                course.getSemester(),
                course.getFaculty(),
                course.getDescription(),
                slots);
    }

    public List<CourseDto> toDtos(List<Course> courses) {
        return courses.stream().map(this::toDto).toList();
    }
}
