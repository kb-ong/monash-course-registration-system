package edu.monash.registration.api;

import edu.monash.registration.domain.AuditAction;
import edu.monash.registration.error.ApiErrorWriter;
import edu.monash.registration.error.ApiException;
import edu.monash.registration.error.ErrorEnvelope;
import edu.monash.registration.error.MalformedRegistrationIdException;
import edu.monash.registration.error.ValidationException;
import edu.monash.registration.security.StudentPrincipal;
import edu.monash.registration.service.AuditWriter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * One error boundary: every exception becomes the frozen envelope, and no stack trace, SQL
 * fragment or exception class name ever reaches the client (NFR2-S6).
 *
 * <p>Rejections that never reach {@code RegistrationService} — a malformed body, a bean-validation
 * miss, an unparseable path id — are still register/drop attempts, so they are audited here
 * (NFR4-1). The student comes from the JWT principal, which is the only identity available that
 * early; without a principal nothing is written (an unauthenticated call is not an attempt by any
 * student).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String REGISTRATIONS_PATH = "/api/registrations";

    private final ApiErrorWriter errorWriter;
    private final AuditWriter auditWriter;

    public GlobalExceptionHandler(ApiErrorWriter errorWriter, AuditWriter auditWriter) {
        this.errorWriter = errorWriter;
        this.auditWriter = auditWriter;
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorEnvelope> handleApiException(ApiException ex) {
        return build(ex.getStatus(), ex.getCode(), ex.getMessage());
    }

    /** Malformed {@code courseId}: a validation error, and an audited register attempt. */
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorEnvelope> handleValidationException(ValidationException ex,
            HttpServletRequest request) {
        auditRejection(request);
        return build(ex.getStatus(), ex.getCode(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorEnvelope> handleValidation(MethodArgumentNotValidException ex,
            HttpServletRequest request) {
        auditRejection(request);
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getDefaultMessage() == null
                        ? error.getField() + " is invalid."
                        : error.getDefaultMessage())
                .orElse("Request validation failed.");
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorEnvelope> handleUnreadable(HttpMessageNotReadableException ex,
            HttpServletRequest request) {
        auditRejection(request);
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Malformed JSON request body.");
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorEnvelope> handleMissingParameter(MissingServletRequestParameterException ex) {
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", ex.getParameterName() + " is required.");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorEnvelope> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR",
                "Invalid value for " + ex.getName() + ".");
    }

    /** A path id that is not a UUID: 404 per the contract, audited as DROP_REJECTED. */
    @ExceptionHandler(MalformedRegistrationIdException.class)
    public ResponseEntity<ErrorEnvelope> handleMalformedRegistrationId(MalformedRegistrationIdException ex,
            HttpServletRequest request) {
        auditRejection(request);
        return build(ex.getStatus(), ex.getCode(), ex.getMessage());
    }

    /**
     * Backstop for the register path only: a race that slips past the existsBy check still maps to
     * 409, never 500 (FR3-B12). A constraint violation anywhere else is not "already registered"
     * and must not be reported as such.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorEnvelope> handleIntegrity(DataIntegrityViolationException ex,
            HttpServletRequest request) {
        if (isRegisterAttempt(request)) {
            log.warn("persistence.constraint.violation mapped=ALREADY_REGISTERED");
            return build(HttpStatus.CONFLICT, "ALREADY_REGISTERED",
                    "You are already registered in this course.");
        }
        log.error("persistence.constraint.violation unmapped path={} method={}",
                request.getRequestURI(), request.getMethod(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred. Please try again.");
    }

    /**
     * A stale registration row means the row was dropped concurrently: the contract calls that
     * 404 REGISTRATION_NOT_FOUND, never an opaque 500 (FR4-B8, m-10). Scoped to the drop path —
     * "registration not found" is not the right answer to an optimistic lock on any other call.
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorEnvelope> handleOptimisticLock(ObjectOptimisticLockingFailureException ex,
            HttpServletRequest request) {
        if (isDropAttempt(request)) {
            log.warn("persistence.optimistic.lock mapped=REGISTRATION_NOT_FOUND");
            return build(HttpStatus.NOT_FOUND, "REGISTRATION_NOT_FOUND", "Registration not found.");
        }
        log.error("persistence.optimistic.lock unmapped path={} method={}",
                request.getRequestURI(), request.getMethod(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred. Please try again.");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorEnvelope> handleNoResource(NoResourceFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found.");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorEnvelope> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "HTTP method not supported.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorEnvelope> handleUnexpected(Exception ex) {
        log.error("request.failed message={}", ex.getMessage(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred. Please try again.");
    }

    /**
     * Records a rejected register/drop attempt that failed before the service could see it. Never
     * throws: the response is already decided, and an audit problem must not change it.
     */
    private void auditRejection(HttpServletRequest request) {
        AuditAction action = rejectionAction(request);
        if (action == null) {
            return;
        }
        String studentId = currentStudentId();
        if (studentId == null) {
            return;
        }
        auditWriter.recordFailure(UUID.randomUUID(), studentId, null, action, "VALIDATION_ERROR");
    }

    private static AuditAction rejectionAction(HttpServletRequest request) {
        if (isRegisterAttempt(request)) {
            return AuditAction.REGISTER_REJECTED;
        }
        if (isDropAttempt(request)) {
            return AuditAction.DROP_REJECTED;
        }
        return null;
    }

    private static boolean isRegisterAttempt(HttpServletRequest request) {
        return HttpMethod.POST.matches(request.getMethod())
                && REGISTRATIONS_PATH.equals(request.getRequestURI());
    }

    private static boolean isDropAttempt(HttpServletRequest request) {
        return HttpMethod.DELETE.matches(request.getMethod())
                && request.getRequestURI().startsWith(REGISTRATIONS_PATH + "/");
    }

    private static String currentStudentId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null
                && authentication.getPrincipal() instanceof StudentPrincipal principal) {
            return principal.studentId();
        }
        return null;
    }

    private ResponseEntity<ErrorEnvelope> build(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(ErrorEnvelope.of(code, message, errorWriter.now()));
    }
}
