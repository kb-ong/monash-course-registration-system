package edu.monash.registration.error;

import org.springframework.http.HttpStatus;

/** No seat left at the moment the row lock was taken (409 COURSE_FULL). */
public class CourseFullException extends ApiException {

    public CourseFullException(String courseCode) {
        super(HttpStatus.CONFLICT, "COURSE_FULL", courseCode + " has no available seats.");
    }
}
