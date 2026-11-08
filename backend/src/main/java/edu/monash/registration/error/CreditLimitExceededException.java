package edu.monash.registration.error;

import org.springframework.http.HttpStatus;

/** Adding the course would push the student past the 24 credit point limit (409). */
public class CreditLimitExceededException extends ApiException {

    public CreditLimitExceededException(String courseCode, int creditHours, int maxCredits) {
        super(HttpStatus.CONFLICT,
                "CREDIT_LIMIT_EXCEEDED",
                "Adding " + courseCode + " (" + creditHours + " credit points) would exceed the "
                        + maxCredits + " credit point limit.");
    }
}
