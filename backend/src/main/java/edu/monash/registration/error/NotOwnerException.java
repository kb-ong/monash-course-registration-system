package edu.monash.registration.error;

import org.springframework.http.HttpStatus;

/** A registration exists but belongs to a different student (403 NOT_OWNER). */
public class NotOwnerException extends ApiException {

    public NotOwnerException() {
        super(HttpStatus.FORBIDDEN, "NOT_OWNER", "This registration belongs to another student.");
    }
}
