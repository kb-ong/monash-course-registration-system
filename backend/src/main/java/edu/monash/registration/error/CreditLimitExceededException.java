package edu.monash.registration.error;

import org.springframework.http.HttpStatus;

/**
 * Adding the course would push the student past the configured semester credit limit (409, FR10).
 * The message names the remaining credits so the student can choose a course that still fits.
 */
public class CreditLimitExceededException extends ApiException {

    public CreditLimitExceededException(String courseCode, int creditHours, int currentCredits,
            int maxCredits) {
        super(HttpStatus.CONFLICT,
                "CREDIT_LIMIT_EXCEEDED",
                "Adding " + courseCode + " (" + creditHours + " credit points) would exceed the "
                        + maxCredits + " credit point limit. You have "
                        + Math.max(0, maxCredits - currentCredits) + " credit points remaining.");
    }
}
