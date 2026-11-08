package edu.monash.registration.error;

import org.springframework.http.HttpStatus;

/** Missing, malformed, expired or wrongly signed bearer token (401 UNAUTHORIZED). */
public class UnauthorizedException extends ApiException {

    public UnauthorizedException() {
        super(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication is required.");
    }
}
