package edu.monash.registration.error;

import org.springframework.http.HttpStatus;

/** FR11: unknown or another student's notification. */
public class NotificationNotFoundException extends ApiException {

    public NotificationNotFoundException() {
        super(HttpStatus.NOT_FOUND, "NOTIFICATION_NOT_FOUND", "Notification not found.");
    }
}
