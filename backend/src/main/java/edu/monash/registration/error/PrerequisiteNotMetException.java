package edu.monash.registration.error;

import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * FR8: the student has not completed one or more prerequisites of the requested course.
 *
 * <p>The message names every missing course so the student can see exactly what blocks the
 * registration; the frozen error envelope carries no extra fields (00 §9.1).
 */
public class PrerequisiteNotMetException extends ApiException {

    public PrerequisiteNotMetException(String courseCode, List<String> missingPrerequisiteCodes) {
        super(HttpStatus.CONFLICT, "PREREQUISITE_NOT_MET", message(courseCode, missingPrerequisiteCodes));
    }

    private static String message(String courseCode, List<String> missingPrerequisiteCodes) {
        String noun = missingPrerequisiteCodes.size() == 1 ? "prerequisite" : "prerequisites";
        return "Cannot register for " + courseCode + ": missing " + noun + " "
                + String.join(", ", missingPrerequisiteCodes) + ".";
    }
}
