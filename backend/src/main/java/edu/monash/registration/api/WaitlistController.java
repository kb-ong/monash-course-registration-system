package edu.monash.registration.api;

import edu.monash.registration.api.dto.RegisterRequest;
import edu.monash.registration.api.dto.WaitlistJoinResponse;
import edu.monash.registration.api.dto.WaitlistLeaveResponse;
import edu.monash.registration.api.dto.WaitlistListDto;
import edu.monash.registration.error.UnauthorizedException;
import edu.monash.registration.error.ValidationException;
import edu.monash.registration.error.MalformedWaitlistIdException;
import edu.monash.registration.security.StudentPrincipal;
import edu.monash.registration.service.WaitlistService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * FR11 waitlist endpoints (E11–E13). Reads are scoped to the authenticated student; a waitlist id
 * is opaque, so unknown and foreign ids are both 404 WAITLIST_NOT_FOUND.
 */
@RestController
@RequestMapping("/api")
public class WaitlistController {

    private final WaitlistService waitlistService;

    public WaitlistController(WaitlistService waitlistService) {
        this.waitlistService = waitlistService;
    }

    @GetMapping("/waitlist")
    public WaitlistListDto waitlist(@AuthenticationPrincipal StudentPrincipal principal) {
        return waitlistService.list(requireStudent(principal));
    }

    @PostMapping("/waitlist")
    public ResponseEntity<WaitlistJoinResponse> join(
            @AuthenticationPrincipal StudentPrincipal principal,
            @Valid @RequestBody RegisterRequest request) {
        UUID courseId = parseUuid(request.courseId(), "courseId");
        WaitlistJoinResponse response = waitlistService.join(requireStudent(principal), courseId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/waitlist/{waitlistId}")
    public WaitlistLeaveResponse leave(@AuthenticationPrincipal StudentPrincipal principal,
            @PathVariable("waitlistId") String waitlistId) {
        return waitlistService.leave(requireStudent(principal), parseId(waitlistId));
    }

    private String requireStudent(StudentPrincipal principal) {
        if (principal == null) {
            throw new UnauthorizedException();
        }
        return principal.studentId();
    }

    private UUID parseUuid(String raw, String field) {
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ex) {
            throw new ValidationException(field + " must be a valid UUID.");
        }
    }

    /** Path ids are opaque: a non-numeric id is "not found", never 400 (mirrors FR4). */
    private Long parseId(String raw) {
        try {
            return Long.valueOf(raw);
        } catch (NumberFormatException ex) {
            throw new MalformedWaitlistIdException();
        }
    }
}
