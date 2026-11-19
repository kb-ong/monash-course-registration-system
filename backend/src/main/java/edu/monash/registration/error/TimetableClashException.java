package edu.monash.registration.error;

import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * FR9: the requested course overlaps in time with a course the student is already registered in.
 *
 * <p>The message names every conflicting course and the particular overlapping slots so the
 * student can see why the registration was blocked, e.g. {@code Cannot register for FIT1051:
 * timetable clash with FIT2004 (TUE 10:00-12:00).} The frozen error envelope is unchanged.
 */
public class TimetableClashException extends ApiException {

    public TimetableClashException(String requestedCourseCode, List<String> conflicts) {
        super(HttpStatus.CONFLICT, "TIMETABLE_CLASH", message(requestedCourseCode, conflicts));
    }

    private static String message(String requestedCourseCode, List<String> conflicts) {
        return "Cannot register for " + requestedCourseCode + ": timetable clash with "
                + String.join(", ", conflicts) + ".";
    }
}
