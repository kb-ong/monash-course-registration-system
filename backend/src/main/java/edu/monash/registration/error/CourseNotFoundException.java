package edu.monash.registration.error;

import org.springframework.http.HttpStatus;

/** Unknown or unparsable course identifier (404 COURSE_NOT_FOUND). */
public class CourseNotFoundException extends ApiException {

    public CourseNotFoundException() {
        super(HttpStatus.NOT_FOUND, "COURSE_NOT_FOUND", "Course not found.");
    }
}
