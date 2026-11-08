package edu.monash.registration.error;

import org.springframework.http.HttpStatus;

/**
 * Base class for every handled business error. Carries the stable machine-readable code and the
 * HTTP status that the global exception handler turns into the frozen error envelope.
 */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    protected ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
