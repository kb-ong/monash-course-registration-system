package edu.monash.registration.service;

import edu.monash.registration.domain.Course;
import edu.monash.registration.domain.CourseSchedule;
import edu.monash.registration.domain.Registration;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

/**
 * FR9: pairwise weekly-slot overlap detection.
 *
 * <p>A clash exists when the requested course and one of the student's existing registrations have
 * at least one pair of slots on the same day whose times intersect. Intervals are half-open
 * ({@code [start, end)}), so a class ending at 10:00 and another starting at 10:00 do not clash.
 * Back-to-back classes on the same day are therefore allowed.
 */
public final class TimetableClashDetector {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private static final Comparator<CourseSchedule> SLOT_ORDER =
            Comparator.comparing(CourseSchedule::getDay)
                    .thenComparing(CourseSchedule::getStartTime);

    private TimetableClashDetector() {
    }

    /** One already-registered course together with the slots that overlap the requested course. */
    public record Clash(Course course, List<CourseSchedule> slots) {
    }

    /**
     * Returns every existing registration that overlaps the requested course, ordered by course
     * code, each carrying only the overlapping slots (ordered day then start time). An empty list
     * means the timetable is safe.
     */
    public static List<Clash> detect(Course requested, List<Registration> existing) {
        List<CourseSchedule> requestedSlots = requested.getSchedule();
        return existing.stream()
                .map(Registration::getCourse)
                .filter(course -> !course.getId().equals(requested.getId()))
                .map(course -> new Clash(course,
                        overlappingSlots(requestedSlots, course.getSchedule())))
                .filter(clash -> !clash.slots().isEmpty())
                .sorted(Comparator.comparing(clash -> clash.course().getCode()))
                .toList();
    }

    private static List<CourseSchedule> overlappingSlots(List<CourseSchedule> requested,
            List<CourseSchedule> registered) {
        return registered.stream()
                .filter(registeredSlot -> requested.stream()
                        .anyMatch(requestedSlot -> overlaps(requestedSlot, registeredSlot)))
                .sorted(SLOT_ORDER)
                .toList();
    }

    static boolean overlaps(CourseSchedule first, CourseSchedule second) {
        return first.getDay() == second.getDay()
                && first.getStartTime().isBefore(second.getEndTime())
                && second.getStartTime().isBefore(first.getEndTime());
    }

    /** {@code MON 10:00-12:00} / {@code MON 10:00-12:00, WED 14:00-16:00}. */
    public static String describeSlots(List<CourseSchedule> slots) {
        return slots.stream()
                .map(slot -> slot.getDay().name() + " "
                        + slot.getStartTime().format(TIME) + "-"
                        + slot.getEndTime().format(TIME))
                .reduce((left, right) -> left + ", " + right)
                .orElse("");
    }

    /** One {@code CODE (slots)} description per conflict, in the order {@link #detect} returns. */
    public static List<String> describe(List<Clash> clashes) {
        return clashes.stream()
                .map(clash -> clash.course().getCode() + " ("
                        + describeSlots(clash.slots()) + ")")
                .toList();
    }
}
